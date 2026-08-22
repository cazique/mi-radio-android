package com.miradio.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miradio.app.data.database.AppDatabase
import com.miradio.app.util.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager olvida todo lo programado al apagarse el móvil: sin esto,
 *  una alarma activa dejaría de sonar sin más tras un reinicio, algo que
 *  para un despertador es un fallo grave y silencioso. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DiagnosticsLog.log(context, "BootReceiver", "Reinicio detectado, reprogramando alarmas")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = AppDatabase.getInstance(context).alarmDao().getAllEnabled()
                val now = System.currentTimeMillis()
                alarms.forEach { alarm ->
                    // Si había una posposición pendiente y todavía no ha
                    // pasado, se restaura tal cual: sin esto, el reinicio la
                    // habría sustituido en silencio por la próxima hora
                    // "normal" de la alarma (mañana, o el próximo día que
                    // repita), perdiendo el snooze sin avisar.
                    val snoozedUntil = alarm.snoozedUntilMillis
                    if (snoozedUntil != null && snoozedUntil > now) {
                        AlarmScheduler.schedule(context, alarm, triggerAtMillis = snoozedUntil)
                    } else {
                        AlarmScheduler.schedule(context, alarm)
                    }
                }
                DiagnosticsLog.log(context, "BootReceiver", "${alarms.size} alarma(s) reprogramada(s)")
            } catch (e: Exception) {
                DiagnosticsLog.logThrowable(context, "BootReceiver", "Fallo reprogramando alarmas tras reiniciar", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
