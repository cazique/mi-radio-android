package com.miradio.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.R
import com.miradio.app.domain.model.OutputDevice
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.ui.components.CastButton
import com.miradio.app.ui.components.PlayerCard
import com.miradio.app.ui.components.StationListItem
import com.miradio.app.ui.components.StationLogo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    showFavoritesOnly: Boolean,
    onOpenPlayer: () -> Unit,
    onAddStation: () -> Unit,
    onEditStation: (RadioStation) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(showFavoritesOnly) {
        viewModel.setShowFavoritesOnly(showFavoritesOnly)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    CastButton()
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddStation) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_add_station))
            }
        },
    ) { padding ->
        // Con el catálogo nacional (cientos de emisoras) hace falta agrupar;
        // en búsqueda o en la pestaña de favoritos se deja la lista plana, que
        // ya es corta por sí sola. remember() debe llamarse aquí, en el cuerpo
        // @Composable de la pantalla, no dentro del builder de LazyColumn.
        val isBrowsingAll = state.searchQuery.isBlank() && !showFavoritesOnly
        // Qué regiones están desplegadas. Con el catálogo nacional (cientos
        // de emisoras) tenerlas todas abiertas a la vez hace la lista
        // interminable; se recuerda aquí (por región) para poder plegar y
        // desplegar sin perder el resto del estado de la pantalla.
        val expandedRegions = remember { mutableStateMapOf<String, Boolean>() }
        val groups = remember(state.visibleStations, isBrowsingAll) {
            if (!isBrowsingAll) {
                emptyList()
            } else {
                state.visibleStations
                    .groupBy { it.region?.takeIf { r -> r.isNotBlank() } ?: "Otras emisoras" }
                    .toList()
                    .sortedBy { (region, _) -> if (region == "Nacional") "" else region }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Espacio suficiente para que el FloatingActionButton de "añadir"
            // (que flota fijo sobre la esquina inferior derecha) no tape el
            // botón de favorito de las últimas emisoras de la lista.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DeviceIndicator(
                        deviceName = if (state.player.outputDevice == OutputDevice.CAST) {
                            state.player.castDeviceName ?: stringResource(R.string.home_device_this_phone)
                        } else {
                            stringResource(R.string.home_device_this_phone)
                        },
                    )
                }
            }

            item {
                PlayerCard(
                    station = state.displayedStation,
                    status = state.player.status,
                    onPlayPauseClick = { viewModel.onPrimaryPlayClick(state) },
                    onCardClick = if (state.displayedStation != null) onOpenPlayer else null,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    placeholder = { Text(stringResource(R.string.home_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }

            if (isBrowsingAll && state.recentStations.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_recent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(state.recentStations, key = { "recent_${it.id}" }) { station ->
                            RecentStationChip(
                                station = station,
                                isPlaying = state.player.station?.id == station.id,
                                onClick = { viewModel.onStationClick(station) },
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (state.visibleStations.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.home_no_stations),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                }
            }

            if (isBrowsingAll) {
                groups.forEach { (region, stationsInRegion) ->
                    val expanded = expandedRegions[region] ?: true
                    stickyHeader(key = region) {
                        RegionHeader(
                            region = region,
                            count = stationsInRegion.size,
                            expanded = expanded,
                            onClick = { expandedRegions[region] = !expanded },
                        )
                    }
                    if (expanded) {
                        items(stationsInRegion, key = { it.id }) { station ->
                            StationListItem(
                                station = station,
                                isPlaying = state.player.station?.id == station.id,
                                onClick = { viewModel.onStationClick(station) },
                                onFavoriteClick = { viewModel.onFavoriteToggle(station) },
                                onEditClick = editActionFor(station, onEditStation),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = if (showFavoritesOnly) stringResource(R.string.home_favorites) else stringResource(R.string.home_my_stations),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.visibleStations, key = { it.id }) { station ->
                    StationListItem(
                        station = station,
                        isPlaying = state.player.station?.id == station.id,
                        onClick = { viewModel.onStationClick(station) },
                        onFavoriteClick = { viewModel.onFavoriteToggle(station) },
                        onEditClick = editActionFor(station, onEditStation),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** El catálogo remoto se administra editando el JSON remoto, no desde la app;
 *  SEED y LOCAL sí son editables a mano (p. ej. para corregir la URL
 *  provisional de COPE La Bañeza si consigues la exacta de la sede local). */
private fun editActionFor(station: RadioStation, onEditStation: (RadioStation) -> Unit): (() -> Unit)? =
    if (station.source != com.miradio.app.domain.model.StationSource.REMOTE) {
        { onEditStation(station) }
    } else {
        null
    }

@Composable
private fun RegionHeader(region: String, count: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = region,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentStationChip(station: RadioStation, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StationLogo(logoUrl = station.logoUrl, modifier = Modifier.size(64.dp), cornerRadius = 16)
        Text(
            text = station.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
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
