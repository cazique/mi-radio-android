package com.miradio.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.miradio.app.AppContainer
import com.miradio.app.RadioApp
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.playback.PlaybackController
import com.miradio.app.playback.PlaybackServiceConnector
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val allStations: List<RadioStation> = emptyList(),
    val visibleStations: List<RadioStation> = emptyList(),
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val player: PlayerUiState = PlayerUiState(),
    /** Emisora a mostrar en la tarjeta principal cuando el servicio aún no ha
     *  cargado ninguna (p. ej. justo al abrir la app): la última escuchada. */
    val lastStation: RadioStation? = null,
    /** Últimas emisoras escuchadas, la más reciente primero. */
    val recentStations: List<RadioStation> = emptyList(),
    val isLoading: Boolean = true,
    /** Con el modo simple activo, Inicio se reduce a "ahora suena" +
     *  favoritas en tarjetas grandes, sin acceso a Explorar/Añadir emisora. */
    val simpleMode: Boolean = false,
) {
    val displayedStation: RadioStation? get() = player.station ?: lastStation
}

class HomeViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val searchQuery = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState = combine(
        container.stationRepository.stations,
        searchQuery,
        favoritesOnly,
        PlaybackServiceConnector.player.flatMapLatest { it?.uiState ?: flowOf(PlayerUiState()) },
        container.preferencesRepository.lastStationId,
    ) { stations, query, favOnly, playerState, lastStationId ->
        val filtered = stations
            .filter { !favOnly || it.isFavorite }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.city.contains(query, ignoreCase = true)
            }
        HomeUiState(
            allStations = stations,
            visibleStations = filtered,
            searchQuery = query,
            showFavoritesOnly = favOnly,
            player = playerState,
            lastStation = stations.find { it.id == lastStationId },
            isLoading = false,
        )
    }.combine(container.preferencesRepository.recentStationIds) { state, recentIds ->
        state.copy(
            recentStations = recentIds.mapNotNull { id -> state.allStations.find { it.id == id } },
        )
    }.combine(container.preferencesRepository.simpleMode) { state, simple ->
        state.copy(simpleMode = simple)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { container.stationRepository.ensureSeeded() }
    }

    fun setShowFavoritesOnly(value: Boolean) {
        favoritesOnly.value = value
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onStationClick(station: RadioStation) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            PlaybackController.ensureServiceStarted(app)
            val player = PlaybackController.awaitPlayer()
            player.playStation(station)
            container.preferencesRepository.setLastStation(station.id)
        }
    }

    /** Botón grande de play/pause de la tarjeta principal. Si el servicio aún no
     *  ha cargado ninguna emisora (recién abierta la app), arranca la última
     *  escuchada; si ya hay una cargada, alterna play/pausa. */
    fun onPrimaryPlayClick(state: HomeUiState) {
        val activePlayer = PlaybackServiceConnector.player.value
        if (activePlayer == null || activePlayer.uiState.value.station == null) {
            state.lastStation?.let { onStationClick(it) }
            return
        }
        val isActive = activePlayer.uiState.value.status == PlaybackStatus.PLAYING ||
            activePlayer.uiState.value.status == PlaybackStatus.BUFFERING
        if (isActive) activePlayer.pause() else activePlayer.play()
    }

    fun onFavoriteToggle(station: RadioStation) {
        viewModelScope.launch {
            container.toggleFavoriteUseCase(station.id, !station.isFavorite)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = radioApp()
                HomeViewModel(app, app.container)
            }
        }
    }
}
