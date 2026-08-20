package com.miradio.app.util

import java.text.SimpleDateFormat
import java.util.Locale

/** Formatos de fecha que usan los feeds RSS que lee la app (RFC-822 con
 *  variantes, más ISO-8601 por si acaso). Compartido entre la capa de datos
 *  (para saber qué artículo es realmente el más reciente) y la pantalla de
 *  Noticias (para mostrar la fecha), así ambas entienden exactamente las
 *  mismas fechas. */
private val RSS_DATE_PATTERNS = listOf(
    "EEE, dd MMM yyyy HH:mm:ss Z",
    "EEE, dd MMM yyyy HH:mm:ss zzz",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
)

/** Milisegundos desde época a partir de un `pubDate` de RSS, o null si viene
 *  vacío o en un formato que no se reconoce. */
fun parseRssPubDateMillis(pubDate: String?): Long? {
    if (pubDate.isNullOrBlank()) return null
    return RSS_DATE_PATTERNS.firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.ENGLISH).parse(pubDate)?.time }.getOrNull()
    }
}
