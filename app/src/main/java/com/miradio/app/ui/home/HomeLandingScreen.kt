package com.miradio.app.ui.home

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.R
import com.miradio.app.domain.model.OutputDevice
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.ui.components.CastButton
import com.miradio.app.ui.components.PlayerCard
import com.miradio.app.ui.components.RecentStationChip
import com.miradio.app.ui.components.StationLogo

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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                DeviceIndicator(
                    deviceName = if (state.player.outputDevice == OutputDevice.CAST) {
                        state.player.castDeviceName ?: stringResource(R.string.home_device_this_phone)
                    } else {
                        stringResource(R.string.home_device_this_phone)
                    },
                )
            }

            PlayerCard(
                station = state.displayedStation,
                status = state.player.status,
                onPlayPauseClick = { viewModel.onPrimaryPlayClick(state) },
                onCardClick = if (state.displayedStation != null && !state.simpleMode) onOpenPlayer else null,
                nowPlayingTitle = state.player.nowPlayingTitle,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            )

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
