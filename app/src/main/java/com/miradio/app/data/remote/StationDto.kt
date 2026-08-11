package com.miradio.app.data.remote

import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource
import kotlinx.serialization.Serializable

/**
 * Forma del JSON del catálogo de emisoras, tanto el que viene incluido en
 * `assets/stations_seed.json` como el que se descarga de una URL remota
 * configurable desde Ajustes. Mantener este contrato estable es lo que
 * permite añadir emisoras (COPE León, SER Madrid, Onda Cero, RNE...) sin
 * publicar una nueva versión de la app: basta con editar el JSON remoto.
 */
@Serializable
data class StationCatalogDto(
    val stations: List<StationDto> = emptyList(),
)

@Serializable
data class StationDto(
    val id: String,
    val name: String,
    val city: String = "",
    val streamUrl: String,
    val logoUrl: String? = null,
    val description: String? = null,
    val category: String? = null,
    val isAvailable: Boolean = true,
)

fun StationDto.toDomain(source: StationSource, sortOrder: Int): RadioStation = RadioStation(
    id = id,
    name = name,
    city = city,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    description = description,
    category = category,
    isFavorite = false,
    isAvailable = isAvailable,
    source = source,
    sortOrder = sortOrder,
)
