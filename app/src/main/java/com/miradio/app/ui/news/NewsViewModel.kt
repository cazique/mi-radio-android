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
    val sources: List<NewsSource> = COPE_SOURCES,
    val selectedSource: NewsSource = COPE_SOURCES.first(),
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

class NewsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val customSources = MutableStateFlow<List<NewsSource>>(emptyList())
    private val enabledPresets = MutableStateFlow<List<NewsSource>>(emptyList())
    private val selectedSource = MutableStateFlow(COPE_SOURCES.first())
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
            sources = COPE_SOURCES + presets + custom,
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
        load(COPE_SOURCES.first())
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
                    load(selectedSource.value, forceReload = true)
                    loadBulletin()
                }
            }
        }
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
        load(newSource)
    }

    fun refresh() {
        load(selectedSource.value, forceReload = true)
        loadBulletin()
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
            container.newsRepository.fetchFeed(newSource.feedUrl)
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
