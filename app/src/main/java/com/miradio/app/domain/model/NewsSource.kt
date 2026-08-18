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
