package com.miradio.app.domain.model

/**
 * Alarma despertador: hora + emisora que suena. repeatDays vacío significa
 * "solo una vez" (se desactiva sola tras sonar); con días, se repite cada
 * semana en esos días. Los días usan java.util.Calendar.MONDAY..SUNDAY
 * (1=domingo, 2=lunes... 7=sábado), que es lo que necesita AlarmScheduler
 * para calcular la próxima vez que debe sonar.
 */
data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int> = emptySet(),
    val stationId: String,
    val stationName: String,
    val stationStreamUrl: String,
    val stationLogoUrl: String? = null,
    /** Volumen inicial (0f-1f) si volumeIncreasing está activo; si no, es el
     *  volumen fijo con el que suena toda la alarma. */
    val startVolume: Float = 0.3f,
    val volumeIncreasing: Boolean = true,
    val fadeInSeconds: Int = 60,
    val enabled: Boolean = true,
)
