package com.miradio.app.data.repository

import com.miradio.app.data.database.StationDao
import com.miradio.app.data.database.StationEntity
import com.miradio.app.data.remote.RemoteStationsService
import com.miradio.app.domain.model.StationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Prueba StationRepository.syncRemoteCatalog(): la lógica más delicada de
 * toda la app (protección de emisoras SEED/LOCAL frente al catálogo remoto,
 * favoritos que el usuario decide y que el catálogo remoto no puede
 * revertir, y rechazo de catálogos con datos rotos). Usa un
 * [FakeStationDao] en memoria (StationDao es una interfaz, no hace falta
 * Room) y un MockWebServer real para servir el JSON remoto sin mockear
 * OkHttpClient a mano.
 */
class StationRepositorySyncTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeStationDao
    private lateinit var repository: StationRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dao = FakeStationDao()
        repository = StationRepository(
            context = mock(android.content.Context::class.java),
            dao = dao,
            remoteService = RemoteStationsService(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `catalogo vacio se rechaza y no toca lo que ya habia`() = runTest {
        dao.seed(entity("existing", source = StationSource.REMOTE))
        enqueueCatalog("""{"stations": []}""")

        val result = repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        assertTrue(result is CatalogSyncResult.Failure)
        assertEquals(1, dao.all().size) // no se ha borrado nada
    }

    @Test
    fun `emisora con id o url en blanco rechaza todo el catalogo`() = runTest {
        enqueueCatalog(
            """{"stations": [
                {"id": "ok1", "name": "OK", "streamUrl": "https://x.com/a.mp3"},
                {"id": "", "name": "Rota", "streamUrl": "https://x.com/b.mp3"}
            ]}""",
        )

        val result = repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        assertTrue(result is CatalogSyncResult.Failure)
        assertEquals(0, dao.all().size)
    }

    @Test
    fun `url que no es http o https rechaza todo el catalogo`() = runTest {
        enqueueCatalog(
            """{"stations": [
                {"id": "ok1", "name": "OK", "streamUrl": "ftp://x.com/a.mp3"}
            ]}""",
        )

        val result = repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        assertTrue(result is CatalogSyncResult.Failure)
    }

    @Test
    fun `ids duplicados en el mismo catalogo se rechazan`() = runTest {
        enqueueCatalog(
            """{"stations": [
                {"id": "dup", "name": "Uno", "streamUrl": "https://x.com/a.mp3"},
                {"id": "dup", "name": "Dos", "streamUrl": "https://x.com/b.mp3"}
            ]}""",
        )

        val result = repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        assertTrue(result is CatalogSyncResult.Failure)
        assertEquals(0, dao.all().size)
    }

    @Test
    fun `catalogo valido se aplica y marca la fuente REMOTE`() = runTest {
        enqueueCatalog(
            """{"stations": [
                {"id": "cope_madrid", "name": "COPE Madrid", "streamUrl": "https://x.com/a.mp3", "isFavorite": true}
            ]}""",
        )

        val result = repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        assertEquals(CatalogSyncResult.Success(1), result)
        val saved = dao.all().single()
        assertEquals(StationSource.REMOTE.name, saved.source)
        assertTrue(saved.isFavorite)
    }

    @Test
    fun `una emisora SEED con el mismo id que trae el remoto no se toca`() = runTest {
        dao.seed(entity("cope_madrid", name = "COPE Madrid (de fábrica)", source = StationSource.SEED, isFavorite = true))
        enqueueCatalog(
            """{"stations": [
                {"id": "cope_madrid", "name": "COPE Madrid (remoto)", "streamUrl": "https://x.com/a.mp3", "isFavorite": false}
            ]}""",
        )

        repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        val saved = dao.all().single { it.id == "cope_madrid" }
        assertEquals("COPE Madrid (de fábrica)", saved.name) // no lo ha pisado el remoto
        assertEquals(StationSource.SEED.name, saved.source)
        assertTrue(saved.isFavorite) // tampoco le ha quitado el favorito
    }

    @Test
    fun `una emisora LOCAL con el mismo id que trae el remoto no se toca`() = runTest {
        dao.seed(entity("mi_emisora", name = "Mi emisora casera", source = StationSource.LOCAL))
        enqueueCatalog(
            """{"stations": [
                {"id": "mi_emisora", "name": "Otra cosa", "streamUrl": "https://x.com/a.mp3"}
            ]}""",
        )

        repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        val saved = dao.all().single { it.id == "mi_emisora" }
        assertEquals("Mi emisora casera", saved.name)
        assertEquals(StationSource.LOCAL.name, saved.source)
    }

    @Test
    fun `si el usuario desmarca un favorito remoto, la siguiente sincronizacion no lo revierte`() = runTest {
        // Primera sincronización: llega como favorita por defecto.
        enqueueCatalog(
            """{"stations": [
                {"id": "cope_madrid", "name": "COPE Madrid", "streamUrl": "https://x.com/a.mp3", "isFavorite": true}
            ]}""",
        )
        repository.syncRemoteCatalog(server.url("/catalog.json").toString())
        assertTrue(dao.all().single().isFavorite)

        // El usuario la desmarca a mano.
        dao.setFavorite("cope_madrid", false)

        // Segunda sincronización: el JSON remoto sigue diciendo isFavorite=true,
        // pero como ya era conocida, debe respetarse la decisión del usuario.
        enqueueCatalog(
            """{"stations": [
                {"id": "cope_madrid", "name": "COPE Madrid", "streamUrl": "https://x.com/a.mp3", "isFavorite": true}
            ]}""",
        )
        repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        assertEquals(false, dao.all().single().isFavorite)
    }

    @Test
    fun `una emisora remota que desaparece del JSON se retira en la siguiente sincronizacion`() = runTest {
        enqueueCatalog(
            """{"stations": [
                {"id": "a", "name": "A", "streamUrl": "https://x.com/a.mp3"},
                {"id": "b", "name": "B", "streamUrl": "https://x.com/b.mp3"}
            ]}""",
        )
        repository.syncRemoteCatalog(server.url("/catalog.json").toString())
        assertEquals(2, dao.all().size)

        enqueueCatalog(
            """{"stations": [
                {"id": "a", "name": "A", "streamUrl": "https://x.com/a.mp3"}
            ]}""",
        )
        repository.syncRemoteCatalog(server.url("/catalog.json").toString())

        val remaining = dao.all()
        assertEquals(1, remaining.size)
        assertEquals("a", remaining.single().id)
    }

    private fun enqueueCatalog(json: String) {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(json))
    }

    private fun entity(
        id: String,
        name: String = id,
        source: StationSource = StationSource.REMOTE,
        isFavorite: Boolean = false,
    ) = StationEntity(
        id = id,
        name = name,
        city = "",
        streamUrl = "https://x.com/$id.mp3",
        logoUrl = null,
        description = null,
        category = null,
        isFavorite = isFavorite,
        isAvailable = true,
        source = source.name,
        sortOrder = 0,
    )
}

/**
 * Fake en memoria de StationDao (interfaz de Room): permite probar la
 * lógica de sincronización sin arrancar una base de datos real.
 */
private class FakeStationDao : StationDao {
    private val stations = LinkedHashMap<String, StationEntity>()
    private val flow = MutableStateFlow<List<StationEntity>>(emptyList())

    fun seed(vararg entities: StationEntity) {
        entities.forEach { stations[it.id] = it }
        publish()
    }

    fun all(): List<StationEntity> = stations.values.toList()

    private fun publish() {
        flow.value = stations.values.toList()
    }

    override fun observeAll(): StateFlow<List<StationEntity>> = flow

    override suspend fun getById(id: String): StationEntity? = stations[id]

    override suspend fun upsert(station: StationEntity) {
        stations[station.id] = station
        publish()
    }

    override suspend fun upsertAll(stationsToInsert: List<StationEntity>) {
        stationsToInsert.forEach { stations[it.id] = it }
        publish()
    }

    override suspend fun update(station: StationEntity) {
        stations[station.id] = station
        publish()
    }

    override suspend fun delete(station: StationEntity) {
        stations.remove(station.id)
        publish()
    }

    override suspend fun deleteById(id: String) {
        stations.remove(id)
        publish()
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        stations[id]?.let { stations[id] = it.copy(isFavorite = isFavorite) }
        publish()
    }

    override suspend fun deleteBySource(source: String) {
        stations.values.filter { it.source == source }.forEach { stations.remove(it.id) }
        publish()
    }

    override suspend fun favoriteIdsBySource(source: String): List<String> =
        stations.values.filter { it.source == source && it.isFavorite }.map { it.id }

    override suspend fun idsBySource(source: String): List<String> =
        stations.values.filter { it.source == source }.map { it.id }

    override suspend fun idsExcludingSource(source: String): List<String> =
        stations.values.filter { it.source != source }.map { it.id }

    override suspend fun replaceSource(source: String, newEntities: List<StationEntity>) {
        deleteBySource(source)
        upsertAll(newEntities)
    }

    override suspend fun count(): Int = stations.size
}
