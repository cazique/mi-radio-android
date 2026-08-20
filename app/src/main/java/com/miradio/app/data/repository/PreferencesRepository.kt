package com.miradio.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.miradio.app.BuildConfig
import com.miradio.app.domain.model.CustomNewsSource
import com.miradio.app.domain.model.Podcast
import com.miradio.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "miradio_prefs")

/**
 * Preferencias ligeras de la app: última emisora escuchada, último
 * dispositivo Cast usado, URL del catálogo remoto y tema. Se guardan con
 * DataStore en lugar de SharedPreferences porque es la solución recomendada
 * actualmente y expone Flow de forma nativa.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LAST_STATION_ID = stringPreferencesKey("last_station_id")
        val LAST_CAST_DEVICE_ID = stringPreferencesKey("last_cast_device_id")
        val LAST_CAST_DEVICE_NAME = stringPreferencesKey("last_cast_device_name")
        val REMOTE_CATALOG_URL = stringPreferencesKey("remote_catalog_url")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PLAYBACK_DELAY_SECONDS = intPreferencesKey("playback_delay_seconds")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val RECENT_STATION_IDS = stringPreferencesKey("recent_station_ids")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val SIMPLE_MODE = booleanPreferencesKey("simple_mode")
        val HIDE_ADD_BUTTON = booleanPreferencesKey("hide_add_button")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAST_SEEN_CHANGELOG_ID = intPreferencesKey("last_seen_changelog_id")
        val CUSTOM_NEWS_SOURCES = stringPreferencesKey("custom_news_sources")
        val NEWS_AUTO_REFRESH = booleanPreferencesKey("news_auto_refresh")
        val AEMET_API_KEY = stringPreferencesKey("aemet_api_key")
        val AEMET_MUNICIPIO_ID = stringPreferencesKey("aemet_municipio_id")
        val AEMET_MUNICIPIO_NAME = stringPreferencesKey("aemet_municipio_name")
        val ENABLED_PRESET_NEWS_SOURCES = stringPreferencesKey("enabled_preset_news_sources")
        val LOG_UPLOAD_WEBHOOK_URL = stringPreferencesKey("log_upload_webhook_url")
        val NEWS_SOURCE_AFFINITY = stringPreferencesKey("news_source_affinity")
        val PODCAST_PROGRESS = stringPreferencesKey("podcast_progress")
        val SUBSCRIBED_PODCASTS = stringPreferencesKey("subscribed_podcasts")
        val BREAKING_NEWS_ALERTS = booleanPreferencesKey("breaking_news_alerts")
        val NOTIFIED_BREAKING_NEWS_LINKS = stringPreferencesKey("notified_breaking_news_links")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val lastStationId: Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_STATION_ID] }

    val lastCastDevice: Flow<Pair<String, String>?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.LAST_CAST_DEVICE_ID]
        val name = prefs[Keys.LAST_CAST_DEVICE_NAME]
        if (id != null && name != null) id to name else null
    }

    val remoteCatalogUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.REMOTE_CATALOG_URL] ?: BuildConfig.DEFAULT_REMOTE_STATIONS_URL
    }

    val lastSyncMillis: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_SYNC_MILLIS] }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    /** Retardo (en segundos) con el que se reproduce el directo en el móvil,
     *  p. ej. para sincronizar la radio con una emisión de televisión. */
    val playbackDelaySeconds: Flow<Int> = context.dataStore.data.map { it[Keys.PLAYBACK_DELAY_SECONDS] ?: 0 }

    /** Escala de tamaño de texto propia de la app (1f = normal), independiente
     *  del tamaño de letra del sistema: así el usuario puede ajustarla sin que
     *  se sume o choque con su ajuste de accesibilidad de Android. */
    val textScale: Flow<Float> = context.dataStore.data.map { it[Keys.TEXT_SCALE] ?: 1f }

    /** IDs de las últimas emisoras escuchadas, la más reciente primero. */
    val recentStationIds: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.RECENT_STATION_IDS]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    }

    /** Muestra u oculta el bloque de "Diagnóstico" (registro técnico interno)
     *  en Ajustes. Activado por defecto mientras la app está en pruebas; se
     *  puede desactivar para un uso normal, sin texto técnico a la vista. */
    val debugMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEBUG_MODE] ?: true }

    /** Modo simple: reduce Inicio a "ahora suena" + favoritas en tarjetas
     *  grandes, y la barra inferior a Inicio/Ajustes, pensado para personas
     *  mayores o con dificultad para manejar el móvil. Desactivado por
     *  defecto: lo activa quien configura la app, no el usuario final. */
    val simpleMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.SIMPLE_MODE] ?: false }

    /** Oculta solo el botón "+ Añadir emisora" de Inicio/Explorar, sin
     *  activar el resto del modo simple. Para quien quiere la app normal
     *  pero sin la tentación de que alguien toque "añadir" sin querer. */
    val hideAddButton: Flow<Boolean> = context.dataStore.data.map { it[Keys.HIDE_ADD_BUTTON] ?: false }

    /** Colores del sistema (Material You, Android 12+) en vez de la paleta de
     *  marca. Activado por defecto (a partir de Android 12; en versiones
     *  anteriores no hay color dinámico del sistema y se usa igualmente la
     *  paleta propia). */
    val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

    /** id de la última entrada del historial de cambios ([com.miradio.app.util.Changelog])
     *  que el usuario ya ha visto, para saber si hay novedades que enseñarle
     *  al abrir la app tras una actualización. */
    val lastSeenChangelogId: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_SEEN_CHANGELOG_ID] ?: 0 }

    /** Fuentes RSS propias añadidas en Ajustes > Noticias, además de las
     *  secciones fijas de COPE. */
    val customNewsSources: Flow<List<CustomNewsSource>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.CUSTOM_NEWS_SOURCES] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<CustomNewsSource>>(raw) }.getOrDefault(emptyList())
    }

    /** Si está activo, Noticias se refresca sola de vez en cuando mientras
     *  se tiene esa pantalla abierta, sin tener que tirar hacia abajo a
     *  mano. Activado por defecto. */
    val newsAutoRefreshEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NEWS_AUTO_REFRESH] ?: true }

    /** Clave personal de AEMET OpenData, puesta a mano por quien usa la app
     *  en Ajustes > Clima. Solo vive en este dispositivo: nunca va en el
     *  APK ni se sube a ningún sitio (ver la explicación de por qué no se
     *  puede/debe embeber una clave personal en una app pública). Null =
     *  AEMET desactivado, se usa solo Open-Meteo. */
    val aemetApiKey: Flow<String?> = context.dataStore.data.map { it[Keys.AEMET_API_KEY]?.takeIf { key -> key.isNotBlank() } }

    /** Municipio elegido para AEMET (id "idNNNNN" + nombre para mostrar). */
    val aemetMunicipio: Flow<Pair<String, String>?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.AEMET_MUNICIPIO_ID]
        val name = prefs[Keys.AEMET_MUNICIPIO_NAME]
        if (id != null && name != null) id to name else null
    }

    /** IDs de [com.miradio.app.domain.model.PresetNewsSources] que el
     *  usuario ha activado (además de COPE). Vacío por defecto: cada cual
     *  elige qué medios añadir. */
    val enabledPresetNewsSources: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.ENABLED_PRESET_NEWS_SOURCES]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    /** URL propia (webhook, servidor personal...) a la que subir el
     *  registro automáticamente tras un cierre inesperado, sin tener que
     *  abrir el selector de compartir a mano. Null = sin configurar, se
     *  sigue ofreciendo solo el botón manual de compartir. */
    val logUploadWebhookUrl: Flow<String?> = context.dataStore.data.map {
        it[Keys.LOG_UPLOAD_WEBHOOK_URL]?.takeIf { url -> url.isNotBlank() }
    }

    /** Cuántas veces se ha abierto una noticia de cada fuente (id de
     *  [com.miradio.app.domain.model.NewsSource]). Es la única señal de
     *  personalización de la pestaña "Para ti": ninguna fuente empieza
     *  favorecida, solo gana peso la que de verdad se lee. */
    val newsSourceAffinity: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.NEWS_SOURCE_AFFINITY] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    /** Posición (ms) por la que se quedó cada episodio de podcast, para
     *  poder retomarlo donde se dejó en vez de siempre desde el principio. */
    val podcastProgress: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.PODCAST_PROGRESS] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())
    }

    /** Podcasts que se siguen, con el feedUrl ya resuelto (evita la consulta
     *  extra de "lookup" que hace falta para uno sin seguir). */
    val subscribedPodcasts: Flow<List<Podcast>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SUBSCRIBED_PODCASTS] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Podcast>>(raw) }.getOrDefault(emptyList())
    }

    /** Avisa con una notificación cuando aparece una noticia de última hora
     *  (título que empieza por "Última hora") en cualquier fuente activa.
     *  Desactivado por defecto: es una comprobación periódica en segundo
     *  plano, no todo el mundo la quiere encendida. */
    val breakingNewsAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BREAKING_NEWS_ALERTS] ?: false }

    /** Enlaces de noticias de última hora ya notificadas, para no avisar dos
     *  veces de la misma. */
    val notifiedBreakingNewsLinks: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFIED_BREAKING_NEWS_LINKS]?.let { runCatching { json.decodeFromString<Set<String>>(it) }.getOrDefault(emptySet()) }
            ?: emptySet()
    }

    suspend fun setLastStation(id: String) {
        context.dataStore.edit { it[Keys.LAST_STATION_ID] = id }
    }

    suspend fun setLastCastDevice(id: String, name: String) {
        context.dataStore.edit {
            it[Keys.LAST_CAST_DEVICE_ID] = id
            it[Keys.LAST_CAST_DEVICE_NAME] = name
        }
    }

    suspend fun clearLastCastDevice() {
        context.dataStore.edit {
            it.remove(Keys.LAST_CAST_DEVICE_ID)
            it.remove(Keys.LAST_CAST_DEVICE_NAME)
        }
    }

    suspend fun setRemoteCatalogUrl(url: String) {
        context.dataStore.edit { it[Keys.REMOTE_CATALOG_URL] = url }
    }

    suspend fun setLastSyncNow() {
        context.dataStore.edit { it[Keys.LAST_SYNC_MILLIS] = System.currentTimeMillis() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPlaybackDelaySeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.PLAYBACK_DELAY_SECONDS] = seconds.coerceIn(0, 300) }
    }

    suspend fun setTextScale(scale: Float) {
        context.dataStore.edit { it[Keys.TEXT_SCALE] = scale.coerceIn(0.8f, 1.6f) }
    }

    suspend fun setDebugMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEBUG_MODE] = enabled }
    }

    suspend fun setSimpleMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SIMPLE_MODE] = enabled }
    }

    suspend fun setHideAddButton(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_ADD_BUTTON] = enabled }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setLastSeenChangelogId(id: Int) {
        context.dataStore.edit { it[Keys.LAST_SEEN_CHANGELOG_ID] = id }
    }

    suspend fun setNewsAutoRefreshEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NEWS_AUTO_REFRESH] = enabled }
    }

    suspend fun setAemetApiKey(key: String) {
        context.dataStore.edit { it[Keys.AEMET_API_KEY] = key.trim() }
    }

    suspend fun clearAemetApiKey() {
        context.dataStore.edit {
            it.remove(Keys.AEMET_API_KEY)
            it.remove(Keys.AEMET_MUNICIPIO_ID)
            it.remove(Keys.AEMET_MUNICIPIO_NAME)
        }
    }

    suspend fun setAemetMunicipio(id: String, name: String) {
        context.dataStore.edit {
            it[Keys.AEMET_MUNICIPIO_ID] = id
            it[Keys.AEMET_MUNICIPIO_NAME] = name
        }
    }

    suspend fun setPresetNewsSourceEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_PRESET_NEWS_SOURCES]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(id) else current.remove(id)
            prefs[Keys.ENABLED_PRESET_NEWS_SOURCES] = current.joinToString(",")
        }
    }

    suspend fun setLogUploadWebhookUrl(url: String) {
        context.dataStore.edit { it[Keys.LOG_UPLOAD_WEBHOOK_URL] = url.trim() }
    }

    suspend fun addCustomNewsSource(name: String, feedUrl: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_NEWS_SOURCES]
                ?.let { runCatching { json.decodeFromString<List<CustomNewsSource>>(it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            val updated = current + CustomNewsSource(id = "custom_${System.currentTimeMillis()}", name = name, feedUrl = feedUrl)
            prefs[Keys.CUSTOM_NEWS_SOURCES] = json.encodeToString(updated)
        }
    }

    suspend fun removeCustomNewsSource(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_NEWS_SOURCES]
                ?.let { runCatching { json.decodeFromString<List<CustomNewsSource>>(it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            prefs[Keys.CUSTOM_NEWS_SOURCES] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    /** Suma una lectura de [sourceId] a su afinidad, la señal que usa "Para
     *  ti" para dar más peso a las fuentes que de verdad se leen. */
    suspend fun incrementNewsSourceAffinity(sourceId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.NEWS_SOURCE_AFFINITY]
                ?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            val updated = current + (sourceId to (current[sourceId] ?: 0) + 1)
            prefs[Keys.NEWS_SOURCE_AFFINITY] = json.encodeToString(updated)
        }
    }

    /** Guarda por dónde va [episodeId]. Se limita a los 200 episodios
     *  guardados más recientes para que esto no crezca sin límite con el
     *  tiempo (cada entrada pesa poco, pero "sin límite" no es un límite). */
    suspend fun savePodcastProgress(episodeId: String, positionMs: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PODCAST_PROGRESS]
                ?.let { runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            val updated = (current + (episodeId to positionMs)).let { map ->
                if (map.size > 200) map.entries.drop(map.size - 200).associate { it.key to it.value } else map
            }
            prefs[Keys.PODCAST_PROGRESS] = json.encodeToString(updated)
        }
    }

    /** Guarda [podcast] (con feedUrl ya resuelto) o lo quita, según
     *  [subscribed]. No hace nada si ya estaba en el estado pedido. */
    suspend fun setPodcastSubscribed(podcast: Podcast, subscribed: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SUBSCRIBED_PODCASTS]
                ?.let { runCatching { json.decodeFromString<List<Podcast>>(it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            val updated = if (subscribed) {
                if (current.any { it.collectionId == podcast.collectionId }) current else current + podcast
            } else {
                current.filterNot { it.collectionId == podcast.collectionId }
            }
            prefs[Keys.SUBSCRIBED_PODCASTS] = json.encodeToString(updated)
        }
    }

    suspend fun setBreakingNewsAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BREAKING_NEWS_ALERTS] = enabled }
    }

    /** Marca [link] como ya notificado, y se queda solo con los 100 más
     *  recientes para no crecer sin límite. Sin orden garantizado (es un
     *  Set): basta con que no se pierda ninguno reciente por el camino. */
    suspend fun markBreakingNewsNotified(link: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.NOTIFIED_BREAKING_NEWS_LINKS]
                ?.let { runCatching { json.decodeFromString<Set<String>>(it) }.getOrDefault(emptySet()) }
                ?: emptySet()
            val updated = (current + link).let { set -> if (set.size > 100) set.toList().takeLast(100).toSet() else set }
            prefs[Keys.NOTIFIED_BREAKING_NEWS_LINKS] = json.encodeToString(updated)
        }
    }

    /** Añade [id] al principio del historial de "últimas escuchadas",
     *  eliminando duplicados y quedándose solo con las 10 más recientes. */
    suspend fun addRecentStation(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_STATION_IDS]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(id) + current.filterNot { it == id }).take(10)
            prefs[Keys.RECENT_STATION_IDS] = updated.joinToString(",")
        }
    }
}
