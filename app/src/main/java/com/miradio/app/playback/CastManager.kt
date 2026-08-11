package com.miradio.app.playback

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.miradio.app.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Se encarga de la parte de Cast que no es puramente reproducción: recordar
 * el último dispositivo usado y permitir desconectar desde la UI. La
 * conmutación real de audio local/Cast la hace [RadioPlayer] a través de
 * [androidx.media3.cast.CastPlayer].
 */
class CastManager(
    context: Context,
    private val preferences: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = rememberDevice(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = rememberDevice(session)
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit

        private fun rememberDevice(session: CastSession) {
            val device = session.castDevice ?: return
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
        castContext?.sessionManager?.endCurrentSession(true)
        scope.launch { preferences.clearLastCastDevice() }
    }

    val isCastAvailable: Boolean get() = castContext != null
}
