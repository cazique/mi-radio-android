package com.miradio.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.miradio.app.AppContainer
import com.miradio.app.data.repository.CatalogSyncResult
import com.miradio.app.domain.model.AemetMunicipio
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
    val simpleMode: Boolean = false,
    val hideAddButton: Boolean = false,
    val dynamicColor: Boolean = false,
    val newsAutoRefresh: Boolean = true,
    val aemetApiKey: String = "",
    val aemetMunicipioName: String? = null,
)

data class AemetSearchState(
    val results: List<AemetMunicipio> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
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
    }.combine(container.preferencesRepository.simpleMode) { state, simple ->
        state.copy(simpleMode = simple)
    }.combine(container.preferencesRepository.hideAddButton) { state, hideAdd ->
        state.copy(hideAddButton = hideAdd)
    }.combine(container.preferencesRepository.dynamicColorEnabled) { state, dynamic ->
        state.copy(dynamicColor = dynamic)
    }.combine(container.preferencesRepository.newsAutoRefreshEnabled) { state, autoRefresh ->
        state.copy(newsAutoRefresh = autoRefresh)
    }.combine(container.preferencesRepository.aemetApiKey) { state, key ->
        state.copy(aemetApiKey = key.orEmpty())
    }.combine(container.preferencesRepository.aemetMunicipio) { state, municipio ->
        state.copy(aemetMunicipioName = municipio?.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _aemetSearch = MutableStateFlow(AemetSearchState())
    val aemetSearch: StateFlow<AemetSearchState> = _aemetSearch
    private var allMunicipios: List<AemetMunicipio>? = null

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
    }

    fun onTextScaleChange(scale: Float) {
        viewModelScope.launch { container.preferencesRepository.setTextScale(scale) }
    }

    fun onDebugModeChange(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setDebugMode(enabled) }
    }

    fun onSimpleModeChange(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setSimpleMode(enabled) }
    }

    fun onHideAddButtonChange(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setHideAddButton(enabled) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setDynamicColorEnabled(enabled) }
    }

    fun onNewsAutoRefreshChange(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setNewsAutoRefreshEnabled(enabled) }
    }

    fun onRemoteUrlChange(url: String) {
        editableUrl.value = url
    }

    fun onAemetApiKeySave(key: String) {
        viewModelScope.launch { container.preferencesRepository.setAemetApiKey(key) }
    }

    fun onAemetDisable() {
        viewModelScope.launch { container.preferencesRepository.clearAemetApiKey() }
        allMunicipios = null
        _aemetSearch.value = AemetSearchState()
    }

    /** Descarga el maestro de municipios (o lo coge de caché) la primera vez
     *  que se busca, y filtra en memoria por nombre en las siguientes. */
    fun onAemetMunicipioQuery(query: String) {
        val key = uiState.value.aemetApiKey
        if (key.isBlank()) return
        if (query.isBlank()) {
            _aemetSearch.value = AemetSearchState()
            return
        }
        val cached = allMunicipios
        if (cached != null) {
            _aemetSearch.value = AemetSearchState(results = filterMunicipios(cached, query))
            return
        }
        viewModelScope.launch {
            _aemetSearch.value = AemetSearchState(isLoading = true)
            container.aemetService.fetchMunicipios(getApplication(), key)
                .onSuccess { municipios ->
                    allMunicipios = municipios
                    _aemetSearch.value = AemetSearchState(results = filterMunicipios(municipios, query))
                }
                .onFailure {
                    _aemetSearch.value = AemetSearchState(
                        error = "No se ha podido descargar el listado de municipios: ${it.message ?: "error desconocido"}",
                    )
                }
        }
    }

    private fun filterMunicipios(all: List<AemetMunicipio>, query: String): List<AemetMunicipio> {
        val normalized = query.trim().lowercase()
        return all.filter { it.nombre.lowercase().contains(normalized) }.take(30)
    }

    fun onAemetMunicipioSelect(municipio: AemetMunicipio) {
        viewModelScope.launch { container.preferencesRepository.setAemetMunicipio(municipio.id, municipio.nombre) }
        _aemetSearch.value = AemetSearchState()
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
