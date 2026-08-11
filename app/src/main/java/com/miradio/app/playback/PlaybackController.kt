package com.miradio.app.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Punto de entrada que usan los ViewModels para hablar con [PlaybackService]
 * sin tener que preocuparse de si el servicio ya está arrancado.
 */
object PlaybackController {
    fun ensureServiceStarted(context: Context) {
        val intent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    suspend fun awaitPlayer(): RadioPlayer =
        PlaybackServiceConnector.player.filterNotNull().first()
}
