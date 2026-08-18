package com.miradio.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.miradio.app.domain.model.Alarm

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Días de repetición (Calendar.MONDAY..SUNDAY) separados por comas,
     *  p. ej. "2,3,4,5,6" para lunes a viernes. Vacío = solo una vez. */
    val repeatDays: String,
    val stationId: String,
    val stationName: String,
    val stationStreamUrl: String,
    val stationLogoUrl: String?,
    val startVolume: Float,
    val volumeIncreasing: Boolean,
    val fadeInSeconds: Int,
    val enabled: Boolean,
)

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    repeatDays = repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet(),
    stationId = stationId,
    stationName = stationName,
    stationStreamUrl = stationStreamUrl,
    stationLogoUrl = stationLogoUrl,
    startVolume = startVolume,
    volumeIncreasing = volumeIncreasing,
    fadeInSeconds = fadeInSeconds,
    enabled = enabled,
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    repeatDays = repeatDays.sorted().joinToString(","),
    stationId = stationId,
    stationName = stationName,
    stationStreamUrl = stationStreamUrl,
    stationLogoUrl = stationLogoUrl,
    startVolume = startVolume,
    volumeIncreasing = volumeIncreasing,
    fadeInSeconds = fadeInSeconds,
    enabled = enabled,
)
