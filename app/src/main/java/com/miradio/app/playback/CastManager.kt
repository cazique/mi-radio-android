package com.miradio.app.playback

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.miradio.app.data.repository.PreferencesRepository
import com.miradio.app.util.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Se encarga de la parte de Cast que no es puramente reproducción: recordar
 * el último dispositivo usado y permitir desconectar desde la UI. La
 * conmutación real de audio local/Cast la hace [RadioPlayer] a través de
 * [androidx.media3.cast.CastPlayer].
 */
class CastManager(
    private val context: Context,
    private val preferences: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(context) }
        .onFailure { DiagnosticsLog.logThrowable(context, "CastManager", "No se pudo obtener CastContext", it) }
        .getOrNull()

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = rememberDevice(session, "onSessionStarted")
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = rememberDevice(session, "onSessionResumed")
        override fun onSessionStarting(session: CastSession) =
            DiagnosticsLog.log(context, "CastManager", "onSessionStarting(${session.castDevice?.friendlyName})")
        override fun onSessionStartFailed(session: CastSession, error: Int) =
            DiagnosticsLog.logWarning(context, "CastManager", "onSessionStartFailed(error=$error)")
        override fun onSessionEnding(session: CastSession) =
            DiagnosticsLog.log(context, "CastManager", "onSessionEnding(${session.castDevice?.friendlyName})")
        override fun onSessionEnded(session: CastSession, error: Int) =
            DiagnosticsLog.log(context, "CastManager", "onSessionEnded(error=$error)")
        override fun onSessionResuming(session: CastSession, sessionId: String) =
            DiagnosticsLog.log(context, "CastManager", "onSessionResuming(${session.castDevice?.friendlyName})")
        override fun onSessionResumeFailed(session: CastSession, error: Int) =
            DiagnosticsLog.logWarning(context, "CastManager", "onSessionResumeFailed(error=$error)")
        override fun onSessionSuspended(session: CastSession, reason: Int) =
            DiagnosticsLog.logWarning(context, "CastManager", "onSessionSuspended(reason=$reason)")

        private fun rememberDevice(session: CastSession, from: String) {
            val device = session.castDevice ?: return
            DiagnosticsLog.log(context, "CastManager", "$from(${device.friendlyName})")
            scope.launch { preferences.setLastCastDevice(device.deviceId, device.friendlyName ?: device.deviceId) }
        }
    }

    fun start() {
        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
    }

    fun stop() {
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }

    fun disconnect() {
        DiagnosticsLog.log(context, "CastManager", "disconnect()")
        castContext?.sessionManager?.endCurrentSession(true)
        scope.launch { preferences.clearLastCastDevice() }
    }

    val isCastAvailable: Boolean get() = castContext != null
}
