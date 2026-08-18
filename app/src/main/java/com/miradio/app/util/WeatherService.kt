package com.miradio.app.util

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.miradio.app.domain.model.DailyForecast
import com.miradio.app.domain.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed class WeatherResult {
    data class Success(val weather: WeatherInfo) : WeatherResult()
    data class Failure(val reason: String) : WeatherResult()
}

/**
 * Tiempo actual y previsión de los próximos días para una ubicación, vía
 * Open-Meteo: es gratis, no exige clave de API ni registro (a diferencia de
 * OpenWeatherMap o AccuWeather), justo lo que hace falta para una app suelta
 * sin backend propio detrás.
 */
class WeatherService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetchWeather(context: Context, location: Location): WeatherResult = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}&longitude=${location.longitude}" +
            "&current=temperature_2m,weather_code,is_day" +
            "&hourly=precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&forecast_days=6&timezone=auto"
        val request = Request.Builder().url(url).get().build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext WeatherResult.Failure("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext WeatherResult.Failure("Respuesta vacía")
                val dto = json.decodeFromString(OpenMeteoResponse.serializer(), body)
                val current = dto.current ?: return@withContext WeatherResult.Failure("Sin datos actuales")
                val daily = dto.daily
                val forecasts = if (daily != null) {
                    daily.time.indices.map { i ->
                        DailyForecast(
                            date = daily.time[i],
                            weatherCode = daily.weatherCode.getOrElse(i) { 0 },
                            maxTempC = daily.tempMax.getOrElse(i) { current.temperature },
                            minTempC = daily.tempMin.getOrElse(i) { current.temperature },
                        )
                    }
                } else {
                    emptyList()
                }
                // El índice "hora actual" en el array horario: con timezone=auto,
                // Open-Meteo empieza el array en la medianoche local de hoy, así
                // que la hora del sistema es directamente el índice.
                val currentHourIndex = java.text.SimpleDateFormat("H", Locale.getDefault())
                    .format(java.util.Date()).toIntOrNull()
                val rainProbability = currentHourIndex?.let { dto.hourly?.precipitationProbability?.getOrNull(it) }
                val placeName = reverseGeocode(context, location)
                WeatherResult.Success(
                    WeatherInfo(
                        locationName = placeName,
                        currentTempC = current.temperature,
                        currentWeatherCode = current.weatherCode,
                        isDay = current.isDay == 1,
                        daily = forecasts,
                        currentRainProbabilityPercent = rainProbability,
                    ),
                )
            }
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "WeatherService", "fetchWeather falló", e)
            WeatherResult.Failure(e.message ?: "Error de conexión")
        }
    }

    /** Nombre de la localidad a partir de las coordenadas usando el propio
     *  servicio de geocodificación de Android (no consume ninguna cuota
     *  aparte, y no hace falta pedir otro permiso extra). Si el dispositivo
     *  no trae ningún servicio de geocodificación instalado (poco frecuente,
     *  algunas ROM sin Google Play Services) simplemente no se muestra
     *  nombre de localidad, sin que eso impida ver el tiempo. */
    private suspend fun reverseGeocode(context: Context, location: Location): String? {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (!Geocoder.isPresent()) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                        cont.resume(addresses.firstOrNull()?.locality ?: addresses.firstOrNull()?.subAdminArea)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
            }
        }.getOrNull()
    }

    @Serializable
    private data class OpenMeteoResponse(
        val current: CurrentBlock? = null,
        val daily: DailyBlock? = null,
        val hourly: HourlyBlock? = null,
    )

    @Serializable
    private data class HourlyBlock(
        @SerialName("precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
    )

    @Serializable
    private data class CurrentBlock(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int,
    )

    @Serializable
    private data class DailyBlock(
        val time: List<String>,
        @SerialName("weather_code") val weatherCode: List<Int>,
        @SerialName("temperature_2m_max") val tempMax: List<Double>,
        @SerialName("temperature_2m_min") val tempMin: List<Double>,
    )
}

/** Traduce un código de tiempo WMO (el estándar que usa Open-Meteo) a una
 *  descripción corta en español. Tabla oficial:
 *  https://open-meteo.com/en/docs#weathervariables */
fun weatherDescription(code: Int): String = when (code) {
    0 -> "Despejado"
    1 -> "Mayormente despejado"
    2 -> "Parcialmente nublado"
    3 -> "Cubierto"
    45, 48 -> "Niebla"
    51, 53, 55 -> "Llovizna"
    56, 57 -> "Llovizna helada"
    61, 63, 65 -> "Lluvia"
    66, 67 -> "Lluvia helada"
    71, 73, 75 -> "Nieve"
    77 -> "Granizo fino"
    80, 81, 82 -> "Chubascos"
    85, 86 -> "Chubascos de nieve"
    95 -> "Tormenta"
    96, 99 -> "Tormenta con granizo"
    else -> "Sin datos"
}
