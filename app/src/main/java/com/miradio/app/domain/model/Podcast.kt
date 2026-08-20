package com.miradio.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Podcast tal y como lo devuelve la búsqueda/lista de populares de iTunes.
 * [feedUrl] es null cuando viene de "más populares" (esa lista de Apple no
 * trae la URL del feed, solo el id de colección; hay que resolverla aparte
 * con una consulta a "lookup" antes de poder pedir los episodios).
 *
 * @Serializable porque también es la forma en que se guarda un podcast
 * "seguido" en DataStore (ver [com.miradio.app.data.repository.PreferencesRepository.subscribedPodcasts]):
 * al guardar ya con el feedUrl resuelto, abrir un podcast seguido no
 * necesita la consulta extra a "lookup" que sí hace falta desde "más
 * populares".
 */
@Serializable
data class Podcast(
    val collectionId: String,
    val name: String,
    val publisher: String?,
    val artworkUrl: String?,
    val feedUrl: String?,
)

/** Episodio de un podcast, ya con los datos del propio podcast copiados
 *  dentro (nombre, portada) para no tener que ir a buscarlos aparte al
 *  reproducirlo o mostrarlo en cualquier lista. */
data class PodcastEpisode(
    val id: String,
    val podcastId: String,
    val podcastName: String,
    val podcastArtworkUrl: String?,
    val title: String,
    val description: String?,
    val pubDate: String?,
    val audioUrl: String,
    /** Duración anunciada por el feed, si la trae. Puramente informativa: la
     *  duración real que gobierna la barra de progreso es la que reporta
     *  ExoPlayer una vez cargado el audio. */
    val durationSeconds: Long?,
)
