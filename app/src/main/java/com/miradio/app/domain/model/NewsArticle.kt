package com.miradio.app.domain.model

data class NewsArticle(
    val title: String,
    val link: String,
    val description: String?,
    val pubDate: String?,
    val imageUrl: String?,
    /** Solo presente en feeds de audio (p. ej. el boletín informativo): la
     *  URL del propio MP3 del episodio, lista para reproducir. */
    val audioUrl: String? = null,
    /** [NewsSource.id] del que viene este artículo. Solo hace falta para
     *  saber de qué medio es una vez mezclado en la pestaña "Para ti"; en el
     *  boletín en audio se deja a null porque ahí no aplica. */
    val sourceId: String? = null,
)

/**
 * Feeds RSS reales y públicos de COPE (https://www.cope.es/rss-cope.html),
 * uno por sección. Se lee el feed tal cual lo publica COPE (solo texto e
 * imagen del artículo, sin scripts ni publicidad: eso es justamente lo que
 * trae y lo que no trae un RSS) en vez de abrir su web dentro de la app.
 */
enum class NewsCategory(val label: String, val feedUrl: String) {
    PORTADA("Portada", "https://www.cope.es/rss/home.xml"),
    ESPANA("España", "https://www.cope.es/api/es/actualidad/espana/news/rss.xml"),
    ECONOMIA("Economía", "https://www.cope.es/api/es/actualidad/economia/news/rss.xml"),
    DEPORTES("Deportes", "https://www.cope.es/api/es/deportes/news/rss.xml"),
    TECNOLOGIA("Tecnología", "https://www.cope.es/api/es/actualidad/tecnologia/news/rss.xml"),
    SOCIEDAD("Sociedad", "https://www.cope.es/api/es/actualidad/sociedad/news/rss.xml"),
}
