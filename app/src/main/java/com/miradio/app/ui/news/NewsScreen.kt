package com.miradio.app.ui.news

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.miradio.app.domain.model.NewsArticle
import com.miradio.app.domain.model.NewsSource
import com.miradio.app.domain.model.NewspaperCover
import com.miradio.app.domain.model.NewspaperCovers
import com.miradio.app.domain.model.NewspaperCategory
import com.miradio.app.domain.model.searchFallbackUrl
import com.miradio.app.util.NewsTts
import com.miradio.app.util.parseRssPubDateMillis
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pestaña de Noticias: secciones RSS de COPE más cualquier fuente propia
 * que se añada ("Añadir" al final de las pestañas), mostrado a lo grande
 * (foto + titular bien visible, como el feed de noticias del móvil), sin
 * nada de publicidad porque solo se lee y se pinta el contenido del RSS,
 * nunca la página completa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: NewsViewModel = viewModel(factory = NewsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedArticle by remember { mutableStateOf<NewsArticle?>(null) }
    var showAddSourceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Noticias") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BulletinCard(
                bulletin = state.bulletin,
                isLoading = state.isBulletinLoading,
                isPlaying = state.isBulletinPlaying,
                onPlayClick = viewModel::onBulletinPlayClick,
                modifier = Modifier.padding(16.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(state.sources, key = { it.id }) { source ->
                    FilterChip(
                        selected = source == state.selectedSource,
                        onClick = { viewModel.onSourceSelect(source) },
                        label = { Text(source.label) },
                        leadingIcon = if (source.id == NewsSource.FOR_YOU_ID) {
                            { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                        trailingIcon = if (source.isCustom) {
                            {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Quitar ${source.label}",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.removeCustomSource(source) },
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showAddSourceDialog = true },
                        label = { Text("Añadir") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tirar hacia abajo para refrescar, además del refresco automático
            // cada 10 min: para quien no quiere esperar.
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.isLoading && state.articles.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                    state.articles.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.selectedSource.id == NewsSource.FOR_YOU_ID) {
                                "Activa alguna fuente en Ajustes > Noticias para tener algo que mostrarte aquí."
                            } else {
                                "No hay noticias en esta fuente ahora mismo."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                    // AnimatedContent con la propia pestaña como "target": un
                    // fundido suave entre listas al cambiar de pestaña, en
                    // vez del salto en seco de antes (Grok señaló que faltaban
                    // microanimaciones al cambiar de pestaña).
                    else -> AnimatedContent(targetState = state.selectedSource.id, label = "newsList") {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            if (state.selectedSource.id == NewsSource.FOR_YOU_ID) {
                                item { PortadasSection() }
                            }
                            items(state.articles, key = { it.link }) { article ->
                                val sourceLabel = if (state.selectedSource.id == NewsSource.FOR_YOU_ID) {
                                    state.sources.firstOrNull { it.id == article.sourceId }?.label ?: ""
                                } else {
                                    state.selectedSource.label
                                }
                                NewsArticleCard(
                                    article = article,
                                    sourceLabel = sourceLabel,
                                    onClick = {
                                        selectedArticle = article
                                        viewModel.onArticleOpened(article)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedArticle?.let { article ->
        val sourceLabel = if (state.selectedSource.id == NewsSource.FOR_YOU_ID) {
            state.sources.firstOrNull { it.id == article.sourceId }?.label ?: ""
        } else {
            state.selectedSource.label
        }
        NewsDetailDialog(article = article, sourceLabel = sourceLabel, onDismiss = { selectedArticle = null })
    }

    if (showAddSourceDialog) {
        val addState by viewModel.addSourceState.collectAsStateWithLifecycle()
        var hasSubmitted by remember { mutableStateOf(false) }
        // Se cierra sola en cuanto la comprobación termina bien: solo si ya
        // se había enviado el formulario (hasSubmitted), para no confundir
        // el Idle inicial (antes de tocar "Añadir") con un éxito.
        LaunchedEffect(addState, hasSubmitted) {
            if (hasSubmitted && addState == AddSourceState.Idle) {
                showAddSourceDialog = false
                hasSubmitted = false
            }
        }
        AddNewsSourceDialog(
            addState = addState,
            onDismiss = {
                showAddSourceDialog = false
                hasSubmitted = false
                viewModel.clearAddSourceError()
            },
            onConfirm = { name, url ->
                hasSubmitted = true
                viewModel.addCustomSource(name, url)
            },
        )
    }
}

/**
 * Diálogo para añadir una fuente RSS propia (El Mundo, ABC, La Razón, el
 * periódico de tu provincia...). Comprueba el feed antes de guardarlo, para
 * no dejar guardada una URL que en realidad no funciona.
 */
@Composable
private fun AddNewsSourceDialog(
    addState: AddSourceState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val checking = addState is AddSourceState.Checking

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir fuente de noticias") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Añade el feed RSS de cualquier medio (por ejemplo, El Mundo, ABC o La Razón, " +
                        "o el periódico de tu provincia).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL del feed RSS") },
                    singleLine = true,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (checking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Comprobando…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (addState is AddSourceState.Error) {
                    Text(
                        text = addState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifBlank { url.trim() }, url.trim()) },
                enabled = !checking && name.isNotBlank() && url.isNotBlank(),
            ) { Text("Añadir") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !checking) { Text("Cancelar") }
        },
    )
}

/** Estilo "tarjeta grande con foto" tipo Spotify/YouTube Music: la imagen
 *  ocupa toda la tarjeta y el titular va superpuesto abajo sobre un
 *  degradado a negro, en vez de foto arriba y texto aparte debajo. */
@Composable
private fun NewsArticleCard(article: NewsArticle, sourceLabel: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    ) {
        if (article.imageUrl != null) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SourceMetaRow(
                        sourceLabel = sourceLabel,
                        pubDate = article.pubDate,
                        articleLink = article.link,
                        contentColor = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                SourceMetaRow(
                    sourceLabel = sourceLabel,
                    pubDate = article.pubDate,
                    articleLink = article.link,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Medio (con el icono real del sitio cuando se consigue cargar, para poder
 *  reconocer de un vistazo si es El Mundo, ABC, etc. y no solo una letra
 *  de color) + fecha y hora, en una sola línea compacta. */
@Composable
private fun SourceMetaRow(
    sourceLabel: String,
    pubDate: String?,
    articleLink: String?,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    val text = listOfNotNull(sourceLabel.takeIf { it.isNotBlank() }, formatArticleDate(pubDate)).joinToString(" · ")
    if (sourceLabel.isBlank() && text.isBlank()) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (sourceLabel.isNotBlank()) {
            SourceIcon(sourceLabel = sourceLabel, articleLink = articleLink, modifier = Modifier.size(18.dp))
        }
        if (text.isNotBlank()) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
    }
}

/** Icono del medio: intenta el favicon real del dominio de la noticia (así
 *  se reconoce el periódico, no solo un color) y, si no carga, cae en una
 *  insignia con la inicial del nombre en vez de dejar un hueco vacío. */
@Composable
private fun SourceIcon(sourceLabel: String, articleLink: String?, modifier: Modifier = Modifier) {
    val faviconUrl = remember(articleLink) { articleLink?.let { faviconUrlFor(it) } }
    Box(
        modifier = modifier.clip(CircleShape).background(sourceBadgeColor(sourceLabel)),
        contentAlignment = Alignment.Center,
    ) {
        val initial = @Composable {
            Text(
                text = sourceLabel.first().uppercaseChar().toString(),
                color = Color.White,
                fontSize = 10.sp,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (faviconUrl != null) {
            SubcomposeAsyncImage(
                model = faviconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                loading = { initial() },
                error = { initial() },
            )
        } else {
            initial()
        }
    }
}

/** El favicon de cada medio (google.com/s2/favicons) se pide a partir del
 *  dominio de la propia noticia, no del feed: así funciona igual en "Para
 *  ti" (donde se mezclan varios medios) sin tener que ir a buscar la fuente
 *  original de cada artículo. */
private fun faviconUrlFor(articleLink: String): String? =
    runCatching { Uri.parse(articleLink).host }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { host -> "https://www.google.com/s2/favicons?sz=64&domain=$host" }

/** El link de un artículo viene de un feed RSS externo sin ningún control
 *  sobre su contenido: abrirlo a ciegas con ACTION_VIEW permitiría a un feed
 *  malicioso o comprometido colar un esquema "intent://" (u otro no-http/s)
 *  para lanzar actividades arbitrarias del sistema en vez de solo abrir una
 *  página web. Se valida el esquema antes de lanzar el Intent. */
private fun openArticleLink(context: android.content.Context, link: String?) {
    val uri = link?.let { runCatching { Uri.parse(it) }.getOrNull() }
    if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https")) {
        Toast.makeText(context, "Enlace no válido", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { Toast.makeText(context, "No se ha podido abrir el enlace", Toast.LENGTH_SHORT).show() }
}

private val SOURCE_BADGE_COLORS = listOf(
    Color(0xFF2E9B5C), Color(0xFF3B6FD4), Color(0xFFD46A3B),
    Color(0xFF9B4FD4), Color(0xFFD43B7A), Color(0xFF3BAFD4),
)

/** Mismo color siempre para el mismo medio (hash del nombre), para poder
 *  reconocerlo de un vistazo entre noticias sin tener que leer el texto. */
private fun sourceBadgeColor(label: String): Color =
    SOURCE_BADGE_COLORS[label.hashCode().mod(SOURCE_BADGE_COLORS.size)]

/** Fila horizontal con las portadas del día de la prensa nacional y
 *  deportiva: cada tarjeta abre la portada real en un navegador embebido
 *  (ver [NewspaperCoverDialog]), en vez de intentar mostrar una miniatura
 *  descargada de un sitio externo. */
@Composable
private fun PortadasSection() {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "Portadas de hoy",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Prensa nacional",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        PortadasRow(NewspaperCovers.all.filter { it.category == NewspaperCategory.NACIONAL })
        Text(
            text = "Deportes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        PortadasRow(NewspaperCovers.all.filter { it.category == NewspaperCategory.DEPORTIVO })
    }
}

@Composable
private fun PortadasRow(covers: List<NewspaperCover>) {
    var expanded by remember { mutableStateOf<NewspaperCover?>(null) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(covers, key = { it.code }) { cover ->
            NewspaperCoverTile(cover = cover, onClick = { expanded = cover })
        }
    }
    expanded?.let { cover ->
        NewspaperCoverDialog(cover = cover, onDismiss = { expanded = null })
    }
}

/** De kiosko.net (dos intentos: URL directa y luego con cabeceras de
 *  navegador) no se ha conseguido cargar ninguna miniatura de verdad, así
 *  que en vez de seguir adivinando a ciegas una tercera URL que tampoco se
 *  puede comprobar desde aquí, la miniatura pasa a ser siempre el mismo
 *  icono + nombre + fecha de hoy: no depende de ninguna descarga, así que
 *  no puede quedarse a medias ni mostrar un icono de "fallo" por sorpresa.
 *  Al tocarla se abre la portada de verdad (ver [NewspaperCoverDialog]). */
@Composable
private fun NewspaperCoverTile(cover: NewspaperCover, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(150.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(16.dp))
                .background(sourceBadgeColor(cover.name).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Newspaper,
                contentDescription = null,
                tint = sourceBadgeColor(cover.name),
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = cover.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = todayShortLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun todayShortLabel(): String =
    SimpleDateFormat("d MMM", Locale("es", "ES")).format(java.util.Date())

/** Muestra la portada de verdad dentro de la propia app (sin depender de
 *  ninguna URL de imagen adivinada): un navegador embebido con la búsqueda
 *  de imágenes de esa portada de hoy, con opción de abrirla en el
 *  navegador del teléfono para guardar o compartir con sus propias
 *  herramientas (mantener pulsada la imagen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewspaperCoverDialog(cover: NewspaperCover, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val searchUrl = remember(cover) { cover.searchFallbackUrl() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(cover.name) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    },
                    actions = {
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))) }) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = "Abrir en el navegador")
                        }
                    },
                )
            },
        ) { padding ->
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { viewContext ->
                    android.webkit.WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = android.webkit.WebViewClient()
                        loadUrl(searchUrl)
                    }
                },
                // Sin "update": se ejecuta en cada recomposición (p. ej. al
                // rotar la pantalla o simplemente al redibujar la barra
                // superior), y volver a cargar la URL ahí tiraba a la basura
                // el scroll y el estado de la página cargada por "factory".
            )
        }
    }
}

/** Detalle a pantalla completa: título, foto e íntegro el resumen del RSS,
 *  con la opción (nunca obligatoria) de abrir la noticia completa en el
 *  navegador si se quiere leer más de lo que trae el resumen. Incluye un
 *  control de tamaño de letra propio (independiente del de Ajustes) para
 *  poder agrandar solo el texto de la noticia sin tocar el resto de la app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsDetailDialog(article: NewsArticle, sourceLabel: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 1.15f de partida: algo más grande que el cuerpo de texto normal de la
    // app, pensado para que se lea cómodo desde el principio sin tener que
    // tocar nada. Con los botones A-/A+ se puede ajustar entre 0.85x y 1.85x.
    var textScale by remember { mutableStateOf(1.15f) }

    val tts = remember { NewsTts(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()
    val ttsError by tts.error.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    LaunchedEffect(ttsError) {
        ttsError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false: sin esto la ventana propia del
        // Dialog no se extiende bajo la barra de gestos y navigationBarsPadding()
        // de más abajo no tiene ningún hueco real que respetar, así que el
        // botón de "Leer completa" queda pegado (y la línea de gestos lo
        // atraviesa) en vez de quedar por encima, limpio.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Noticia") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { textScale = (textScale - 0.15f).coerceAtLeast(0.85f) },
                        ) {
                            Icon(Icons.Filled.TextDecrease, contentDescription = "Letra más pequeña")
                        }
                        IconButton(
                            onClick = { textScale = (textScale + 0.15f).coerceAtMost(1.85f) },
                        ) {
                            Icon(Icons.Filled.TextIncrease, contentDescription = "Letra más grande")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                article.imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        // Sin esto, en móviles con navegación por gestos el
                        // botón de "Leer completa" quedaba pegado (a veces
                        // tapado) por la barra del sistema, justo lo que se
                        // reportó.
                        .navigationBarsPadding(),
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value * textScale).sp,
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    SourceMetaRow(
                        sourceLabel = sourceLabel,
                        pubDate = article.pubDate,
                        articleLink = article.link,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                    Button(
                        onClick = {
                            if (isSpeaking) {
                                tts.stop()
                            } else {
                                val textToRead = listOfNotNull(article.title, article.description).joinToString(". ")
                                tts.speak(textToRead)
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(if (isSpeaking) "Detener lectura" else "Escuchar noticia")
                    }
                    article.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textScale).sp,
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = { openArticleLink(context, article.link) },
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(if (sourceLabel.isNotBlank()) "Leer completa en $sourceLabel" else "Leer completa")
                    }
                }
            }
        }
    }
}

/** Tarjeta del último boletín informativo en audio: un solo botón grande de
 *  reproducir/pausar, reutilizando el mismo reproductor (y su notificación)
 *  que las emisoras de radio. */
@Composable
private fun BulletinCard(
    bulletin: NewsArticle?,
    isLoading: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Newspaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "Último boletín informativo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = when {
                        isLoading -> "Cargando…"
                        bulletin != null -> bulletin.title
                        else -> "No disponible ahora mismo"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Se enseña la hora que trae el feed (aunque no siempre se
                // pueda confiar en que sea la más reciente de verdad): así se
                // puede comprobar de un vistazo qué boletín cree la app que
                // es el último, en vez de tener que abrirlo para saberlo.
                if (!isLoading) {
                    formatArticleDate(bulletin?.pubDate)?.let { dateText ->
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            } else {
                FilledIconButton(
                    onClick = onPlayClick,
                    enabled = bulletin?.audioUrl != null,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar boletín" else "Escuchar boletín",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** Los RSS traen la fecha en formato RFC-822 ("Wed, 12 Aug 2026 10:00:00
 *  +0200", aunque no todos los medios lo escriben exactamente igual, de ahí
 *  varios patrones). Se muestra fecha y hora tal cual (pedido explícito:
 *  "que se vea la fecha y hora de la noticia") en vez de un "hace X"
 *  relativo, más ambiguo pasadas unas horas. Si no se puede interpretar,
 *  no se muestra nada de fecha en vez de enseñar el texto en crudo. */
private fun formatArticleDate(pubDate: String?): String? {
    val parsedMillis = parseRssPubDateMillis(pubDate) ?: return null
    val diffMs = (System.currentTimeMillis() - parsedMillis).coerceAtLeast(0)
    // "ahora"/"hace X min" solo para lo muy reciente (más útil que una hora
    // exacta cuando acaba de publicarse); a partir de ahí, fecha y hora
    // tal cual.
    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "ahora"
        diffMs < TimeUnit.HOURS.toMillis(1) -> "hace ${TimeUnit.MILLISECONDS.toMinutes(diffMs)} min"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("es", "ES")).format(java.util.Date(parsedMillis))
    }
}
