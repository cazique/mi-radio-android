package com.miradio.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.R
import com.miradio.app.domain.model.OutputDevice
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.ui.components.CastButton
import com.miradio.app.ui.components.PlayerCard
import com.miradio.app.ui.components.RecentStationChip
import com.miradio.app.ui.components.StationLogo
import com.miradio.app.ui.components.WeatherCard
import com.miradio.app.util.AppUpdater
import com.miradio.app.util.UpdateChecker
import com.miradio.app.util.extractDominantColor

/**
 * Pantalla de Inicio curada: "ahora suena", últimas escuchadas y accesos
 * rápidos a Favoritos/Explorar, en vez de mostrar de golpe el catálogo
 * completo (985 emisoras). El catálogo completo vive en la pestaña
 * "Explorar" (HomeScreen con showFavoritesOnly = false).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLandingScreen(
    onOpenPlayer: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenFavorites: () -> Unit,
    onAddStation: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    val favoritesCount = state.allStations.count { it.isFavorite }

    // Mismo truco que la pantalla de reproducción: el color dominante del
    // logo de lo que suena tiñe el fondo (aquí solo la cabecera, el resto de
    // Inicio sigue con el fondo normal), para que el ambiente de color no
    // quede solo en el reproductor a pantalla completa.
    val context = LocalContext.current
    val fallbackColor = MaterialTheme.colorScheme.background
    var dominantColor by remember { mutableStateOf(fallbackColor) }
    LaunchedEffect(state.displayedStation?.logoUrl, fallbackColor) {
        dominantColor = extractDominantColor(context, state.displayedStation?.logoUrl, fallbackColor)
    }
    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 700),
        label = "homeHeaderBackground",
    )
    val heroBrush = Brush.verticalGradient(
        colors = listOf(
            Color(
                red = animatedColor.red * 0.4f,
                green = animatedColor.green * 0.4f,
                blue = animatedColor.blue * 0.4f,
            ),
            MaterialTheme.colorScheme.background,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            // OJO: no usar R.mipmap.ic_launcher aquí. En
                            // Android 8+ ese mipmap es un icono adaptativo
                            // (XML <adaptive-icon>), y painterResource() de
                            // Compose no sabe cargar ese tipo de recurso:
                            // lanza una excepción y cierra la app nada más
                            // abrir Inicio. Por eso hay un PNG plano aparte
                            // solo para este uso en pantalla.
                            painter = painterResource(R.drawable.logo_radio_dari),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    CastButton()
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            // Añadir/editar emisoras es una acción avanzada: se oculta en
            // modo simple, o si se ha ocultado el botón por su cuenta desde
            // Ajustes (sin necesidad de activar el modo simple completo).
            if (!state.simpleMode && !state.hideAddButton) {
                FloatingActionButton(onClick = onAddStation) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_add_station))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(heroBrush)) {
                NotificationPermissionBanner()
                UpdateAvailableBanner()

                ClockHeader(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DeviceIndicator(
                        deviceName = if (state.player.outputDevice == OutputDevice.CAST) {
                            state.player.castDeviceName ?: stringResource(R.string.home_device_this_phone)
                        } else {
                            stringResource(R.string.home_device_this_phone)
                        },
                    )
                }

                WeatherCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                PlayerCard(
                    station = state.displayedStation,
                    status = state.player.status,
                    onPlayPauseClick = { viewModel.onPrimaryPlayClick(state) },
                    onCardClick = if (state.displayedStation != null && !state.simpleMode) onOpenPlayer else null,
                    nowPlayingTitle = state.player.nowPlayingTitle,
                    onPreviousStation = { viewModel.onSkipStation(-1) },
                    onNextStation = { viewModel.onSkipStation(1) },
                    volume = state.player.volume,
                    onVolumeChange = viewModel::onVolumeChange,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                )
            }

            if (state.simpleMode) {
                SimpleModeFavorites(
                    favorites = state.allStations.filter { it.isFavorite },
                    playingStationId = state.player.station?.id,
                    onStationClick = viewModel::onStationClick,
                )
            } else {
                // No es un campo de texto: es un botón que lleva a Explorar,
                // donde sí se puede escribir y filtrar sobre el catálogo
                // completo. Sin ">" al final se confundía con un buscador
                // vacío en el que se podía tocar para escribir directamente.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenExplore)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(R.string.home_search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.recentStations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    ) {
                        items(state.recentStations, key = { it.id }) { station ->
                            RecentStationChip(
                                station = station,
                                isPlaying = state.player.station?.id == station.id,
                                onClick = { viewModel.onStationClick(station) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickAccessCard(
                        icon = Icons.Filled.Favorite,
                        title = stringResource(R.string.home_favorites),
                        subtitle = stringResource(R.string.home_favorites_count, favoritesCount),
                        onClick = onOpenFavorites,
                        modifier = Modifier.weight(1f),
                    )
                    QuickAccessCard(
                        icon = Icons.Filled.Explore,
                        title = stringResource(R.string.home_explore_all),
                        subtitle = stringResource(R.string.home_explore_count, state.allStations.size),
                        onClick = onOpenExplore,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Sin permiso de notificaciones, Android sustituye la notificación real
 * (portada, emisora, controles de pausa) por un aviso genérico del sistema
 * sin nada de eso, y sin dar ninguna pista de por qué. Esto explica el
 * "solo aparece Radio Dari sin controles" reportado: se detecta el caso y
 * se ofrece un atajo directo a los ajustes de notificaciones de la app en
 * vez de dejar al usuario sin explicación.
 */
@Composable
private fun NotificationPermissionBanner() {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var dismissed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Se vuelve a comprobar cada vez que la app pasa a primer plano, por si
    // el usuario activó el permiso desde Ajustes del sistema y vuelve.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!notificationsEnabled && !dismissed) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = "Notificación de la emisora desactivada",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "Sin permiso de notificaciones, Android no puede mostrar la emisora que suena ni los controles de pausa en la barra de notificaciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text("Activar notificaciones") }
                }
                IconButton(onClick = { dismissed = true }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

/**
 * Hora y fecha grandes, integradas en Inicio (sin tarjeta aparatosa, para
 * que no compita con "ahora suena"). Se actualiza cada minuto por su
 * cuenta mientras la pantalla está abierta, usando la zona horaria del
 * propio dispositivo.
 */
@Composable
private fun ClockHeader(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(java.util.Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.util.Date()
            // Se espera hasta el siguiente cambio de minuto, no un delay fijo
            // de 60s: así no se acumula un desfase que acabaría mostrando la
            // hora con varios segundos de retraso tras un buen rato abierta.
            val msIntoMinute = System.currentTimeMillis() % 60_000L
            kotlinx.coroutines.delay(60_000L - msIntoMinute)
        }
    }
    val timeText = remember(now) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now)
    }
    val dateText = remember(now) {
        java.text.SimpleDateFormat("EEEE, d 'de' MMMM", java.util.Locale("es", "ES"))
            .format(now)
            .replaceFirstChar { it.uppercase() }
    }
    Column(modifier = modifier) {
        Text(text = timeText, style = MaterialTheme.typography.displayMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(text = dateText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Aviso de que hay una versión más reciente instalable, sin que haga falta
 * ir a Ajustes > Buscar actualizaciones a mano: UpdateChecker comprueba
 * solo, en segundo plano, mientras la app está abierta.
 */
@Composable
private fun UpdateAvailableBanner() {
    val context = LocalContext.current
    val update by UpdateChecker.updateAvailable.collectAsState()
    val current = update ?: return

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "Hay una versión nueva",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                current.versionName?.let { versionName ->
                    Text(
                        text = "Versión $versionName disponible para instalar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TextButton(
                    onClick = {
                        AppUpdater.installUpdate(context, current.apkFile)
                        UpdateChecker.dismiss()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Instalar ahora") }
            }
            IconButton(onClick = { UpdateChecker.dismiss() }) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/**
 * Contenido de Inicio en modo simple: solo las emisoras favoritas, como
 * tarjetas grandes en una rejilla de 2 columnas (logo grande + nombre), sin
 * nada más que tocar. Si todavía no hay ninguna favorita, se explica cómo
 * elegirlas en vez de dejar la pantalla vacía sin explicación.
 */
@Composable
private fun SimpleModeFavorites(
    favorites: List<RadioStation>,
    playingStationId: String?,
    onStationClick: (RadioStation) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.home_favorites), style = MaterialTheme.typography.titleMedium)
        if (favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.home_simple_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            favorites.chunked(2).forEach { rowStations ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowStations.forEach { station ->
                        FavoriteStationTile(
                            station = station,
                            isPlaying = playingStationId == station.id,
                            onClick = { onStationClick(station) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Si la fila queda con una sola tarjeta (número impar de
                    // favoritas), se rellena el hueco para que no se estire
                    // a todo el ancho de forma descompensada.
                    if (rowStations.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteStationTile(
    station: RadioStation,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StationLogo(
                logoUrl = station.logoUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                cornerRadius = 16,
            )
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceIndicator(deviceName: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Icons.Filled.Circle,
            contentDescription = null,
            tint = com.miradio.app.ui.theme.SuccessGreen,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = stringResource(R.string.home_device_label, deviceName),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
