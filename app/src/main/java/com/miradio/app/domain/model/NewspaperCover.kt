package com.miradio.app.domain.model

enum class NewspaperCategory { NACIONAL, DEPORTIVO }

/**
 * Portada de un periódico para la sección "Portadas de hoy". No se hotlinkea
 * la imagen directamente desde la app (Coil/OkHttp sin más: eso ya se probó
 * dos veces contra kiosko.net y kiosko.net la bloqueaba igual con o sin
 * cabeceras de navegador, sin forma de comprobar por qué desde este entorno
 * sin red): en vez de eso se abre la página real de kiosko.net para ese
 * periódico dentro de un navegador embebido (ver [NewspaperCoverDialog] en
 * NewsScreen.kt), que si carga la imagen porque la pide como la pediría
 * cualquier visita normal a kiosko.net (referer y cookies de su propio
 * dominio), no como una petición de imagen suelta desde otra app.
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

/** Página real de kiosko.net con la portada de hoy de este periódico (no una
 *  búsqueda de Google): se abre dentro del navegador embebido de la app. */
fun NewspaperCover.kioskoPageUrl(): String = "https://kiosko.net/es/np/$code.html"

/** Último recurso si kiosko.net no llega a cargar nada útil: búsqueda de
 *  imágenes, para no dejar la pantalla completamente vacía. */
fun NewspaperCover.searchFallbackUrl(): String {
    val query = android.net.Uri.encode("portada $name hoy")
    return "https://www.google.com/search?tbm=isch&q=$query"
}
