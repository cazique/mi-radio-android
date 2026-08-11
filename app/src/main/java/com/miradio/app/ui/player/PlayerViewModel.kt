package com.miradio.app.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.playback.PlaybackController
import com.miradio.app.playback.PlaybackServiceConnector
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerScreenState(
    val player: PlayerUiState = PlayerUiState(),
    val stations: List<RadioStation> = emptyList(),
    val sleepTimerSecondsLeft: Int? = null,
)

class PlayerViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val sleepTimerSeconds = MutableStateFlow<Int?>(null)
    private var sleepTimerJob: Job? = null

    val uiState: StateFlow<PlayerScreenState> = combine(
        PlaybackServiceConnector.player.flatMapLatest { it?.uiState ?: flowOf(PlayerUiState()) },
        container.stationRepository.stations,
        sleepTimerSeconds,
    ) { playerState, stations, timer ->
        PlayerScreenState(player = playerState, stations = stations, sleepTimerSecondsLeft = timer)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerScreenState())

    fun onPlayPauseClick() {
        val player = PlaybackServiceConnector.player.value ?: return
        val isActive = player.uiState.value.status == PlaybackStatus.PLAYING ||
            player.uiState.value.status == PlaybackStatus.BUFFERING
        if (isActive) player.pause() else player.play()
    }

    fun onStop() {
        PlaybackServiceConnector.player.value?.stop()
        cancelSleepTimer()
    }

    fun onFavoriteToggle() {
        val station = PlaybackServiceConnector.player.value?.uiState?.value?.station ?: return
        viewModelScope.launch { container.toggleFavoriteUseCase(station.id, !station.isFavorite) }
    }

    /** Emisora anterior/siguiente dentro de la lista actual, para los botones ⏮ ⏭. */
    fun onSkipStation(delta: Int) {
        val state = uiState.value
        val current = state.player.station ?: return
        val list = state.stations
        val index = list.indexOfFirst { it.id == current.id }
        if (index == -1 || list.isEmpty()) return
        val next = list[(index + delta + list.size) % list.size]
        viewModelScope.launch {
            val app = getApplication<Application>()
            PlaybackController.ensureServiceStarted(app)
            val player = PlaybackController.awaitPlayer()
            player.playStation(next)
            container.preferencesRepository.setLastStation(next.id)
        }
    }

    fun startSleepTimer(totalSeconds: Int) {
        sleepTimerJob?.cancel()
        sleepTimerSeconds.value = totalSeconds
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                sleepTimerSeconds.value = remaining
            }
            PlaybackServiceConnector.player.value?.pause()
            sleepTimerSeconds.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerSeconds.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = radioApp()
                PlayerViewModel(app, app.container)
            }
        }
    }
}
