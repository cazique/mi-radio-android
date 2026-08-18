package com.miradio.app.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/** Por URL de logo, para no volver a analizar el mismo bitmap con Palette
 *  cada vez que se reabre la pantalla de reproducción de la misma emisora. */
private val dominantColorCache = mutableMapOf<String, Int>()

/**
 * Color dominante del logo de una emisora, para el fondo degradado de la
 * pantalla de reproducción (estilo Spotify). Usa el ImageLoader compartido
 * de Coil (`context.imageLoader`), así que si el logo ya se pintó en algún
 * sitio con AsyncImage lo coge de su caché en vez de descargarlo de nuevo.
 * Si no hay logo, falla la descarga o Palette no encuentra nada útil,
 * devuelve [fallback] sin lanzar ninguna excepción.
 */
suspend fun extractDominantColor(context: Context, logoUrl: String?, fallback: Color): Color {
    if (logoUrl.isNullOrBlank()) return fallback
    dominantColorCache[logoUrl]?.let { return Color(it) }

    val request = ImageRequest.Builder(context)
        .data(logoUrl)
        // Palette necesita leer los píxeles del bitmap a mano; un bitmap
        // "hardware" (la config por defecto y más eficiente de Coil en
        // Android 8+) no permite eso y lanzaría una excepción al generarlo.
        .allowHardware(false)
        .build()

    val bitmap = runCatching {
        (context.imageLoader.execute(request) as? SuccessResult)?.drawable
    }.getOrNull()?.let { drawable -> (drawable as? BitmapDrawable)?.bitmap } ?: return fallback

    val color = runCatching {
        val palette = Palette.from(bitmap).generate()
        palette.vibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: fallback.toArgb()
    }.getOrDefault(fallback.toArgb())

    dominantColorCache[logoUrl] = color
    return Color(color)
}
