package com.miradio.app.ui.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.Alarm
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    val alarms: StateFlow<List<Alarm>> = container.alarmRepository.alarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onToggle(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch { container.alarmRepository.setEnabled(alarm, enabled) }
    }

    fun onDelete(alarm: Alarm) {
        viewModelScope.launch { container.alarmRepository.delete(alarm) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = radioApp()
                AlarmsViewModel(app, app.container)
            }
        }
    }
}
