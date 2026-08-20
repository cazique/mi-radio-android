package com.miradio.app.ui.podcast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.Podcast
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastsUiState(
    val query: String = "",
    val results: List<Podcast> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** true = los resultados son de una búsqueda; false = "Más populares". */
    val isSearchResults: Boolean = false,
)

class PodcastsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PodcastsUiState())
    val state: StateFlow<PodcastsUiState> = _state

    private var loadJob: Job? = null

    init {
        loadTop()
    }

    private fun loadTop() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, isSearchResults = false, error = null) }
        loadJob = viewModelScope.launch {
            container.podcastRepository.topPodcasts()
                .onSuccess { list -> _state.update { it.copy(results = list, isLoading = false) } }
                .onFailure {
                    _state.update {
                        it.copy(isLoading = false, results = emptyList(), error = "No se han podido cargar los podcasts más populares.")
                    }
                }
        }
    }

    /** Con debounce: no se busca en cada pulsación, sino 400 ms después de
     *  la última, para no lanzar una petición por cada letra tecleada. */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        loadJob?.cancel()
        if (query.isBlank()) {
            loadTop()
            return
        }
        loadJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(isLoading = true, isSearchResults = true, error = null) }
            container.podcastRepository.searchPodcasts(query)
                .onSuccess { list -> _state.update { it.copy(results = list, isLoading = false) } }
                .onFailure {
                    _state.update {
                        it.copy(isLoading = false, results = emptyList(), error = "No se han podido buscar podcasts.")
                    }
                }
        }
    }

    fun retry() {
        if (_state.value.isSearchResults) onQueryChange(_state.value.query) else loadTop()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = radioApp()
                PodcastsViewModel(app, app.container)
            }
        }
    }
}
