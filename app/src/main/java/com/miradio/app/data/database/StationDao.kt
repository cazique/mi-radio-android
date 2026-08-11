package com.miradio.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {

    @Query("SELECT * FROM stations ORDER BY isFavorite DESC, sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: StationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<StationEntity>)

    @Update
    suspend fun update(station: StationEntity)

    @Delete
    suspend fun delete(station: StationEntity)

    @Query("DELETE FROM stations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE stations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM stations WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT id FROM stations WHERE source = :source AND isFavorite = 1")
    suspend fun favoriteIdsBySource(source: String): List<String>

    /** IDs que NO pertenecen a [source]. Se usa para que el catálogo remoto nunca
     *  pueda pisar (por id duplicado) una emisora de fábrica o añadida a mano. */
    @Query("SELECT id FROM stations WHERE source != :source")
    suspend fun idsExcludingSource(source: String): List<String>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int
}
