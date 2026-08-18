package com.miradio.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Fuente RSS añadida a mano por el usuario en Ajustes (El Mundo, ABC, La
 * Razón, el periódico de su provincia, lo que sea): id estable (para poder
 * borrarla luego), nombre a mostrar y URL del feed.
 */
@Serializable
data class CustomNewsSource(
    val id: String,
    val name: String,
    val feedUrl: String,
)

/**
 * Fuente seleccionable en la pestaña Noticias: une las secciones fijas de
 * COPE ([NewsCategory]) con las fuentes propias del usuario, para que la
 * pantalla no tenga que distinguir entre unas y otras al pintar las
 * pestañas ni al pedir el feed.
 */
data class NewsSource(
    val id: String,
    val label: String,
    val feedUrl: String,
    val isCustom: Boolean = false,
) {
    companion object {
        fun fromCategory(category: NewsCategory): NewsSource =
            NewsSource(id = "cope_${category.name}", label = category.label, feedUrl = category.feedUrl)

        fun fromCustom(custom: CustomNewsSource): NewsSource =
            NewsSource(id = custom.id, label = custom.name, feedUrl = custom.feedUrl, isCustom = true)
    }
}

/**
 * Medios conocidos que se pueden activar con un interruptor en Ajustes >
 * Noticias, sin tener que teclear su URL de RSS a mano. Desactivados por
 * defecto: cada cual elige qué medios quiere ver, además de COPE.
 *
 * OJO: las URL de RSS de un medio pueden cambiar con el tiempo sin aviso;
 * si alguna deja de funcionar, se ve como "no se han podido cargar las
 * noticias de X" (fallo normal ya contemplado), sin afectar al resto.
 */
object PresetNewsSources {
    val all: List<NewsSource> = listOf(
        NewsSource(id = "preset_elmundo", label = "El Mundo", feedUrl = "https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml"),
        NewsSource(id = "preset_abc", label = "ABC", feedUrl = "https://www.abc.es/rss/feeds/abcPortada.xml"),
        NewsSource(id = "preset_larazon", label = "La Razón", feedUrl = "https://www.larazon.es/rss/portada.xml"),
        NewsSource(id = "preset_okdiario", label = "OKDiario", feedUrl = "https://okdiario.com/feed"),
    )
}
