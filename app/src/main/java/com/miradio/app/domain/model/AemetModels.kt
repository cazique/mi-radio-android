package com.miradio.app.domain.model

import kotlinx.serialization.Serializable

/** Municipio del maestro de AEMET (id con formato "id28079", nombre tal cual
 *  lo da AEMET, p. ej. "Madrid, Madrid"). */
@Serializable
data class AemetMunicipio(val id: String, val nombre: String)

/** Lectura de AEMET para la hora en curso (o la más cercana disponible),
 *  para comparar con Open-Meteo. AEMET no da un valor "instantáneo" como
 *  hace un sensor: es la predicción horaria para la hora en la que estamos,
 *  de ahí "hourLabel" para dejar claro a qué hora corresponde el dato. */
data class AemetSnapshot(
    val tempC: Double?,
    val skyDescription: String?,
    val rainProbabilityPercent: Int?,
    val hourLabel: String,
)
