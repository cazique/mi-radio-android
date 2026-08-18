package com.miradio.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Obtiene una localización aproximada sin depender de Google Play Services
 * (que en esta app es opcional: Cast ya se apaga solo si no está
 * disponible). Usa el LocationManager del propio Android, que funciona en
 * cualquier dispositivo con GPS o localización por red.
 */
object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Última localización conocida (instantánea, puede ser nula o algo
     * vieja) si hay alguna; si no, pide una localización nueva con un breve
     * margen de espera. Nunca lanza: cualquier fallo o falta de proveedor
     * disponible devuelve null.
     */
    suspend fun getLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val locationManager = context.getSystemService<LocationManager>() ?: return null

        val cached = runCatching {
            locationManager.getProviders(true)
                .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
        if (cached != null) return cached

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return requestFreshLocation(context, locationManager, provider)
    }

    private suspend fun requestFreshLocation(
        context: Context,
        locationManager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { cont ->
        if (!hasPermission(context)) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val executor = Executor { it.run() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            locationManager.getCurrentLocation(provider, signal, executor) { location -> cont.resume(location) }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    cont.resume(location)
                }
            }
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, listener, context.mainLooper)
        }
    }
}
