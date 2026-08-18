package com.miradio.app.data.repository

import android.content.Context
import com.miradio.app.alarm.AlarmScheduler
import com.miradio.app.data.database.AlarmDao
import com.miradio.app.data.database.toDomain
import com.miradio.app.data.database.toEntity
import com.miradio.app.domain.model.Alarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Guardar/borrar una alarma y (des)programarla con AlarmManager son siempre
 * la misma operación desde el punto de vista de quien la usa: aquí se
 * mantienen juntas para que la UI nunca pueda guardar una alarma en Room
 * sin que además quede realmente programada, o al revés.
 */
class AlarmRepository(private val context: Context, private val dao: AlarmDao) {

    val alarms: Flow<List<Alarm>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(alarm: Alarm): Unit = withContext(Dispatchers.IO) {
        val entity = alarm.toEntity()
        val id = dao.upsert(entity)
        val saved = if (alarm.id == 0L) entity.copy(id = id) else entity
        if (saved.enabled) {
            AlarmScheduler.schedule(context, saved)
        } else {
            AlarmScheduler.cancel(context, saved.id)
        }
    }

    suspend fun delete(alarm: Alarm): Unit = deleteById(alarm.id)

    suspend fun deleteById(alarmId: Long): Unit = withContext(Dispatchers.IO) {
        AlarmScheduler.cancel(context, alarmId)
        dao.getById(alarmId)?.let { dao.delete(it) }
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        dao.setEnabled(alarm.id, enabled)
        if (enabled) {
            AlarmScheduler.schedule(context, alarm.toEntity().copy(enabled = true))
        } else {
            AlarmScheduler.cancel(context, alarm.id)
        }
    }
}
