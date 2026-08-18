package com.miradio.app.domain.model

/** Previsión de un día: fecha en formato ISO ("2026-08-19"), código de
 *  tiempo WMO (ver [com.miradio.app.util.weatherDescription]) y máxima/mínima
 *  en grados Celsius. */
data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val maxTempC: Double,
    val minTempC: Double,
)

data class WeatherInfo(
    val locationName: String?,
    val currentTempC: Double,
    val currentWeatherCode: Int,
    val isDay: Boolean,
    /** Del día de hoy en adelante, orden cronológico (normalmente 6 días). */
    val daily: List<DailyForecast>,
)
