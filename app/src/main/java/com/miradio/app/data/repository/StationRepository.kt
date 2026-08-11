package com.miradio.app.data.repository

import android.content.Context
import com.miradio.app.data.database.StationDao
import com.miradio.app.data.database.toDomain
import com.miradio.app.data.database.toEntity
import com.miradio.app.data.remote.RemoteCatalogResult
import com.miradio.app.data.remote.RemoteStationsService
import com.miradio.app.data.remote.StationCatalogDto
import com.miradio.app.data.remote.toDomain
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed class CatalogSyncResult {
    data class Success(val addedOrUpdated: Int) : CatalogSyncResult()
    data class Failure(val reason: String) : CatalogSyncResult()
}

/**
 * Única fuente de verdad para las emisoras: combina el catálogo incluido en
 * la app (assets/stations_seed.json), lo que el usuario añade a mano y lo
 * que llega del catálogo remoto configurable. Room es la caché local que
 * alimenta la UI de forma reactiva; el resto de fuentes solo escriben en ella.
 */
class StationRepository(
    private val context: Context,
    private val dao: StationDao,
    private val remoteService: RemoteStationsService = RemoteStationsService(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    val stations: Flow<List<RadioStation>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext
        val json = context.assets.open("stations_seed.json").bufferedReader().use { it.readText() }
        val catalog = this@StationRepository.json.decodeFromString(StationCatalogDto.serializer(), json)
        val entities = catalog.stations.mapIndexed { index, dto ->
            dto.toDomain(StationSource.SEED, index).toEntity()
        }
        dao.upsertAll(entities)
    }

    suspend fun getById(id: String): RadioStation? = dao.getById(id)?.toDomain()

    suspend fun addStation(station: RadioStation) = withContext(Dispatchers.IO) {
        dao.upsert(station.toEntity())
    }

    suspend fun updateStation(station: RadioStation) = withContext(Dispatchers.IO) {
        dao.update(station.toEntity())
    }

    suspend fun deleteStation(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun setFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, isFavorite)
    }

    /**
     * Descarga el catálogo remoto y fusiona las emisoras (upsert por id).
     * No borra emisoras locales del usuario ni las "seed": solo reemplaza
     * las que ya vinieron del catálogo remoto en una sincronización anterior
     * más las que llegan nuevas, para que quitar una emisora del JSON remoto
     * también la retire de la app.
     */
    suspend fun syncRemoteCatalog(url: String): CatalogSyncResult = withContext(Dispatchers.IO) {
        when (val result = remoteService.fetchCatalog(url)) {
            is RemoteCatalogResult.Failure -> CatalogSyncResult.Failure(result.reason)
            is RemoteCatalogResult.Success -> {
                // Recordamos qué emisoras remotas estaban marcadas como favoritas
                // antes de reemplazar el catálogo, para no perder esa marca al
                // volver a sincronizar.
                val previousFavoriteIds = dao.favoriteIdsBySource(StationSource.REMOTE.name).toSet()
                dao.deleteBySource(StationSource.REMOTE.name)
                val entities = result.stations.mapIndexed { index, dto ->
                    val station = dto.toDomain(StationSource.REMOTE, index)
                    station.copy(isFavorite = station.id in previousFavoriteIds).toEntity()
                }
                dao.upsertAll(entities)
                CatalogSyncResult.Success(entities.size)
            }
        }
    }
}
