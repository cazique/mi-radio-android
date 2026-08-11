package com.miradio.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.playback.PlaybackServiceConnector
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Franja de "ahora suena" fija encima de la barra de navegación, visible
 * desde Inicio/Favoritos/Explorar/Ajustes (igual que Spotify o YouTube
 * Music): permite pausar o abrir el reproductor completo sin tener que
 * volver a Inicio. No se muestra si todavía no ha sonado nada en esta sesión.
 */
@Composable
fun MiniPlayerBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val radioPlayer by PlaybackServiceConnector.player.collectAsState()
    val emptyState = remember { MutableStateFlow(PlayerUiState()) }
    val uiState by (radioPlayer?.uiState ?: emptyState).collectAsState()
    val station = uiState.station ?: return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StationLogo(logoUrl = station.logoUrl, modifier = Modifier.size(40.dp), cornerRadius = 10)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = station.city,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val isActive = uiState.status == PlaybackStatus.PLAYING || uiState.status == PlaybackStatus.BUFFERING
            IconButton(onClick = { if (isActive) radioPlayer?.pause() else radioPlayer?.play() }) {
                if (uiState.status == PlaybackStatus.BUFFERING) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
