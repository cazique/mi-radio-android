package com.miradio.app.util

import android.content.Context
import com.miradio.app.domain.model.AemetMunicipio
import com.miradio.app.domain.model.AemetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val MUNICIPIOS_CACHE_FILE = "aemet_municipios.json"
private val MUNICIPIOS_CACHE_MAX_AGE_MS = TimeUnit.DAYS.toMillis(30)

/**
 * Cliente para AEMET OpenData, con la clave puesta a mano por cada usuario
 * en Ajustes (nunca va en el APK: ver la conversación sobre por qué no se
 * puede embeber una clave personal en una app que se distribuye en
 * abierto). Cada endpoint de AEMET responde en dos pasos: una petición
 * inicial que solo da una URL ("datos"), y una segunda petición a esa URL
 * con el contenido real.
 *
 * OJO: el formato exacto de la respuesta de AEMET no se ha podido probar
 * contra una clave real desde aquí (entorno sin acceso a Internet salvo a
 * través de CI). El parseo es todo lo defensivo posible (campo por campo,
 * con null en vez de excepción si algo no encaja) para que, si AEMET
 * cambia algo o el formato no es exactamente el esperado, como mucho no
 * aparezca el dato de AEMET — nunca debe romper el resto de la app.
 */
class AemetService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetchMunicipios(context: Context, apiKey: String): Result<List<AemetMunicipio>> =
        withContext(Dispatchers.IO) {
            readCachedMunicipios(context)?.let { return@withContext Result.success(it) }
            try {
                val body = fetchAemetJson(apiKey, "https://opendata.aemet.es/opendata/api/maestro/municipios")
                    ?: return@withContext Result.failure(Exception("AEMET no ha devuelto el maestro de municipios"))
                val municipios = json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val nombre = obj["nombre"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    AemetMunicipio(id = id, nombre = nombre)
                }
                if (municipios.isEmpty()) return@withContext Result.failure(Exception("Maestro de municipios vacío"))
                cacheMunicipios(context, municipios)
                Result.success(municipios)
            } catch (e: Exception) {
                DiagnosticsLog.logThrowable(context, "AemetService", "fetchMunicipios falló", e)
                Result.failure(e)
            }
        }

    /** Temperatura/cielo/probabilidad de lluvia para la hora en curso (o la
     *  más cercana en la previsión horaria), a partir del municipio elegido
     *  en Ajustes. */
    suspend fun fetchCurrentConditions(context: Context, apiKey: String, municipioId: String): Result<AemetSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val cleanId = municipioId.removePrefix("id")
                val body = fetchAemetJson(
                    apiKey,
                    "https://opendata.aemet.es/opendata/api/prediccion/especifica/municipio/horaria/$cleanId",
                ) ?: return@withContext Result.failure(Exception("AEMET no ha devuelto previsión horaria"))

                val today = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject
                    ?.get("prediccion")?.jsonObject
                    ?.get("dia")?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@withContext Result.failure(Exception("Respuesta de AEMET sin previsión para hoy"))

                val currentHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date())

                fun periodEntry(arrayKey: String) = today[arrayKey]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.let { entries ->
                        entries.firstOrNull { it["periodo"]?.jsonPrimitive?.contentOrNull == currentHour } ?: entries.firstOrNull()
                    }

                val temp = periodEntry("temperatura")?.get("value")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val sky = periodEntry("estadoCielo")?.get("descripcion")?.jsonPrimitive?.contentOrNull
                val rain = periodEntry("probPrecipitacion")?.get("value")?.jsonPrimitive?.intOrNull

                Result.success(
                    AemetSnapshot(
                        tempC = temp,
                        skyDescription = sky,
                        rainProbabilityPercent = rain,
                        hourLabel = "$currentHour:00",
                    ),
                )
            } catch (e: Exception) {
                DiagnosticsLog.logThrowable(context, "AemetService", "fetchCurrentConditions falló", e)
                Result.failure(e)
            }
        }

    /** GET inicial (devuelve la URL de "datos") + GET a esa URL con el
     *  contenido real. Null si cualquiera de los dos pasos falla. */
    private fun fetchAemetJson(apiKey: String, url: String): String? {
        val request = Request.Builder().url("$url?api_key=$apiKey").get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val envelopeBody = response.body?.string() ?: return null
            val datosUrl = json.parseToJsonElement(envelopeBody).jsonObject["datos"]?.jsonPrimitive?.contentOrNull
                ?: return null
            val datosRequest = Request.Builder().url(datosUrl).get().build()
            httpClient.newCall(datosRequest).execute().use { datosResponse ->
                if (!datosResponse.isSuccessful) return null
                return datosResponse.body?.string()
            }
        }
    }

    @Serializable
    private data class MunicipiosCache(val cachedAtMillis: Long, val municipios: List<AemetMunicipio>)

    private fun readCachedMunicipios(context: Context): List<AemetMunicipio>? = runCatching {
        val file = File(context.filesDir, MUNICIPIOS_CACHE_FILE)
        if (!file.exists()) return null
        val cache = json.decodeFromString<MunicipiosCache>(file.readText())
        if (System.currentTimeMillis() - cache.cachedAtMillis > MUNICIPIOS_CACHE_MAX_AGE_MS) return null
        cache.municipios.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun cacheMunicipios(context: Context, municipios: List<AemetMunicipio>) {
        runCatching {
            val cache = MunicipiosCache(cachedAtMillis = System.currentTimeMillis(), municipios = municipios)
            File(context.filesDir, MUNICIPIOS_CACHE_FILE).writeText(json.encodeToString(cache))
        }
    }
}
