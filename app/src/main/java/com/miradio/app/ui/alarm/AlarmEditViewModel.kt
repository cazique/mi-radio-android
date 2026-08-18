package com.miradio.app.ui.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.Alarm
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class AlarmEditState(
    val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDays: Set<Int> = emptySet(),
    val station: RadioStation? = null,
    val volumeIncreasing: Boolean = true,
    val startVolume: Float = 0.3f,
    val fadeInSeconds: Int = 60,
    val enabled: Boolean = true,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val isEditing: Boolean = false,
    /** false mientras se está leyendo la alarma existente de Room. La
     *  pantalla espera a esto antes de crear el selector de hora: si no,
     *  se inicializaba con la hora por defecto (7:00) y esa hora "de
     *  paso" se colaba como si el usuario la hubiera tocado. Para una
     *  alarma nueva (sin nada que cargar) empieza ya en true. */
    val isLoaded: Boolean = true,
)

/** L, M, X, J, V, S, D en el orden habitual español (lunes primero), cada
 *  uno con su valor de java.util.Calendar.DAY_OF_WEEK. */
val WEEK_DAYS_ES: List<Pair<String, Int>> = listOf(
    "L" to Calendar.MONDAY,
    "M" to Calendar.TUESDAY,
    "X" to Calendar.WEDNESDAY,
    "J" to Calendar.THURSDAY,
    "V" to Calendar.FRIDAY,
    "S" to Calendar.SATURDAY,
    "D" to Calendar.SUNDAY,
)

class AlarmEditViewModel(
    application: Application,
    private val container: AppContainer,
    private val alarmId: Long?,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AlarmEditState(isEditing = alarmId != null, isLoaded = alarmId == null))
    val state: StateFlow<AlarmEditState> = _state

    private val stationQuery = MutableStateFlow("")
    val stationResults: StateFlow<List<RadioStation>> = combine(
        container.stationRepository.stations,
        stationQuery,
    ) { stations, query ->
        if (query.isBlank()) {
            stations.filter { it.isFavorite }.take(20)
        } else {
            stations.filter { it.name.contains(query, ignoreCase = true) }.take(30)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (alarmId != null) {
            viewModelScope.launch {
                val alarm = container.alarmRepository.alarms.first().firstOrNull { it.id == alarmId }
                if (alarm == null) {
                    _state.update { it.copy(isLoaded = true) }
                    return@launch
                }
                _state.update {
                    it.copy(
                        isLoaded = true,
                        id = alarm.id,
                        hour = alarm.hour,
                        minute = alarm.minute,
                        repeatDays = alarm.repeatDays,
                        station = RadioStation(
                            id = alarm.stationId,
                            name = alarm.stationName,
                            city = "",
                            streamUrl = alarm.stationStreamUrl,
                            logoUrl = alarm.stationLogoUrl,
                            description = null,
                            category = null,
                            isFavorite = false,
                        ),
                        volumeIncreasing = alarm.volumeIncreasing,
                        startVolume = alarm.startVolume,
                        fadeInSeconds = alarm.fadeInSeconds,
                        enabled = alarm.enabled,
                    )
                }
            }
        }
    }

    fun onTimeChange(hour: Int, minute: Int) = _state.update { it.copy(hour = hour, minute = minute) }

    fun onDayToggle(day: Int) = _state.update {
        it.copy(repeatDays = if (day in it.repeatDays) it.repeatDays - day else it.repeatDays + day)
    }

    fun onStationSelect(station: RadioStation) = _state.update { it.copy(station = station) }

    fun onStationQueryChange(query: String) {
        stationQuery.value = query
    }

    fun onVolumeIncreasingChange(enabled: Boolean) = _state.update { it.copy(volumeIncreasing = enabled) }

    fun onStartVolumeChange(volume: Float) = _state.update { it.copy(startVolume = volume) }

    fun onFadeInSecondsChange(seconds: Int) = _state.update { it.copy(fadeInSeconds = seconds) }

    fun onEnabledChange(enabled: Boolean) = _state.update { it.copy(enabled = enabled) }

    fun save() {
        val current = _state.value
        val station = current.station ?: return
        viewModelScope.launch {
            container.alarmRepository.save(
                Alarm(
                    id = current.id,
                    hour = current.hour,
                    minute = current.minute,
                    repeatDays = current.repeatDays,
                    stationId = station.id,
                    stationName = station.name,
                    stationStreamUrl = station.streamUrl,
                    stationLogoUrl = station.logoUrl,
                    startVolume = current.startVolume,
                    volumeIncreasing = current.volumeIncreasing,
                    fadeInSeconds = current.fadeInSeconds,
                    enabled = current.enabled,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.id
        if (id == 0L) return
        viewModelScope.launch {
            container.alarmRepository.deleteById(id)
            _state.update { it.copy(deleted = true) }
        }
    }

    companion object {
        fun factory(alarmId: Long?) = viewModelFactory {
            initializer {
                val app = radioApp()
                AlarmEditViewModel(app, app.container, alarmId)
            }
        }
    }
}
