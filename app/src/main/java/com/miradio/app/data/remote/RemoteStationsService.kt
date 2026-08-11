package com.miradio.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class RemoteCatalogResult {
    data class Success(val stations: List<StationDto>) : RemoteCatalogResult()
    data class Failure(val reason: String) : RemoteCatalogResult()
}

/**
 * Descarga y parsea el catálogo remoto de emisoras (JSON). Se usa tanto para
 * la sincronización manual desde Ajustes como, potencialmente, para una
 * sincronización periódica futura. No lanza excepciones: siempre devuelve
 * un resultado explícito para que la UI pueda mostrar un mensaje claro.
 */
class RemoteStationsService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    suspend fun fetchCatalog(url: String): RemoteCatalogResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext RemoteCatalogResult.Failure("URL vacía")
        val request = Request.Builder().url(url).get().build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RemoteCatalogResult.Failure("HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: return@withContext RemoteCatalogResult.Failure("Respuesta vacía")
                val catalog = json.decodeFromString(StationCatalogDto.serializer(), body)
                RemoteCatalogResult.Success(catalog.stations)
            }
        } catch (e: IOException) {
            RemoteCatalogResult.Failure(e.message ?: "Error de conexión")
        } catch (e: Exception) {
            RemoteCatalogResult.Failure("JSON inválido: ${e.message}")
        }
    }
}
