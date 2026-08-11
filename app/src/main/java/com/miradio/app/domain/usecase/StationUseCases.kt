package com.miradio.app.domain.usecase

import com.miradio.app.data.repository.CatalogSyncResult
import com.miradio.app.data.repository.PreferencesRepository
import com.miradio.app.data.repository.StationRepository
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource
import java.util.UUID

class AddStationUseCase(private val repository: StationRepository) {
    suspend operator fun invoke(
        name: String,
        city: String,
        streamUrl: String,
        logoUrl: String?,
        description: String?,
        category: String?,
    ): RadioStation {
        val station = RadioStation(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            city = city.trim(),
            streamUrl = streamUrl.trim(),
            logoUrl = logoUrl?.trim()?.ifBlank { null },
            description = description?.trim()?.ifBlank { null },
            category = category?.trim()?.ifBlank { null },
            isFavorite = false,
            isAvailable = true,
            source = StationSource.LOCAL,
        )
        repository.addStation(station)
        return station
    }
}

class UpdateStationUseCase(private val repository: StationRepository) {
    suspend operator fun invoke(station: RadioStation) = repository.updateStation(station)
}

class DeleteStationUseCase(private val repository: StationRepository) {
    suspend operator fun invoke(id: String) = repository.deleteStation(id)
}

class ToggleFavoriteUseCase(private val repository: StationRepository) {
    suspend operator fun invoke(id: String, isFavorite: Boolean) =
        repository.setFavorite(id, isFavorite)
}

class RefreshRemoteStationsUseCase(
    private val repository: StationRepository,
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke(url: String): CatalogSyncResult {
        val result = repository.syncRemoteCatalog(url)
        if (result is CatalogSyncResult.Success) {
            preferences.setRemoteCatalogUrl(url)
            preferences.setLastSyncNow()
        }
        return result
    }
}
