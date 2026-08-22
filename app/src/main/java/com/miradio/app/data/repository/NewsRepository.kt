package com.miradio.app.data.repository

import android.content.Context
import android.text.Html
import com.miradio.app.domain.model.NewsArticle
import com.miradio.app.util.DiagnosticsLog
import com.miradio.app.util.parseRssPubDateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

private const val BULLETIN_FEED_URL = "https://www.cope.es/api/es/actualidad/boletin/audios/rss.xml"

/**
 * Descarga y parsea feeds RSS 2.0 públicos de COPE (noticias por sección y
 * el boletín informativo en audio). Al leer el RSS "en crudo" y mostrar
 * solo título/resumen/imagen en la propia app (en vez de cargar su web),
 * el resultado no lleva la publicidad de cope.es: un RSS nunca la incluye,
 * solo el contenido.
 */
class NewsRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchFeed(url: String, sourceId: String? = null): Result<List<NewsArticle>> =
        fetchFeed(listOf(url), sourceId)

    /** Prueba cada URL en orden y se queda con la primera que responda con
     *  algún artículo: así una fuente con [NewsSource.fallbackUrls] no se
     *  queda rota en cuanto el medio cambia la ruta de su RSS principal. */
    suspend fun fetchFeed(urls: List<String>, sourceId: String?): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        var lastFailure: Result<List<NewsArticle>>? = null
        for (url in urls) {
            val result = fetchSingleFeed(url, sourceId)
            if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) return@withContext result
            lastFailure = result
        }
        lastFailure ?: Result.failure(Exception("Sin URL de feed"))
    }

    private suspend fun fetchSingleFeed(url: String, sourceId: String?): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        try {
            // Algunos medios (reportado con La Razón) rechazan o cortan la
            // respuesta a peticiones sin cabeceras de navegador, tratándolas
            // como bots: sin esto, esa fuente en concreto podía fallar aunque
            // la URL del feed fuera correcta. El User-Agent es el de un
            // Chrome de Android real, sin ningún texto que identifique la
            // app (antes decía "Mi Radio App" dentro de la cadena): algunos
            // filtros anti-bot bloquean precisamente cualquier UA que no
            // coincida exactamente con el de un navegador conocido.
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .header("Accept-Language", "es-ES,es;q=0.9")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Respuesta vacía"))
                Result.success(parseRss(body, sourceId))
            }
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "NewsRepository", "fetchFeed($url) falló", e)
            Result.failure(e)
        }
    }

    /** No se fía del orden del feed (reportado: "el boletín siempre es el de
     *  las 2 de la mañana"). Un registro real (ver diagnóstico) reveló el
     *  formato de verdad de este feed: los episodios ya archivados llevan un
     *  título con la fecha completa ("18:00H | 27 AGO 2024 | BOLETÍN"),
     *  mientras que el episodio del momento (el único todavía sin archivar)
     *  se llama solo "BOLETÍN HH:MM", sin fecha. Un primer intento (solo
     *  pubDate) fallaba porque pubDate viene roto para ese episodio sin
     *  archivar; un segundo intento (sacar la hora del título y ponerla en
     *  el día de hoy para CUALQUIER título) fallaba distinto: un episodio
     *  archivado de 2024 con hora "18:00" en el título ganaba siempre a uno
     *  de hoy con hora "02:00", por tener la hora numéricamente más alta,
     *  ignorando que el 2024 en su propio título lo desmentía. Ahora se
     *  distingue explícitamente: si hay algún episodio sin fecha en el
     *  título, ese es el actual y gana siempre; solo si no hay ninguno se
     *  cae a ordenar los archivados por su fecha completa (o por pubDate si
     *  ni eso se puede sacar del título). */
    suspend fun fetchLatestBulletin(): Result<NewsArticle?> =
        fetchFeed(BULLETIN_FEED_URL).map { articles ->
            val now = System.currentTimeMillis()
            val maxFuture = now + TimeUnit.HOURS.toMillis(6)
            val hasYear = Regex("""\d{4}""")
            val current = articles.filter { !hasYear.containsMatchIn(it.title) }
            val chosen = if (current.isNotEmpty()) {
                current.maxByOrNull { timeFromTitle(it.title, now) ?: Long.MIN_VALUE } ?: current.first()
            } else {
                val ranked = articles.mapNotNull { article ->
                    val rank = archivedDateFromTitle(article.title)?.takeIf { it <= maxFuture }
                        ?: parseRssPubDateMillis(article.pubDate)?.takeIf { it <= maxFuture }
                    rank?.let { article to it }
                }
                ranked.maxByOrNull { it.second }?.first ?: articles.firstOrNull()
            }
            DiagnosticsLog.log(
                context,
                "NewsRepository",
                "Boletín: ${articles.size} episodio(s), " +
                    articles.take(8).joinToString(" | ") { "\"${it.title}\" pubDate=${it.pubDate ?: "(vacío)"}" },
            )
            DiagnosticsLog.log(context, "NewsRepository", "Boletín elegido: \"${chosen?.title}\"")
            chosen
        }

    /** Extrae una hora "HH:mm" del título (p. ej. "BOLETÍN 14:00") y la
     *  ubica hoy; si esa hora cae más de 6h en el futuro respecto a [now]
     *  (p. ej. son las 00:10 y el título dice "23:00"), se entiende que es
     *  la última franja de ayer, todavía en el feed. Null si el título no
     *  trae ninguna hora reconocible. Solo se usa con títulos ya filtrados
     *  para no llevar ninguna fecha completa (ver [fetchLatestBulletin]). */
    private fun timeFromTitle(title: String, now: Long): Long? {
        val match = Regex("""(\d{1,2}):(\d{2})""").find(title) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis > now + TimeUnit.HOURS.toMillis(6)) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return calendar.timeInMillis
    }

    private val SPANISH_MONTH_ABBREVIATIONS = mapOf(
        "ENE" to 0, "FEB" to 1, "MAR" to 2, "ABR" to 3, "MAY" to 4, "JUN" to 5,
        "JUL" to 6, "AGO" to 7, "SEP" to 8, "OCT" to 9, "NOV" to 10, "DIC" to 11,
    )

    /** Fecha completa de un episodio ya archivado, p. ej. "18:00H | 27 AGO
     *  2024 | BOLETÍN" o "02:00H | 27-08-2024 | BOLETÍN". Null si el título
     *  no trae ninguna de las dos variantes de fecha completa observadas. */
    private fun archivedDateFromTitle(title: String): Long? {
        Regex("""(\d{1,2}):(\d{2})H?\s*\|\s*(\d{1,2})\s+([A-ZÑ]{3})\s+(\d{4})""", RegexOption.IGNORE_CASE)
            .find(title)?.let { m ->
                val (hour, minute, day, monthAbbr, year) = m.destructured
                val month = SPANISH_MONTH_ABBREVIATIONS[monthAbbr.uppercase()] ?: return@let
                dateMillis(year.toInt(), month, day.toInt(), hour.toInt(), minute.toInt())?.let { return it }
            }
        Regex("""(\d{1,2}):(\d{2})H?\s*\|\s*(\d{1,2})-(\d{1,2})-(\d{4})""")
            .find(title)?.let { m ->
                val (hour, minute, day, month, year) = m.destructured
                dateMillis(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt())?.let { return it }
            }
        return null
    }

    private fun dateMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long? {
        if (hour !in 0..23 || minute !in 0..59 || month !in 0..11 || day !in 1..31) return null
        return java.util.Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parseRss(xml: String, sourceId: String?): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var title: String? = null
        var link: String? = null
        var description: String? = null
        var pubDate: String? = null
        var imageUrl: String? = null
        var audioUrl: String? = null
        var insideItem = false
        var currentTag: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when {
                        currentTag.equals("item", ignoreCase = true) -> {
                            insideItem = true
                            title = null; link = null; description = null
                            pubDate = null; imageUrl = null; audioUrl = null
                        }
                        insideItem && currentTag.equals("enclosure", ignoreCase = true) -> {
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            val enclosureUrl = parser.getAttributeValue(null, "url")
                            when {
                                type.startsWith("image") -> imageUrl = imageUrl ?: enclosureUrl
                                type.startsWith("audio") -> audioUrl = audioUrl ?: enclosureUrl
                            }
                        }
                        insideItem && currentTag.equals("media:content", ignoreCase = true) -> {
                            imageUrl = imageUrl ?: parser.getAttributeValue(null, "url")
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (insideItem) {
                        val text = parser.text
                        if (!text.isNullOrEmpty()) {
                            when (currentTag?.lowercase()) {
                                "title" -> title = title.orEmpty() + text
                                "link" -> link = link.orEmpty() + text
                                "description" -> description = description.orEmpty() + text
                                "pubdate" -> pubDate = pubDate.orEmpty() + text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true)) {
                        val cleanTitle = title?.let { stripHtml(it) }?.trim()
                        val cleanLink = link?.trim()
                        if (!cleanTitle.isNullOrBlank() && !cleanLink.isNullOrBlank()) {
                            val cleanDescription = description?.let { stripHtml(it) }?.trim()?.takeIf { it.isNotBlank() }
                            articles += NewsArticle(
                                title = cleanTitle,
                                link = cleanLink,
                                description = cleanDescription,
                                pubDate = pubDate?.trim()?.takeIf { it.isNotBlank() },
                                imageUrl = imageUrl ?: description?.let { extractFirstImageUrl(it) },
                                audioUrl = audioUrl,
                                sourceId = sourceId,
                            )
                        }
                        insideItem = false
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun stripHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

    private fun extractFirstImageUrl(html: String): String? =
        Regex("<img[^>]+src=\"([^\">]+)\"").find(html)?.groupValues?.getOrNull(1)
}
