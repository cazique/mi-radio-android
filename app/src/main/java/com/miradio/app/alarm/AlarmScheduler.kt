package com.miradio.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.miradio.app.data.database.AlarmEntity
import com.miradio.app.util.DiagnosticsLog
import java.util.Calendar

const val EXTRA_ALARM_ID = "alarm_id"

/**
 * Programa/cancela alarmas con AlarmManager.setExactAndAllowWhileIdle:
 * suena a la hora exacta aunque el móvil esté en reposo profundo (Doze),
 * que es justo el caso de uso de una alarma despertador (a diferencia de
 * setAndAllowWhileIdle, que Android puede retrasar varios minutos).
 */
object AlarmScheduler {

    fun schedule(context: Context, alarm: AlarmEntity) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            DiagnosticsLog.logWarning(context, "AlarmScheduler", "Sin permiso de alarmas exactas, no se programa la alarma ${alarm.id}")
            return
        }
        val triggerAtMillis = nextTriggerTimeMillis(alarm)
        val pendingIntent = pendingIntentFor(context, alarm.id)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        DiagnosticsLog.log(
            context,
            "AlarmScheduler",
            "Alarma ${alarm.id} (${alarm.stationName}) programada para ${java.util.Date(triggerAtMillis)}",
        )
    }

    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.cancel(pendingIntentFor(context, alarmId))
        DiagnosticsLog.log(context, "AlarmScheduler", "Alarma $alarmId cancelada")
    }

    private fun pendingIntentFor(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Próxima vez que debe sonar [alarm] a partir de [now]. Función pura
     * (recibe "ahora" en vez de leer System.currentTimeMillis() ella misma)
     * para poder probar el cálculo con fechas fijas en un test unitario.
     */
    fun nextTriggerTimeMillis(alarm: AlarmEntity, now: Calendar = Calendar.getInstance()): Long {
        val repeatDays = alarm.repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet()

        val todayAtAlarmTime = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays.isEmpty()) {
            if (todayAtAlarmTime.timeInMillis <= now.timeInMillis) {
                todayAtAlarmTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            return todayAtAlarmTime.timeInMillis
        }

        // Se prueba hoy y los 6 días siguientes; el primero que caiga en
        // repeatDays y sea en el futuro es el próximo disparo.
        for (offset in 0..7) {
            val candidate = (todayAtAlarmTime.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            if (candidate.get(Calendar.DAY_OF_WEEK) in repeatDays && candidate.timeInMillis > now.timeInMillis) {
                return candidate.timeInMillis
            }
        }
        // No debería llegarse aquí (repeatDays no vacío cubre los 7 días de
        // la semana como muy tarde en el offset 7), pero de recurrir a algo,
        // que sea "dentro de una semana" y no una excepción.
        return todayAtAlarmTime.timeInMillis + 7 * 24 * 60 * 60 * 1000L
    }
}
