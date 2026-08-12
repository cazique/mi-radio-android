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
 * Comprueba que la URL de un stream tiene una forma válida y que lo que hay
 * detrás es realmente audio, no solo que el servidor responde. Antes solo se
 * miraba el código HTTP: una emisora caída que redirige a una página de
 * "stream no disponible" (HTML, 200 OK) pasaba como válida igualmente. Ahora
 * se mira el Content-Type y, si no es concluyente (habitual en servidores
 * Icecast/Shoutcast, que muchas veces no lo mandan bien), los primeros bytes
 * reales del cuerpo en busca de una cabecera de audio conocida.
 *
 * Sigue sin ser una prueba de reproducción completa (no se llega a decodificar
 * nada con ExoPlayer): es una comprobación barata y rápida que descarta el
 * caso más habitual de "la URL responde pero no es un stream".
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
                if (!response.isSuccessful && response.code !in 200..399) {
                    return@use UrlValidationResult.Unreachable("HTTP ${response.code}")
                }
                val body = response.body ?: return@use UrlValidationResult.Unreachable("Respuesta vacía")
                val contentType = response.header("Content-Type")
                // peek() lee de una copia del flujo sin "consumirlo": el body
                // se puede seguir cerrando normalmente al salir de use{}, sin
                // haber descargado el stream (que, al ser radio en directo, no
                // tiene fin) más allá de estos pocos bytes.
                //
                // OJO: request() en vez de readByteArray(n). readByteArray(n)
                // exige exactamente n bytes y lanza EOFException si el cuerpo
                // es más corto (p. ej. una respuesta de error de 6 bytes),
                // perdiendo los bytes que sí habían llegado. request(n) no
                // lanza: deja en el buffer lo que haya podido leer, aunque
                // sea menos de n.
                val firstBytes = runCatching {
                    val peeked = body.source().peek()
                    peeked.request(MAGIC_BYTES_TO_CHECK.toLong())
                    peeked.buffer.readByteArray()
                }.getOrDefault(ByteArray(0))

                if (looksLikeAudio(contentType, firstBytes)) {
                    UrlValidationResult.Valid
                } else {
                    val detail = contentType?.let { " (el servidor dice \"$it\")" }.orEmpty()
                    UrlValidationResult.Unreachable("El servidor responde, pero no parece un stream de audio$detail")
                }
            }
        } catch (e: Exception) {
            UrlValidationResult.Unreachable(e.message ?: "Error de conexión")
        }
    }

    private fun looksLikeAudio(contentType: String?, firstBytes: ByteArray): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type != null) {
            if (type in KNOWN_AUDIO_CONTENT_TYPES) return true
            // Si el propio servidor dice explícitamente que esto es HTML,
            // JSON o texto plano, no hace falta mirar el cuerpo: no es audio.
            if (type in KNOWN_NON_AUDIO_CONTENT_TYPES) return false
        }
        return hasKnownAudioMagic(firstBytes)
    }

    /** Reconoce el arranque de un MP3 (con o sin cabecera ID3), un AAC ADTS
     *  crudo, un OGG, o una lista de reproducción HLS/M3U — los formatos que
     *  de verdad usan las emisoras de esta app (ver guessStreamMimeType en
     *  RadioPlayer). Sirve de respaldo cuando el Content-Type no dice nada
     *  útil, algo muy habitual en servidores Icecast/Shoutcast. */
    private fun hasKnownAudioMagic(bytes: ByteArray): Boolean {
        if (bytes.size >= 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            return true // MP3 con etiqueta ID3
        }
        if (bytes.size >= 4 && bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
        ) {
            return true // OGG
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return true // MP3/AAC: frame sync
        }
        val asText = runCatching { String(bytes, Charsets.US_ASCII) }.getOrDefault("")
        if (asText.startsWith("#EXTM3U")) return true // lista de reproducción HLS
        return false
    }

    private companion object {
        const val MAGIC_BYTES_TO_CHECK = 16

        val KNOWN_AUDIO_CONTENT_TYPES = setOf(
            "audio/mpeg", "audio/mp3", "audio/aac", "audio/aacp",
            "audio/ogg", "application/ogg", "audio/flac", "audio/wav", "audio/x-wav",
            "audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl",
            "audio/x-scpls",
        )

        val KNOWN_NON_AUDIO_CONTENT_TYPES = setOf(
            "text/html", "application/json", "text/plain", "text/xml", "application/xml",
        )
    }
}
