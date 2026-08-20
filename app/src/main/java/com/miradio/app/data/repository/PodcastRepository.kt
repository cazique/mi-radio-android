package com.miradio.app.data.repository

import android.content.Context
import android.text.Html
import com.miradio.app.domain.model.Podcast
import com.miradio.app.domain.model.PodcastEpisode
import com.miradio.app.util.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Búsqueda y episodios de podcasts vía la API pública de iTunes (sin clave,
 * sin registro: es la misma que usan la mayoría de apps de podcasts para
 * descubrir contenido) más el propio feed RSS de cada podcast para sus
 * episodios, igual que [NewsRepository] hace con las noticias.
 */
class PodcastRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchPodcasts(query: String): Result<List<Podcast>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("media", "podcast")
                .addQueryParameter("country", "ES")
                .addQueryParameter("limit", "25")
                .addQueryParameter("term", query)
                .build()
            val body = execute(url.toString()) ?: return@withContext Result.failure(Exception("Respuesta vacía"))
            val response = json.decodeFromString<ItunesSearchResponse>(body)
            Result.success(response.results.mapNotNull { it.toPodcast() })
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "PodcastRepository", "searchPodcasts(\"$query\") falló", e)
            Result.failure(e)
        }
    }

    /** "Más populares" de España. Esta lista de Apple no trae la URL del feed
     *  de cada podcast (solo su id de colección): hay que resolverla aparte
     *  con [resolvePodcast] antes de poder pedir sus episodios. */
    suspend fun topPodcasts(): Result<List<Podcast>> = withContext(Dispatchers.IO) {
        try {
            val body = execute("https://itunes.apple.com/es/rss/toppodcasts/limit=25/json")
                ?: return@withContext Result.failure(Exception("Respuesta vacía"))
            val response = json.decodeFromString<TopPodcastsResponse>(body)
            Result.success(response.feed.entry.mapNotNull { it.toPodcast() })
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "PodcastRepository", "topPodcasts() falló", e)
            Result.failure(e)
        }
    }

    /** La lista de "más populares" no trae la URL del feed de cada podcast,
     *  solo su id: esto la resuelve (y de paso trae nombre/portada/editor
     *  frescos desde la misma llamada) antes de poder pedir sus episodios. */
    suspend fun resolvePodcast(collectionId: String): Result<Podcast> = withContext(Dispatchers.IO) {
        try {
            val url = "https://itunes.apple.com/lookup".toHttpUrl().newBuilder()
                .addQueryParameter("id", collectionId)
                .build()
            val body = execute(url.toString()) ?: return@withContext Result.failure(Exception("Respuesta vacía"))
            val response = json.decodeFromString<ItunesSearchResponse>(body)
            val podcast = response.results.firstOrNull()?.toPodcast()?.takeIf { it.feedUrl != null }
                ?: return@withContext Result.failure(Exception("Este podcast no tiene feed RSS público"))
            Result.success(podcast)
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "PodcastRepository", "resolvePodcast($collectionId) falló", e)
            Result.failure(e)
        }
    }

    suspend fun fetchEpisodes(podcast: Podcast): Result<List<PodcastEpisode>> = withContext(Dispatchers.IO) {
        val feedUrl = podcast.feedUrl
            ?: return@withContext Result.failure(IllegalStateException("fetchEpisodes necesita feedUrl ya resuelta"))
        try {
            val body = execute(feedUrl) ?: return@withContext Result.failure(Exception("Respuesta vacía"))
            Result.success(parseEpisodes(body, podcast))
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "PodcastRepository", "fetchEpisodes(${podcast.name}) falló", e)
            Result.failure(e)
        }
    }

    private fun execute(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string()
        }
    }

    /** Mismo enfoque que [NewsRepository.parseRss]: XmlPullParser sin
     *  namespaces, así "itunes:duration"/"itunes:image" llegan tal cual como
     *  nombre de etiqueta en vez de tener que resolver el namespace. */
    private fun parseEpisodes(xml: String, podcast: Podcast): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var title: String? = null
        var description: String? = null
        var pubDate: String? = null
        var guid: String? = null
        var audioUrl: String? = null
        var durationSeconds: Long? = null
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
                            title = null; description = null; pubDate = null
                            guid = null; audioUrl = null; durationSeconds = null
                        }
                        insideItem && currentTag.equals("enclosure", ignoreCase = true) -> {
                            audioUrl = audioUrl ?: parser.getAttributeValue(null, "url")
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (insideItem) {
                        val text = parser.text
                        if (!text.isNullOrEmpty()) {
                            when (currentTag?.lowercase()) {
                                "title" -> title = title.orEmpty() + text
                                "guid" -> guid = guid.orEmpty() + text
                                "pubdate" -> pubDate = pubDate.orEmpty() + text
                                "description", "itunes:summary" -> description = description.orEmpty() + text
                                "itunes:duration" -> durationSeconds = parseItunesDuration((durationSeconds?.toString().orEmpty()) + text)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true)) {
                        val cleanTitle = title?.let { stripHtml(it) }?.trim()
                        if (!cleanTitle.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
                            episodes += PodcastEpisode(
                                id = guid?.trim()?.takeIf { it.isNotBlank() } ?: audioUrl,
                                podcastId = podcast.collectionId,
                                podcastName = podcast.name,
                                podcastArtworkUrl = podcast.artworkUrl,
                                title = cleanTitle,
                                description = description?.let { stripHtml(it) }?.trim()?.takeIf { it.isNotBlank() },
                                pubDate = pubDate?.trim()?.takeIf { it.isNotBlank() },
                                audioUrl = audioUrl,
                                durationSeconds = durationSeconds,
                            )
                        }
                        insideItem = false
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
        return episodes
    }

    /** itunes:duration puede venir como segundos a secas ("1834") o como
     *  "HH:MM:SS"/"MM:SS". Si no se puede interpretar, se deja en null: es
     *  solo informativo, no gobierna la barra de progreso real. */
    private fun parseItunesDuration(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return if (":" in trimmed) {
            trimmed.split(":").mapNotNull { it.toLongOrNull() }
                .takeIf { it.isNotEmpty() }
                ?.fold(0L) { acc, part -> acc * 60 + part }
        } else {
            trimmed.toLongOrNull()
        }
    }

    private fun stripHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
}

@Serializable
private data class ItunesSearchResponse(val results: List<ItunesSearchResult> = emptyList())

@Serializable
private data class ItunesSearchResult(
    val collectionId: Long? = null,
    val collectionName: String? = null,
    val artistName: String? = null,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val feedUrl: String? = null,
) {
    fun toPodcast(): Podcast? {
        val id = collectionId ?: return null
        val name = collectionName?.takeIf { it.isNotBlank() } ?: return null
        return Podcast(
            collectionId = id.toString(),
            name = name,
            publisher = artistName,
            artworkUrl = artworkUrl600 ?: artworkUrl100,
            feedUrl = feedUrl,
        )
    }
}

@Serializable
private data class TopPodcastsResponse(val feed: TopPodcastsFeed = TopPodcastsFeed())

@Serializable
private data class TopPodcastsFeed(val entry: List<TopPodcastEntry> = emptyList())

@Serializable
private data class TopPodcastEntry(
    @SerialName("im:name") val name: TopPodcastLabel = TopPodcastLabel(),
    @SerialName("im:artist") val artist: TopPodcastLabel = TopPodcastLabel(),
    @SerialName("im:image") val images: List<TopPodcastLabel> = emptyList(),
    val id: TopPodcastId = TopPodcastId(),
) {
    fun toPodcast(): Podcast? {
        val collectionId = id.attributes.imId.takeIf { it.isNotBlank() } ?: return null
        val podcastName = name.label.takeIf { it.isNotBlank() } ?: return null
        return Podcast(
            collectionId = collectionId,
            name = podcastName,
            publisher = artist.label.takeIf { it.isNotBlank() },
            // La lista de "im:image" viene de menor a mayor resolución.
            artworkUrl = images.lastOrNull()?.label,
            feedUrl = null,
        )
    }
}

@Serializable
private data class TopPodcastLabel(val label: String = "")

@Serializable
private data class TopPodcastId(val attributes: TopPodcastIdAttrs = TopPodcastIdAttrs())

@Serializable
private data class TopPodcastIdAttrs(@SerialName("im:id") val imId: String = "")
