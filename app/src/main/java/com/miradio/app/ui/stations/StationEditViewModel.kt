package com.miradio.app.ui.stations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.miradio.app.AppContainer
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.usecase.UrlValidationResult
import com.miradio.app.ui.util.radioApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UrlCheckState { IDLE, CHECKING, OK, FAILED }

data class StationFormState(
    val stationId: String? = null,
    val name: String = "",
    val city: String = "",
    val streamUrl: String = "",
    val logoUrl: String = "",
    val description: String = "",
    val category: String = "",
    val nameError: Boolean = false,
    val urlError: Boolean = false,
    val urlCheckState: UrlCheckState = UrlCheckState.IDLE,
    val urlCheckMessage: String? = null,
    val isSaving: Boolean = false,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank() && streamUrl.isNotBlank()
}

class StationEditViewModel(
    application: Application,
    private val container: AppContainer,
    private val editingStationId: String?,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(StationFormState(stationId = editingStationId, isEditing = editingStationId != null))
    val state: StateFlow<StationFormState> = _state

    init {
        editingStationId?.let { id ->
            viewModelScope.launch {
                container.stationRepository.getById(id)?.let { station ->
                    _state.update {
                        it.copy(
                            name = station.name,
                            city = station.city,
                            streamUrl = station.streamUrl,
                            logoUrl = station.logoUrl.orEmpty(),
                            description = station.description.orEmpty(),
                            category = station.category.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun onCityChange(value: String) = _state.update { it.copy(city = value) }
    fun onStreamUrlChange(value: String) =
        _state.update { it.copy(streamUrl = value, urlError = false, urlCheckState = UrlCheckState.IDLE) }
    fun onLogoUrlChange(value: String) = _state.update { it.copy(logoUrl = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onCategoryChange(value: String) = _state.update { it.copy(category = value) }

    fun checkStreamUrl() {
        val url = _state.value.streamUrl
        if (!container.validateStreamUrlUseCase.looksValid(url)) {
            _state.update { it.copy(urlError = true, urlCheckState = UrlCheckState.FAILED) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(urlCheckState = UrlCheckState.CHECKING) }
            when (val result = container.validateStreamUrlUseCase(url)) {
                UrlValidationResult.Valid -> _state.update { it.copy(urlCheckState = UrlCheckState.OK) }
                is UrlValidationResult.Unreachable -> _state.update {
                    it.copy(urlCheckState = UrlCheckState.FAILED, urlCheckMessage = result.reason)
                }
                UrlValidationResult.InvalidFormat -> _state.update {
                    it.copy(urlError = true, urlCheckState = UrlCheckState.FAILED)
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val nameBlank = current.name.isBlank()
        val urlInvalid = !container.validateStreamUrlUseCase.looksValid(current.streamUrl)
        if (nameBlank || urlInvalid) {
            _state.update { it.copy(nameError = nameBlank, urlError = urlInvalid) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            if (current.isEditing && current.stationId != null) {
                val existing = container.stationRepository.getById(current.stationId)
                val updated = (existing ?: emptyStation(current.stationId)).copy(
                    name = current.name.trim(),
                    city = current.city.trim(),
                    streamUrl = current.streamUrl.trim(),
                    logoUrl = current.logoUrl.trim().ifBlank { null },
                    description = current.description.trim().ifBlank { null },
                    category = current.category.trim().ifBlank { null },
                )
                container.updateStationUseCase(updated)
            } else {
                container.addStationUseCase(
                    name = current.name,
                    city = current.city,
                    streamUrl = current.streamUrl,
                    logoUrl = current.logoUrl.ifBlank { null },
                    description = current.description.ifBlank { null },
                    category = current.category.ifBlank { null },
                )
            }
            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.stationId ?: return
        viewModelScope.launch {
            container.deleteStationUseCase(id)
            _state.update { it.copy(deleted = true) }
        }
    }

    private fun emptyStation(id: String) = RadioStation(
        id = id, name = "", city = "", streamUrl = "", logoUrl = null,
        description = null, category = null, isFavorite = false,
    )

    companion object {
        fun factory(stationId: String?) = viewModelFactory {
            initializer {
                val app = radioApp()
                StationEditViewModel(app, app.container, stationId)
            }
        }
    }
}
