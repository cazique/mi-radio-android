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
     *
     * Como el id es la clave primaria en Room, una emisora remota con el
     * mismo id que una de fábrica o añadida a mano podría "adoptar" esa fila
     * (y luego borrarla si desaparece del JSON). Para evitarlo, cualquier id
     * remoto que ya exista como SEED o LOCAL se ignora silenciosamente: esas
     * fuentes siempre tienen prioridad sobre el catálogo remoto.
     */
    suspend fun syncRemoteCatalog(url: String): CatalogSyncResult = withContext(Dispatchers.IO) {
        when (val result = remoteService.fetchCatalog(url)) {
            is RemoteCatalogResult.Failure -> CatalogSyncResult.Failure(result.reason)
            is RemoteCatalogResult.Success -> {
                // Un catálogo vacío casi nunca es intencionado (JSON roto, CDN caída,
                // fichero sustituido por error...) y borraría todo el catálogo remoto
                // que ya tenías. Se trata como fallo en vez de aplicarlo.
                if (result.stations.isEmpty()) {
                    return@withContext CatalogSyncResult.Failure(
                        "El catálogo remoto está vacío; no se ha aplicado ningún cambio.",
                    )
                }
                // Campos imprescindibles vacíos o URLs que no son http(s) indican un
                // JSON mal generado; mejor rechazar todo el catálogo que guardar
                // emisoras rotas.
                val invalidStation = result.stations.firstOrNull {
                    it.id.isBlank() || it.name.isBlank() || it.streamUrl.isBlank() ||
                        !(it.streamUrl.startsWith("http://") || it.streamUrl.startsWith("https://"))
                }
                if (invalidStation != null) {
                    return@withContext CatalogSyncResult.Failure(
                        "El catálogo remoto tiene una emisora inválida (id \"${invalidStation.id}\"); no se ha aplicado ningún cambio.",
                    )
                }
                // Con REPLACE como estrategia de conflicto de Room, dos emisoras
                // remotas con el mismo id en el mismo JSON harían que una se
                // sobrescribiera silenciosamente a la otra.
                val duplicateIds = result.stations.groupBy { it.id }.filterValues { it.size > 1 }.keys
                if (duplicateIds.isNotEmpty()) {
                    return@withContext CatalogSyncResult.Failure(
                        "El catálogo remoto tiene IDs duplicados (${duplicateIds.take(3).joinToString()}); no se ha aplicado ningún cambio.",
                    )
                }
                // El campo "favorita" de una emisora es una decisión del usuario, no
                // del catálogo remoto: si el JSON pudiera reimponerla en cada
                // sincronización, desmarcar una emisora "favorita por defecto" (p. ej.
                // COPE Madrid/León) nunca sería permanente. Por eso el catálogo remoto
                // solo decide el valor inicial la primera vez que ve una emisora nueva;
                // a partir de ahí se respeta siempre lo que haya elegido el usuario.
                val previousRemoteIds = dao.idsBySource(StationSource.REMOTE.name).toSet()
                val previousFavoriteIds = dao.favoriteIdsBySource(StationSource.REMOTE.name).toSet()
                val protectedIds = dao.idsExcludingSource(StationSource.REMOTE.name).toSet()
                val entities = result.stations
                    .filter { it.id !in protectedIds }
                    .mapIndexed { index, dto ->
                        val station = dto.toDomain(StationSource.REMOTE, index)
                        val isFavorite = if (station.id in previousRemoteIds) {
                            station.id in previousFavoriteIds
                        } else {
                            dto.isFavorite
                        }
                        station.copy(isFavorite = isFavorite).toEntity()
                    }
                // Borrar+insertar en una sola transacción: si el proceso muere a
                // mitad, la app se queda con el catálogo anterior completo, nunca
                // a medias.
                dao.replaceSource(StationSource.REMOTE.name, entities)
                CatalogSyncResult.Success(entities.size)
            }
        }
    }
}
