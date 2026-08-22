package com.miradio.app.util

/**
 * Historial de cambios en español sencillo, para gente sin vocabulario
 * técnico: no "commits" ni nombres de archivo, sino qué cambia para quien
 * usa la app. id es un contador propio (no el número de build de CI, que
 * sube en cada compilación aunque no haya nada visible que contar): sirve
 * para saber qué entradas son "nuevas desde la última vez que se vieron"
 * (ver PreferencesRepository.lastSeenChangelogId).
 *
 * Al añadir una función nueva, se añade aquí una entrada más (id = la más
 * alta + 1, arriba del todo de la lista).
 */
data class ChangelogEntry(val id: Int, val title: String, val bullets: List<String>)

object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            id = 7,
            title = "Noticias más completas",
            bullets = listOf(
                "Cada noticia muestra ahora la fecha y hora de publicación.",
                "Tirar hacia abajo en Noticias las actualiza (además del refresco automático).",
                "Arreglado que el último boletín se quedaba siempre fijo en el mismo episodio.",
                "Se pueden activar El Mundo, ABC, La Razón y OKDiario como fuentes adicionales en Ajustes > Noticias, además de añadir cualquier otra por su RSS.",
                "Ajustes > Diagnóstico: se puede poner una URL propia para subir el registro solo tras un cierre inesperado, sin tener que compartirlo a mano.",
                "El diálogo de Novedades indica ahora el número de versión de cada cambio.",
            ),
        ),
        ChangelogEntry(
            id = 6,
            title = "Botón de reproducir arreglado",
            bullets = listOf(
                "El botón de play/pausa se veía como un círculo gigante y descolocado en algunos momentos; ya vuelve a verse como un botón normal.",
            ),
        ),
        ChangelogEntry(
            id = 5,
            title = "Reproductor con nuevo aspecto",
            bullets = listOf(
                "El fondo de la pantalla de reproducción ahora cambia de color según el logo de la emisora que suena.",
                "Animaciones más suaves al dar a play/pausa y en la onda de sonido.",
                "Nueva opción en Ajustes para usar los colores del sistema (Material You) en vez de los propios de Radio Dari.",
            ),
        ),
        ChangelogEntry(
            id = 4,
            title = "El tiempo en Inicio",
            bullets = listOf(
                "Nueva tarjeta con la temperatura actual y la previsión de los próximos días, usando tu ubicación aproximada.",
                "Se pide permiso de ubicación solo si tocas 'Activar', nunca automáticamente.",
            ),
        ),
        ChangelogEntry(
            id = 3,
            title = "Notificación y temporizador de apagado arreglados",
            bullets = listOf(
                "La notificación de la emisora ya no se queda sin controles de pausa/siguiente/anterior.",
                "Al terminar el temporizador de apagado, volver a dar a play ahora retoma en directo, no desde donde se quedó.",
                "Arreglado un cierre inesperado al compartir el registro técnico desde Ajustes.",
            ),
        ),
        ChangelogEntry(
            id = 2,
            title = "Lectura de noticias en voz alta más fiable",
            bullets = listOf(
                "Ya no aparece el aviso de 'no tiene motor de voz' al tocar Escuchar justo después de abrir una noticia.",
            ),
        ),
        ChangelogEntry(
            id = 1,
            title = "Actualizaciones dentro de la app más fiables",
            bullets = listOf(
                "Arreglado un fallo que a veces impedía comprobar o descargar actualizaciones.",
            ),
        ),
    )

    val latestId: Int get() = entries.maxOf { it.id }
}
