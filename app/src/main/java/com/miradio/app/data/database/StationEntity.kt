package com.miradio.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val streamUrl: String,
    val logoUrl: String?,
    val description: String?,
    val category: String?,
    val isFavorite: Boolean,
    val isAvailable: Boolean,
    val source: String,
    val sortOrder: Int,
    val region: String? = null,
)

fun StationEntity.toDomain(): RadioStation = RadioStation(
    id = id,
    name = name,
    city = city,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    description = description,
    category = category,
    isFavorite = isFavorite,
    isAvailable = isAvailable,
    source = runCatching { StationSource.valueOf(source) }.getOrDefault(StationSource.LOCAL),
    sortOrder = sortOrder,
    region = region,
)

fun RadioStation.toEntity(): StationEntity = StationEntity(
    id = id,
    name = name,
    city = city,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    description = description,
    category = category,
    isFavorite = isFavorite,
    isAvailable = isAvailable,
    source = source.name,
    sortOrder = sortOrder,
    region = region,
)
