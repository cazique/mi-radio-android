package com.miradio.app.domain.model

enum class NewspaperCategory { NACIONAL, DEPORTIVO }

/**
 * Portada de un periódico para la sección "Portadas de hoy". No se hotlinkea
 * ninguna imagen de un agregador externo (se intentó con kiosko.net y
 * siguió sin cargar, sin forma de comprobar por qué desde este entorno sin
 * red): la portada de verdad se muestra en un navegador embebido dentro de
 * la propia app (ver [NewspaperCoverDialog] en NewsScreen.kt).
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

/** Búsqueda de imágenes de la portada de hoy: siempre da algún resultado
 *  real (a diferencia de adivinar la URL exacta de un agregador externo),
 *  así que es la fuente principal de la portada, no un último recurso. */
fun NewspaperCover.searchFallbackUrl(): String {
    val query = android.net.Uri.encode("portada $name hoy")
    return "https://www.google.com/search?tbm=isch&q=$query"
}
