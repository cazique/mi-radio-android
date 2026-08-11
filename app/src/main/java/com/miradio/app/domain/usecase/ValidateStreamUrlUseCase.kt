package com.miradio.app.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class UrlValidationResult {
    data object Valid : UrlValidationResult()
    data object InvalidFormat : UrlValidationResult()
    data class Unreachable(val reason: String) : UrlValidationResult()
}

/**
 * Comprueba que la URL de un stream tiene una forma válida y, si es
 * alcanzable, que el servidor responde. Muchos servidores Icecast/Shoutcast
 * no soportan HEAD, así que se hace una GET y se corta la conexión en cuanto
 * llegan las cabeceras, sin descargar el cuerpo del stream.
 */
class ValidateStreamUrlUseCase(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build(),
) {
    fun looksValid(url: String): Boolean {
        if (url.isBlank()) return false
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }

    suspend operator fun invoke(url: String): UrlValidationResult = withContext(Dispatchers.IO) {
        if (!looksValid(url)) return@withContext UrlValidationResult.InvalidFormat
        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 200..399) {
                    UrlValidationResult.Valid
                } else {
                    UrlValidationResult.Unreachable("HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            UrlValidationResult.Unreachable(e.message ?: "Error de conexión")
        }
    }
}
