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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastsUiState(
    val query: String = "",
    val results: List<Podcast> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** true = los resultados son de una búsqueda; false = "Más populares". */
    val isSearchResults: Boolean = false,
    /** Podcasts seguidos, para la sección "Tus podcasts" (solo se muestra
     *  sin búsqueda activa, igual que "Más populares"). */
    val subscriptions: List<Podcast> = emptyList(),
)

class PodcastsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val baseState = MutableStateFlow(PodcastsUiState())

    val state: StateFlow<PodcastsUiState> = combine(
        baseState,
        container.preferencesRepository.subscribedPodcasts,
    ) { base, subscriptions ->
        base.copy(subscriptions = subscriptions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastsUiState())

    private var loadJob: Job? = null

    init {
        loadTop()
    }

    private fun loadTop() {
        loadJob?.cancel()
        baseState.update { it.copy(isLoading = true, isSearchResults = false, error = null) }
        loadJob = viewModelScope.launch {
            container.podcastRepository.topPodcasts()
                .onSuccess { list -> baseState.update { it.copy(results = list, isLoading = false) } }
                .onFailure {
                    baseState.update {
                        it.copy(isLoading = false, results = emptyList(), error = "No se han podido cargar los podcasts más populares.")
                    }
                }
        }
    }

    /** Con debounce: no se busca en cada pulsación, sino 400 ms después de
     *  la última, para no lanzar una petición por cada letra tecleada. */
    fun onQueryChange(query: String) {
        baseState.update { it.copy(query = query) }
        loadJob?.cancel()
        if (query.isBlank()) {
            loadTop()
            return
        }
        loadJob = viewModelScope.launch {
            delay(400)
            baseState.update { it.copy(isLoading = true, isSearchResults = true, error = null) }
            container.podcastRepository.searchPodcasts(query)
                .onSuccess { list -> baseState.update { it.copy(results = list, isLoading = false) } }
                .onFailure {
                    baseState.update {
                        it.copy(isLoading = false, results = emptyList(), error = "No se han podido buscar podcasts.")
                    }
                }
        }
    }

    fun retry() {
        if (baseState.value.isSearchResults) onQueryChange(baseState.value.query) else loadTop()
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
