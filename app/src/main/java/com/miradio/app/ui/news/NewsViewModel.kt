package com.miradio.app.ui.news

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.NewsArticle
import com.miradio.app.domain.model.NewsCategory
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource
import com.miradio.app.playback.PlaybackController
import com.miradio.app.playback.PlaybackServiceConnector
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewsUiState(
    val category: NewsCategory = NewsCategory.PORTADA,
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

/** Id determinista a partir del enlace del episodio, para poder comparar
 *  "¿es este boletín el que está sonando ahora?" sin necesitar guardar nada. */
fun bulletinStationId(article: NewsArticle): String = "cope_boletin_${article.link.hashCode()}"

class NewsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val category = MutableStateFlow(NewsCategory.PORTADA)
    private val articlesState = MutableStateFlow<Pair<List<NewsArticle>, String?>>(emptyList<NewsArticle>() to null)
    private val isLoading = MutableStateFlow(false)
    private val bulletinState = MutableStateFlow<NewsArticle?>(null)
    private val isBulletinLoading = MutableStateFlow(true)

    // Se guarda lo ya descargado por categoría para no volver a pedirlo cada
    // vez que se cambia de pestaña y se vuelve atrás.
    private val cache = mutableMapOf<NewsCategory, List<NewsArticle>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NewsUiState> = combine(
        category,
        articlesState,
        isLoading,
        bulletinState,
    ) { cat, articlesAndError, loading, bulletin ->
        val (articles, error) = articlesAndError
        NewsUiState(category = cat, articles = articles, isLoading = loading, error = error, bulletin = bulletin)
    }.combine(isBulletinLoading) { state, bulletinLoading ->
        state.copy(isBulletinLoading = bulletinLoading)
    }.combine(PlaybackServiceConnector.player.flatMapLatest { it?.uiState ?: flowOf(PlayerUiState()) }) { state, playerState ->
        state.copy(player = playerState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsUiState())

    init {
        load(NewsCategory.PORTADA)
        viewModelScope.launch {
            container.newsRepository.fetchLatestBulletin()
                .onSuccess { bulletinState.value = it }
                .onFailure { bulletinState.value = null }
            isBulletinLoading.value = false
        }
    }

    fun onCategorySelect(newCategory: NewsCategory) {
        if (newCategory == category.value && (cache.containsKey(newCategory) || isLoading.value)) return
        load(newCategory)
    }

    fun refresh() = load(category.value, forceReload = true)

    private fun load(newCategory: NewsCategory, forceReload: Boolean = false) {
        category.value = newCategory
        val cached = cache[newCategory]
        if (cached != null && !forceReload) {
            articlesState.value = cached to null
            return
        }
        isLoading.value = true
        viewModelScope.launch {
            container.newsRepository.fetchFeed(newCategory.feedUrl)
                .onSuccess { articles ->
                    cache[newCategory] = articles
                    articlesState.value = articles to null
                }
                .onFailure {
                    articlesState.value = emptyList<NewsArticle>() to
                        "No se han podido cargar las noticias de ${newCategory.label.lowercase()}."
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
