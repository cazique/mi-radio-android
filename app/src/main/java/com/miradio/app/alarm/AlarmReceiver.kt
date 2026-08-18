package com.miradio.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.miradio.app.R
import com.miradio.app.data.database.AppDatabase
import com.miradio.app.util.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val ALARM_CHANNEL_ID = "miradio_alarm"

/**
 * Se dispara cuando AlarmManager cumple la hora programada. En vez de
 * intentar abrir la actividad directamente (Android 10+ bloquea lanzar
 * actividades desde un proceso en segundo plano salvo excepciones muy
 * concretas), usa una notificación de "intent a pantalla completa"
 * (setFullScreenIntent): es el mecanismo oficial para alarmas/llamadas,
 * pensado exactamente para poder saltar por encima de la pantalla de
 * bloqueo sin que el usuario tenga que desbloquear el móvil antes.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId < 0) return
        DiagnosticsLog.log(context, "AlarmReceiver", "Alarma disparada: $alarmId")

        showFullScreenNotification(context, alarmId)

        // Reprogramar (si es repetitiva) implica leer Room, que es async;
        // goAsync() evita que el sistema mate el proceso antes de terminar.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).alarmDao()
                val alarm = dao.getById(alarmId)
                if (alarm != null && alarm.enabled && alarm.repeatDays.isNotBlank()) {
                    AlarmScheduler.schedule(context, alarm)
                }
            } catch (e: Exception) {
                DiagnosticsLog.logThrowable(context, "AlarmReceiver", "Fallo reprogramando la alarma $alarmId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showFullScreenNotification(context: Context, alarmId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarma despertador",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Avisa cuando suena una alarma despertador" }
            context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Es hora de despertarse")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(alarmId.toInt(), notification) }
            .onFailure { DiagnosticsLog.logThrowable(context, "AlarmReceiver", "No se pudo mostrar la notificación de alarma", it) }
    }
}
