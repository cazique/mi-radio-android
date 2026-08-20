package com.miradio.app.ui.news

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.NewsArticle
import com.miradio.app.domain.model.NewsCategory
import com.miradio.app.domain.model.NewsSource
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PresetNewsSources
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource
import com.miradio.app.playback.PlaybackController
import com.miradio.app.playback.PlaybackServiceConnector
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val COPE_SOURCES = NewsCategory.entries.map { NewsSource.fromCategory(it) }
private const val AUTO_REFRESH_INTERVAL_MS = 10 * 60_000L

data class NewsUiState(
    val sources: List<NewsSource> = listOf(NewsSource.ForYou) + COPE_SOURCES,
    val selectedSource: NewsSource = NewsSource.ForYou,
    val articles: List<NewsArticle> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val bulletin: NewsArticle? = null,
    val isBulletinLoading: Boolean = true,
    val player: PlayerUiState = PlayerUiState(),
) {
    val isBulletinPlaying: Boolean
        get() = bulletin != null &&
            player.station?.id == bulletinStationId(bulletin) &&
            (player.status == PlaybackStatus.PLAYING || player.status == PlaybackStatus.BUFFERING)
}

sealed class AddSourceState {
    data object Idle : AddSourceState()
    data object Checking : AddSourceState()
    data class Error(val message: String) : AddSourceState()
}

/** Id determinista a partir del enlace del episodio, para poder comparar
 *  "¿es este boletín el que está sonando ahora?" sin necesitar guardar nada. */
fun bulletinStationId(article: NewsArticle): String = "cope_boletin_${article.link.hashCode()}"

/**
 * Reparto por rondas: en cada vuelta, cada fuente aporta tantos artículos
 * como su peso (1 + veces que se le ha leído algo, con un tope para que una
 * sola fuente muy leída no llegue a tapar por completo a las demás). Da un
 * mix parejo al principio (todas empiezan en peso 1) que se va inclinando
 * hacia lo que de verdad se lee, sin depender de comparar fechas entre
 * medios distintos (formatos de RSS demasiado dispares para fiarse).
 */
private fun mergeForYouFeed(perSource: Map<NewsSource, List<NewsArticle>>, affinity: Map<String, Int>): List<NewsArticle> {
    val queues = perSource.mapValues { (_, articles) -> ArrayDeque(articles) }
    val weightOf = perSource.keys.associateWith { source -> 1 + (affinity[source.id] ?: 0).coerceAtMost(8) }
    val orderedSources = perSource.keys.sortedByDescending { weightOf.getValue(it) }
    val result = mutableListOf<NewsArticle>()
    var progressed = true
    while (progressed) {
        progressed = false
        for (source in orderedSources) {
            val queue = queues.getValue(source)
            repeat(weightOf.getValue(source)) {
                queue.removeFirstOrNull()?.let {
                    result += it
                    progressed = true
                }
            }
        }
    }
    return result
}

class NewsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val customSources = MutableStateFlow<List<NewsSource>>(emptyList())
    private val enabledPresets = MutableStateFlow<List<NewsSource>>(emptyList())
    private val selectedSource = MutableStateFlow(NewsSource.ForYou)
    private val articlesState = MutableStateFlow<Pair<List<NewsArticle>, String?>>(emptyList<NewsArticle>() to null)
    private val isLoading = MutableStateFlow(false)
    private val bulletinState = MutableStateFlow<NewsArticle?>(null)
    private val isBulletinLoading = MutableStateFlow(true)

    // Se guarda lo ya descargado por fuente (id) para no volver a pedirlo
    // cada vez que se cambia de pestaña y se vuelve atrás.
    private val cache = mutableMapOf<String, List<NewsArticle>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NewsUiState> = combine(
        customSources,
        enabledPresets,
        selectedSource,
        articlesState,
        isLoading,
    ) { custom, presets, selected, articlesAndError, loading ->
        val (articles, error) = articlesAndError
        NewsUiState(
            sources = listOf(NewsSource.ForYou) + COPE_SOURCES + presets + custom,
            selectedSource = selected,
            articles = articles,
            isLoading = loading,
            error = error,
        )
    }.combine(bulletinState) { state, bulletin -> state.copy(bulletin = bulletin) }
        .combine(isBulletinLoading) { state, bulletinLoading ->
            state.copy(isBulletinLoading = bulletinLoading)
        }.combine(PlaybackServiceConnector.player.flatMapLatest { it?.uiState ?: flowOf(PlayerUiState()) }) { state, playerState ->
            state.copy(player = playerState)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsUiState())

    init {
        viewModelScope.launch {
            container.preferencesRepository.customNewsSources.collect { custom ->
                customSources.value = custom.map { NewsSource.fromCustom(it) }
            }
        }
        viewModelScope.launch {
            container.preferencesRepository.enabledPresetNewsSources.collect { enabledIds ->
                enabledPresets.value = PresetNewsSources.all.filter { it.id in enabledIds }
            }
        }
        selectSource(NewsSource.ForYou)
        loadBulletin()
        // Refresco automático mientras se tiene Noticias abierta, para no
        // depender de que alguien se acuerde de tirar hacia abajo a mano.
        // Incluye el boletín: antes solo se pedía una vez al abrir la
        // pantalla y se quedaba fijo en el mismo episodio para siempre
        // (reportado: "el último boletín siempre es el mismo").
        viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (container.preferencesRepository.newsAutoRefreshEnabled.first()) {
                    selectSource(selectedSource.value, forceReload = true)
                    loadBulletin()
                }
            }
        }
    }

    /** Punto único de entrada para cargar una pestaña: "Para ti" se resuelve
     *  mezclando las demás fuentes activas, el resto pide su feed tal cual. */
    private fun selectSource(source: NewsSource, forceReload: Boolean = false) {
        if (source.id == NewsSource.FOR_YOU_ID) loadForYou(forceReload) else load(source, forceReload)
    }

    private fun loadBulletin() {
        viewModelScope.launch {
            container.newsRepository.fetchLatestBulletin()
                .onSuccess { bulletinState.value = it }
                .onFailure { bulletinState.value = null }
            isBulletinLoading.value = false
        }
    }

    fun onSourceSelect(newSource: NewsSource) {
        if (newSource == selectedSource.value && (cache.containsKey(newSource.id) || isLoading.value)) return
        selectSource(newSource)
    }

    fun refresh() {
        selectSource(selectedSource.value, forceReload = true)
        loadBulletin()
    }

    /** Se llama al abrir una noticia (tocarla en la lista): es la única señal
     *  que alimenta la personalización de "Para ti", ninguna fuente empieza
     *  favorecida sobre las demás. */
    fun onArticleOpened(article: NewsArticle) {
        val sourceId = article.sourceId ?: return
        viewModelScope.launch { container.preferencesRepository.incrementNewsSourceAffinity(sourceId) }
    }

    private val _addSourceState = MutableStateFlow<AddSourceState>(AddSourceState.Idle)
    val addSourceState: StateFlow<AddSourceState> = _addSourceState

    /** Antes de guardar una fuente nueva se comprueba que el feed responde
     *  de verdad: sin esto, una URL mal copiada se quedaría guardada para
     *  siempre mostrando "no se han podido cargar las noticias" cada vez. */
    fun addCustomSource(name: String, feedUrl: String) {
        viewModelScope.launch {
            _addSourceState.value = AddSourceState.Checking
            container.newsRepository.fetchFeed(feedUrl)
                .onSuccess {
                    container.preferencesRepository.addCustomNewsSource(name, feedUrl)
                    _addSourceState.value = AddSourceState.Idle
                }
                .onFailure {
                    _addSourceState.value = AddSourceState.Error(
                        "No se ha podido comprobar esa dirección: ${it.message ?: "error desconocido"}",
                    )
                }
        }
    }

    fun clearAddSourceError() {
        _addSourceState.value = AddSourceState.Idle
    }

    fun removeCustomSource(source: NewsSource) {
        viewModelScope.launch {
            container.preferencesRepository.removeCustomNewsSource(source.id)
            if (selectedSource.value.id == source.id) load(COPE_SOURCES.first())
        }
    }

    private fun load(newSource: NewsSource, forceReload: Boolean = false) {
        selectedSource.value = newSource
        val cached = cache[newSource.id]
        if (cached != null && !forceReload) {
            articlesState.value = cached to null
            return
        }
        isLoading.value = true
        viewModelScope.launch {
            container.newsRepository.fetchFeed(newSource.allFeedUrls, newSource.id)
                .onSuccess { articles ->
                    cache[newSource.id] = articles
                    articlesState.value = articles to null
                }
                .onFailure {
                    articlesState.value = emptyList<NewsArticle>() to
                        "No se han podido cargar las noticias de ${newSource.label.lowercase()}."
                }
            isLoading.value = false
        }
    }

    /** "Para ti": pide en paralelo el feed de cada fuente activa (COPE +
     *  medios activados + propias) y las mezcla dando más hueco, ronda a
     *  ronda, a las fuentes que más se leen (ver [PreferencesRepository.newsSourceAffinity]).
     *  Si alguna fuente falla, sencillamente no aporta nada a la mezcla: un
     *  medio caído no debe tumbar el resto de la pestaña. */
    private fun loadForYou(forceReload: Boolean = false) {
        selectedSource.value = NewsSource.ForYou
        val cached = cache[NewsSource.FOR_YOU_ID]
        if (cached != null && !forceReload) {
            articlesState.value = cached to null
            return
        }
        isLoading.value = true
        viewModelScope.launch {
            val activeSources = COPE_SOURCES + enabledPresets.value + customSources.value
            val perSource = activeSources.map { source ->
                async {
                    val existing = cache[source.id]
                    val articles = if (existing != null && !forceReload) {
                        existing
                    } else {
                        container.newsRepository.fetchFeed(source.allFeedUrls, source.id).getOrElse { emptyList() }
                            .also { cache[source.id] = it }
                    }
                    source to articles
                }
            }.awaitAll().toMap()
            val affinity = container.preferencesRepository.newsSourceAffinity.first()
            val merged = mergeForYouFeed(perSource, affinity)
            cache[NewsSource.FOR_YOU_ID] = merged
            articlesState.value = merged to null
            isLoading.value = false
        }
    }

    /** Reproduce (o pausa, si ya está sonando) el último boletín informativo,
     *  reutilizando el mismo reproductor/notificación que las emisoras. */
    fun onBulletinPlayClick() {
        val bulletin = uiState.value.bulletin ?: return
        val audioUrl = bulletin.audioUrl ?: return
        if (uiState.value.isBulletinPlaying) {
            PlaybackServiceConnector.player.value?.pause()
            return
        }
        val currentPlayer = PlaybackServiceConnector.player.value
        if (currentPlayer != null && currentPlayer.uiState.value.station?.id == bulletinStationId(bulletin)) {
            currentPlayer.play()
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            PlaybackController.ensureServiceStarted(app)
            val player = PlaybackController.awaitPlayer()
            player.playStation(
                RadioStation(
                    id = bulletinStationId(bulletin),
                    name = bulletin.title,
                    city = "Cadena COPE",
                    streamUrl = audioUrl,
                    logoUrl = null,
                    description = bulletin.description,
                    category = "Boletín",
                    isFavorite = false,
                    source = StationSource.LOCAL,
                    mimeType = "audio/mpeg",
                ),
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = radioApp()
                NewsViewModel(app, app.container)
            }
        }
    }
}
