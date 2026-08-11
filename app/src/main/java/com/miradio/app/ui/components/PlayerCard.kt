package com.miradio.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miradio.app.R
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.RadioStation

/**
 * Tarjeta grande de "ahora suena" que se ve en la pantalla principal, tal y
 * como se pidió en el boceto de la interfaz: logo, nombre de la emisora y
 * un botón de play/pause bien visible.
 */
@Composable
fun PlayerCard(
    station: RadioStation?,
    status: PlaybackStatus,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .let { if (onCardClick != null) it.clickable(onClick = onCardClick) else it },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StationLogo(
                logoUrl = station?.logoUrl,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(1f),
                cornerRadius = 24,
            )

            Text(
                text = station?.name ?: stringResource(R.string.player_no_station),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            if (station != null) {
                Text(
                    text = station.city,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PlayPauseButton(status = status, enabled = station != null, onClick = onPlayPauseClick)
        }
    }
}

@Composable
fun PlayPauseButton(status: PlaybackStatus, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isPlaying = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING
    val description = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        when (status) {
            PlaybackStatus.BUFFERING -> CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 3.dp,
            )
            else -> Icon(
                imageVector = if (status == PlaybackStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = description,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
