package com.miradio.app.ui.podcast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.PodcastEpisode
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastEpisodesUiState(
    val podcastName: String = "",
    val podcastArtworkUrl: String? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val player: PlayerUiState = PlayerUiState(),
    /** Posición (ms) por episodio ya escuchado en parte, para poder mostrar
     *  "reanudar" en vez de "reproducir" en la lista. */
    val progress: Map<String, Long> = emptyMap(),
) {
    val playingEpisodeId: String?
        get() = player.station?.id?.takeIf {
            player.status == PlaybackStatus.PLAYING || player.status == PlaybackStatus.BUFFERING || player.status == PlaybackStatus.PAUSED
        }
}

class PodcastEpisodesViewModel(
    application: Application,
    private val container: AppContainer,
    private val collectionId: String,
) : AndroidViewModel(application) {

    private val podcastState = MutableStateFlow(PodcastEpisodesUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PodcastEpisodesUiState> = combine(
        podcastState,
        container.preferencesRepository.podcastProgress,
        PlaybackServiceConnector.player.flatMapLatest { it?.uiState ?: flowOf(PlayerUiState()) },
    ) { base, progress, player ->
        base.copy(progress = progress, player = player)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastEpisodesUiState())

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            container.podcastRepository.resolvePodcast(collectionId)
                .onSuccess { podcast ->
                    podcastState.update { it.copy(podcastName = podcast.name, podcastArtworkUrl = podcast.artworkUrl) }
                    container.podcastRepository.fetchEpisodes(podcast)
                        .onSuccess { episodes -> podcastState.update { it.copy(episodes = episodes, isLoading = false) } }
                        .onFailure {
                            podcastState.update { it.copy(isLoading = false, error = "No se han podido cargar los episodios.") }
                        }
                }
                .onFailure {
                    podcastState.update { it.copy(isLoading = false, error = "No se ha podido abrir este podcast.") }
                }
        }
    }

    /** Si ya es el episodio sonando, alterna play/pausa; si no, lo arranca
     *  (retomando donde se dejó, si hay progreso guardado). */
    fun onEpisodeClick(episode: PodcastEpisode) {
        val player = PlaybackServiceConnector.player.value
        if (player != null && player.uiState.value.station?.id == episode.id) {
            if (player.uiState.value.status == PlaybackStatus.PLAYING) player.pause() else player.play()
            return
        }
        viewModelScope.launch {
            val startPositionMs = container.preferencesRepository.podcastProgress.first()[episode.id] ?: 0L
            val app = getApplication<Application>()
            PlaybackController.ensureServiceStarted(app)
            val activePlayer = PlaybackController.awaitPlayer()
            activePlayer.playStation(episode.toRadioStation(), startPositionMs = startPositionMs)
        }
    }

    companion object {
        fun factory(collectionId: String) = viewModelFactory {
            initializer {
                val app = radioApp()
                PodcastEpisodesViewModel(app, app.container, collectionId)
            }
        }
    }
}

/** Un episodio se reproduce igual que una emisora (mismo ExoPlayer, misma
 *  notificación, mismo widget, mismo Cast): se modela como una RadioStation
 *  "de un solo uso", el mismo truco que ya usa el boletín de noticias. */
fun PodcastEpisode.toRadioStation(): RadioStation = RadioStation(
    id = id,
    name = title,
    city = podcastName,
    streamUrl = audioUrl,
    logoUrl = podcastArtworkUrl,
    description = description,
    category = "Podcast",
    isFavorite = false,
    source = StationSource.LOCAL,
    mimeType = "audio/mpeg",
)
