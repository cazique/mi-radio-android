package com.miradio.app.domain.model

enum class NewspaperCategory { NACIONAL, DEPORTIVO }

/**
 * Portada de un periódico para la sección "Portadas de hoy". No se
 * hotlinkea a ciegas ninguna URL de imagen adivinada (eso ya se probó dos
 * veces contra kiosko.net y falló igual con o sin cabeceras de navegador):
 * en vez de eso, [NewspaperCoverDialog] (NewsScreen.kt) carga la página
 * real de kiosko.net en un WebView, saca de su DOM ya cargado la URL real
 * de la imagen de portada y la descarga aparte con el referer y la cookie
 * de esa misma página, para poder verla con zoom, guardarla y compartirla
 * como una imagen normal en vez de solo verla dentro de la página web.
 */
data class NewspaperCover(
    val name: String,
    val code: String,
    val category: NewspaperCategory,
)

object NewspaperCovers {
    val all: List<NewspaperCover> = listOf(
        NewspaperCover("El País", "elpais", NewspaperCategory.NACIONAL),
        NewspaperCover("El Mundo", "elmundo", NewspaperCategory.NACIONAL),
        NewspaperCover("ABC", "abc", NewspaperCategory.NACIONAL),
        NewspaperCover("La Razón", "larazon", NewspaperCategory.NACIONAL),
        NewspaperCover("La Vanguardia", "lavanguardia", NewspaperCategory.NACIONAL),
        NewspaperCover("El Periódico", "elperiodico", NewspaperCategory.NACIONAL),
        NewspaperCover("Marca", "marca", NewspaperCategory.DEPORTIVO),
        NewspaperCover("As", "as", NewspaperCategory.DEPORTIVO),
        NewspaperCover("Sport", "sport", NewspaperCategory.DEPORTIVO),
        NewspaperCover("Mundo Deportivo", "mundodeportivo", NewspaperCategory.DEPORTIVO),
    )
}

/** Página real de kiosko.net con la portada de hoy de este periódico: de
 *  aquí se extrae la imagen real a descargar, y es también el respaldo
 *  visible si esa extracción llega a fallar. */
fun NewspaperCover.kioskoPageUrl(): String = "https://kiosko.net/es/np/$code.html"
