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
            // la URL del feed fuera correcta.
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mi Radio App) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
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
     *  las 2 de la mañana", como si COPE publicara una lista de franjas fija
     *  en vez de devolver siempre la más reciente primero): elige a mano el
     *  elemento con la fecha de publicación más nueva. Si ningún elemento
     *  trae una fecha que se pueda interpretar, se queda con el primero
     *  como antes, en vez de fallar. */
    suspend fun fetchLatestBulletin(): Result<NewsArticle?> =
        fetchFeed(BULLETIN_FEED_URL).map { articles ->
            // Reportado con captura: el título decía "boletín 02:00" pero la
            // fecha mostrada era de semanas después, en el futuro. Un pubDate
            // en el futuro no puede ser de verdad "el más reciente" (o el
            // reloj del móvil está mal, o el propio feed trae un dato roto
            // para ese elemento): se descarta como candidato a "más nuevo"
            // en vez de dejar que gane por tener la fecha numéricamente más
            // alta, igual que antes se descartaba una fecha sin interpretar.
            val now = System.currentTimeMillis()
            val maxFuture = now + TimeUnit.HOURS.toMillis(6)
            val withParsedDate = articles.mapNotNull { article ->
                parseRssPubDateMillis(article.pubDate)
                    ?.takeIf { it <= maxFuture }
                    ?.let { article to it }
            }
            if (withParsedDate.isEmpty() && articles.isNotEmpty()) {
                // Si ninguna fecha se ha podido interpretar, maxByOrNull con
                // un valor de reserva igual para todos volvería a devolver
                // sin querer el primer elemento (el mismo fallo de siempre:
                // "el boletín siempre es el de las 2"). Se deja constancia
                // de las fechas en crudo para poder ampliar los formatos de
                // RssDates la próxima vez, en vez de repetir ese fallo en
                // silencio.
                DiagnosticsLog.log(
                    context,
                    "NewsRepository",
                    "Boletín: ninguna fecha reconocida, pubDate en crudo: " +
                        articles.take(5).joinToString(" | ") { it.pubDate ?: "(vacío)" },
                )
            }
            withParsedDate.maxByOrNull { it.second }?.first ?: articles.firstOrNull()
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
