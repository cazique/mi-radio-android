package com.miradio.app.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NewspaperCategory { NACIONAL, DEPORTIVO }

/**
 * Portada de un periódico para la sección "Portadas de hoy": no se guarda
 * ninguna imagen propia, se compone la URL de la portada del día a partir
 * del código del medio en kiosko.net (agregador público de portadas de
 * prensa española). Si algún código deja de coincidir, esa portada
 * simplemente no carga su miniatura (fallo aislado, ver [NewspaperCoverTile]).
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

/** kiosko.net publica cada portada del día como imagen en
 *  img.kiosko.net/AAAA/MM/DD/es/{codigo}.750.jpg. Es una convención de un
 *  sitio externo (no una API propia), así que puede fallar puntualmente:
 *  se trata siempre como "mejor esfuerzo", nunca como algo garantizado. */
fun NewspaperCover.todayImageUrl(): String {
    val datePath = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
    return "https://img.kiosko.net/$datePath/es/$code.750.jpg"
}

/** Búsqueda de imágenes siempre disponible como último recurso, por si la
 *  miniatura de kiosko.net no carga: no depende de conocer ninguna URL
 *  exacta del propio periódico, así que nunca lleva a un enlace roto. */
fun NewspaperCover.searchFallbackUrl(): String {
    val query = android.net.Uri.encode("portada $name hoy")
    return "https://www.google.com/search?tbm=isch&q=$query"
}
