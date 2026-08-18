package com.miradio.app.ui.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.WeatherInfo
import com.miradio.app.ui.util.radioApp
import com.miradio.app.util.DiagnosticsLog
import com.miradio.app.util.LocationProvider
import com.miradio.app.util.WeatherResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class WeatherUiState {
    /** Todavía no se ha pedido el permiso de ubicación, o se pidió y se denegó. */
    data class NeedsPermission(val deniedBefore: Boolean = false) : WeatherUiState()
    data object Loading : WeatherUiState()
    data class Loaded(val weather: WeatherInfo) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<WeatherUiState>(
        if (LocationProvider.hasPermission(application)) WeatherUiState.Loading else WeatherUiState.NeedsPermission(),
    )
    val state: StateFlow<WeatherUiState> = _state

    init {
        if (LocationProvider.hasPermission(application)) load()
    }

    /** Llamar tras el resultado del diálogo de permiso del sistema. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) load() else _state.value = WeatherUiState.NeedsPermission(deniedBefore = true)
    }

    fun retry() {
        if (LocationProvider.hasPermission(getApplication())) load() else _state.value = WeatherUiState.NeedsPermission()
    }

    private fun load() {
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            val context = getApplication<Application>()
            // 12 s de margen: en interiores o con GPS frío puede tardar; pasado
            // ese tiempo es mejor mostrar un error con reintentar que dejar el
            // "cargando" girando para siempre.
            val location = withTimeoutOrNull(12_000) { LocationProvider.getLocation(context) }
            if (location == null) {
                DiagnosticsLog.logWarning(context, "WeatherViewModel", "No se pudo obtener la ubicación")
                _state.value = WeatherUiState.Error("No se ha podido obtener la ubicación actual")
                return@launch
            }
            when (val result = container.weatherService.fetchWeather(context, location)) {
                is WeatherResult.Success -> _state.value = WeatherUiState.Loaded(result.weather)
                is WeatherResult.Failure -> {
                    DiagnosticsLog.logWarning(context, "WeatherViewModel", "fetchWeather falló: ${result.reason}")
                    _state.value = WeatherUiState.Error(result.reason)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = radioApp()
                WeatherViewModel(app, app.container)
            }
        }
    }
}
