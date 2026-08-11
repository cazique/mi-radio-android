package com.miradio.app.domain.model

/**
 * Fuente de la que procede una emisora. Permite distinguir en la UI las
 * emisoras incluidas de fábrica, las añadidas a mano por el usuario y las
 * que llegan del catálogo remoto (JSON), sin tener que tocar código para
 * ampliar el catálogo.
 */
enum class StationSource {
    SEED,
    LOCAL,
    REMOTE,
}

/**
 * Modelo de dominio de una emisora de radio. Es la estructura que pidió el
 * usuario, ampliada con lo mínimo necesario (disponibilidad, origen y orden)
 * para que favoritos, búsqueda y el catálogo remoto funcionen.
 */
data class RadioStation(
    val id: String,
    val name: String,
    val city: String,
    val streamUrl: String,
    val logoUrl: String?,
    val description: String?,
    val category: String?,
    val isFavorite: Boolean,
    val isAvailable: Boolean = true,
    val source: StationSource = StationSource.LOCAL,
    val sortOrder: Int = 0,
    /** Comunidad autónoma (o "Nacional"), usada para agrupar el catálogo grande. */
    val region: String? = null,
)
