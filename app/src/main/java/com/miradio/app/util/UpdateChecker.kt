package com.miradio.app.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Antes solo se comprobaba si había una actualización cuando el usuario
 * entraba en Ajustes y tocaba "Buscar actualizaciones" a mano. Esto
 * comprueba solo, en segundo plano mientras la app está abierta, y expone
 * el resultado para que cualquier pantalla pueda avisar sin que haga falta
 * tocar nada.
 */
object UpdateChecker {
    private const val CHECK_INTERVAL_MS = 30 * 60_000L

    private val _updateAvailable = MutableStateFlow<UpdateCheckResult.UpdateAvailable?>(null)
    val updateAvailable: StateFlow<UpdateCheckResult.UpdateAvailable?> = _updateAvailable

    private var started = false

    /** Se llama una sola vez, desde RadioApp.onCreate. Repetir la llamada no
     *  arranca un segundo bucle (started evita duplicar comprobaciones). */
    fun start(context: Context, scope: CoroutineScope) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        scope.launch {
            while (true) {
                runCatching { AppUpdater.checkForUpdate(appContext) }
                    .getOrNull()
                    ?.let { result ->
                        if (result is UpdateCheckResult.UpdateAvailable) {
                            DiagnosticsLog.log(appContext, "UpdateChecker", "Actualización disponible: ${result.versionName}")
                            _updateAvailable.value = result
                        }
                    }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /** Al descartar el aviso (o tras instalar) no se vuelve a mostrar hasta
     *  la siguiente comprobación periódica, para no ser pesados. */
    fun dismiss() {
        _updateAvailable.value = null
    }
}
