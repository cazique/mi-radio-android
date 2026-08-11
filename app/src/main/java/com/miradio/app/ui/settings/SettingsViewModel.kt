package com.miradio.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.miradio.app.AppContainer
import com.miradio.app.data.repository.CatalogSyncResult
import com.miradio.app.domain.model.ThemeMode
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val remoteCatalogUrl: String = "",
    val lastSyncMillis: Long? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncFailed: Boolean = false,
    val textScale: Float = 1f,
    val debugMode: Boolean = true,
)

class SettingsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val editableUrl = MutableStateFlow<String?>(null)
    private val syncStatus = MutableStateFlow<Triple<Boolean, String?, Boolean>>(Triple(false, null, false))

    val uiState: StateFlow<SettingsUiState> = combine(
        container.preferencesRepository.themeMode,
        container.preferencesRepository.remoteCatalogUrl,
        container.preferencesRepository.lastSyncMillis,
        editableUrl,
        syncStatus,
    ) { theme, savedUrl, lastSync, edited, sync ->
        SettingsUiState(
            themeMode = theme,
            remoteCatalogUrl = edited ?: savedUrl,
            lastSyncMillis = lastSync,
            isSyncing = sync.first,
            syncMessage = sync.second,
            syncFailed = sync.third,
        )
    }.combine(container.preferencesRepository.textScale) { state, scale ->
        state.copy(textScale = scale)
    }.combine(container.preferencesRepository.debugMode) { state, debug ->
        state.copy(debugMode = debug)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
    }

    fun onTextScaleChange(scale: Float) {
        viewModelScope.launch { container.preferencesRepository.setTextScale(scale) }
    }

    fun onDebugModeChange(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setDebugMode(enabled) }
    }

    fun onRemoteUrlChange(url: String) {
        editableUrl.value = url
    }

    fun refreshRemoteCatalog() {
        val url = uiState.value.remoteCatalogUrl
        viewModelScope.launch {
            syncStatus.value = Triple(true, null, false)
            when (val result = container.refreshRemoteStationsUseCase(url)) {
                is CatalogSyncResult.Success ->
                    syncStatus.value = Triple(false, "Se han sincronizado ${result.addedOrUpdated} emisoras.", false)
                is CatalogSyncResult.Failure ->
                    syncStatus.value = Triple(false, result.reason, true)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = radioApp()
                SettingsViewModel(app, app.container)
            }
        }
    }
}
