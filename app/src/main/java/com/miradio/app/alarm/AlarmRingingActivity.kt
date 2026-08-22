package com.miradio.app.alarm

import android.app.KeyguardManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.miradio.app.data.database.AppDatabase
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource
import com.miradio.app.playback.PlaybackController
import com.miradio.app.playback.RadioPlayer
import com.miradio.app.ui.theme.MiRadioTheme
import com.miradio.app.util.DiagnosticsLog
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FALLBACK_TIMEOUT_MS = 20_000L
private const val SNOOZE_MINUTES = 10

/**
 * Pantalla de alarma sonando: a pantalla completa, por encima de la
 * pantalla de bloqueo (setShowWhenLocked/setTurnScreenOn, con el
 * equivalente en banderas de ventana para versiones antiguas de Android).
 * Arranca la emisora de la alarma con subida de volumen gradual si está
 * activada; si el stream no llega a sonar en 20 s (sin red, emisora
 * caída...), recurre al tono de alarma del propio sistema para no dejar
 * a alguien sin despertar por un fallo de red.
 */
class AlarmRingingActivity : ComponentActivity() {

    private var fadeInJob: Job? = null
    private var fallbackJob: Job? = null
    private var fallbackRingtone: Ringtone? = null
    private var radioPlayer: RadioPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        DiagnosticsLog.log(this, "AlarmRingingActivity", "onCreate alarmId=$alarmId")

        setContent {
            MiRadioTheme {
                AlarmRingingScreen(
                    onDismiss = { stopAlarmAndFinish() },
                    onSnooze = { snoozeAndFinish(alarmId) },
                )
            }
        }

        if (alarmId >= 0) startAlarmPlayback(alarmId) else startFallbackRingtone()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService<KeyguardManager>()?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
    }

    private fun startAlarmPlayback(alarmId: Long) {
        lifecycleScope.launch {
            val alarm = withContext(Dispatchers.IO) { AppDatabase.getInstance(this@AlarmRingingActivity).alarmDao().getById(alarmId) }
            if (alarm == null) {
                DiagnosticsLog.logWarning(this@AlarmRingingActivity, "AlarmRingingActivity", "Alarma $alarmId no encontrada")
                startFallbackRingtone()
                return@launch
            }
            runCatching {
                PlaybackController.ensureServiceStarted(this@AlarmRingingActivity)
                val player = PlaybackController.awaitPlayer()
                radioPlayer = player
                player.setVolume(if (alarm.volumeIncreasing) alarm.startVolume.coerceIn(0f, 1f) else 1f)
                player.playStation(
                    RadioStation(
                        id = alarm.stationId,
                        name = alarm.stationName,
                        city = "Alarma",
                        streamUrl = alarm.stationStreamUrl,
                        logoUrl = alarm.stationLogoUrl,
                        description = null,
                        category = "Alarma",
                        isFavorite = false,
                        source = StationSource.LOCAL,
                    ),
                )

                if (alarm.volumeIncreasing) {
                    fadeInJob = lifecycleScope.launch {
                        val steps = 20
                        val stepDelayMs = (alarm.fadeInSeconds * 1000L / steps).coerceAtLeast(200L)
                        for (i in 1..steps) {
                            val volume = alarm.startVolume + (1f - alarm.startVolume) * (i / steps.toFloat())
                            player.setVolume(volume.coerceIn(0f, 1f))
                            delay(stepDelayMs)
                        }
                    }
                }

                // Si no llega a sonar en el margen dado, se recurre al tono
                // del sistema: mejor eso que no despertar a nadie por un
                // fallo de red o una emisora caída justo esa mañana.
                fallbackJob = lifecycleScope.launch {
                    delay(FALLBACK_TIMEOUT_MS)
                    if (player.uiState.value.status != PlaybackStatus.PLAYING) {
                        DiagnosticsLog.logWarning(
                            this@AlarmRingingActivity,
                            "AlarmRingingActivity",
                            "La emisora no arrancó en ${FALLBACK_TIMEOUT_MS / 1000}s, usando tono de reserva",
                        )
                        startFallbackRingtone()
                    }
                }
            }.onFailure {
                DiagnosticsLog.logThrowable(this@AlarmRingingActivity, "AlarmRingingActivity", "Fallo arrancando la emisora de la alarma", it)
                startFallbackRingtone()
            }
        }
    }

    private fun startFallbackRingtone() {
        if (fallbackRingtone?.isPlaying == true) return
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getValidRingtoneUri(this)
            fallbackRingtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                }
                play()
            }
        }.onFailure { DiagnosticsLog.logThrowable(this, "AlarmRingingActivity", "No se pudo reproducir el tono de reserva", it) }
    }

    private fun stopAlarmAndFinish() {
        fadeInJob?.cancel()
        fallbackJob?.cancel()
        fallbackRingtone?.stop()
        radioPlayer?.stop()
        finishAndRemoveTask()
    }

    private fun snoozeAndFinish(alarmId: Long) {
        fadeInJob?.cancel()
        fallbackJob?.cancel()
        fallbackRingtone?.stop()
        radioPlayer?.stop()
        if (alarmId < 0) {
            finishAndRemoveTask()
            return
        }
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@AlarmRingingActivity).alarmDao()
            val alarm = withContext(Dispatchers.IO) { dao.getById(alarmId) }
            if (alarm != null) {
                val snoozeAt = Calendar.getInstance().apply { add(Calendar.MINUTE, SNOOZE_MINUTES) }
                val snoozedAlarm = alarm.copy(
                    hour = snoozeAt.get(Calendar.HOUR_OF_DAY),
                    minute = snoozeAt.get(Calendar.MINUTE),
                    repeatDays = "",
                )
                // Se persiste también en la base de datos (no solo en
                // AlarmManager): sin esto, un reinicio durante los minutos de
                // posposición hacía que BootReceiver la sustituyera en
                // silencio por la próxima hora "normal" de la alarma.
                withContext(Dispatchers.IO) { dao.setSnoozedUntil(alarmId, snoozeAt.timeInMillis) }
                AlarmScheduler.schedule(this@AlarmRingingActivity, snoozedAlarm, triggerAtMillis = snoozeAt.timeInMillis)
                DiagnosticsLog.log(this@AlarmRingingActivity, "AlarmRingingActivity", "Alarma $alarmId pospuesta $SNOOZE_MINUTES min")
            }
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        fadeInJob?.cancel()
        fallbackJob?.cancel()
        fallbackRingtone?.stop()
        super.onDestroy()
    }
}

@Composable
private fun AlarmRingingScreen(onDismiss: () -> Unit, onSnooze: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "¡Es hora de despertarse!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Text("Posponer $SNOOZE_MINUTES min", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Apagar", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
