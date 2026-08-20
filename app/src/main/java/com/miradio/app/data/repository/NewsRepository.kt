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

    suspend fun fetchFeed(url: String, sourceId: String? = null): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
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
     *  las 2 de la mañana", como si COPE publicara una lista de franjas fija
     *  en vez de devolver siempre la más reciente primero): elige a mano el
     *  elemento con la fecha de publicación más nueva. Si ningún elemento
     *  trae una fecha que se pueda interpretar, se queda con el primero
     *  como antes, en vez de fallar. */
    suspend fun fetchLatestBulletin(): Result<NewsArticle?> =
        fetchFeed(BULLETIN_FEED_URL).map { articles ->
            articles.maxByOrNull { parseRssPubDateMillis(it.pubDate) ?: Long.MIN_VALUE }
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
