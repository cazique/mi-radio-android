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
import com.miradio.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PLAYBACK_DELAY_SECONDS = intPreferencesKey("playback_delay_seconds")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val RECENT_STATION_IDS = stringPreferencesKey("recent_station_ids")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    }

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

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

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

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
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
