package com.miradio.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
 * Tarjeta grande de "ahora suena" que se ve en la pantalla principal: logo
 * centrado, nombre y ciudad de la emisora, controles de anterior/pausa/
 * siguiente y volumen, igual que en la pantalla completa del reproductor
 * pero sin salir de Inicio.
 */
@Composable
fun PlayerCard(
    station: RadioStation?,
    status: PlaybackStatus,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null,
    nowPlayingTitle: String? = null,
    onPreviousStation: (() -> Unit)? = null,
    onNextStation: (() -> Unit)? = null,
    volume: Float = 1f,
    onVolumeChange: ((Float) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    val content: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StationLogo(
                logoUrl = station?.logoUrl,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(1f)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), clip = false),
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
                // "Artista - Canción" de los metadatos ICY del stream, si la
                // emisora los envía (muchas no lo hacen).
                nowPlayingTitle?.let { nowPlaying ->
                    Text(
                        text = nowPlaying,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPreviousStation != null) {
                    IconButton(onClick = onPreviousStation, enabled = station != null) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.cd_previous_station),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                PlayPauseButton(status = status, enabled = station != null, onClick = onPlayPauseClick)
                if (onNextStation != null) {
                    IconButton(onClick = onNextStation, enabled = station != null) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.cd_next_station),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            if (onVolumeChange != null && station != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when {
                            volume <= 0f -> Icons.Filled.VolumeOff
                            volume < 0.5f -> Icons.Filled.VolumeDown
                            else -> Icons.Filled.VolumeUp
                        },
                        contentDescription = stringResource(R.string.cd_volume),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
    if (onCardClick != null) {
        Card(
            onClick = onCardClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            content = content,
        )
    }
}

@Composable
fun PlayPauseButton(status: PlaybackStatus, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isPlaying = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING
    val description = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(88.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        AnimatedContent(
            targetState = status,
            // clipToBounds(): scaleIn/scaleOut son transformaciones puramente
            // visuales (graphicsLayer) que, sin recortar, pueden pintar fuera
            // del hueco de 88dp del botón durante la transición (se vio como
            // un círculo enorme y descolocado en el reproductor de Inicio).
            // Con el recorte, la animación queda siempre dentro del botón.
            modifier = Modifier.clipToBounds(),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                (scaleIn(animationSpec = tween(180), initialScale = 0.7f) + fadeIn(tween(180)))
                    .togetherWith(scaleOut(animationSpec = tween(140), targetScale = 0.7f) + fadeOut(tween(140)))
            },
            label = "playPauseIcon",
        ) { animatedStatus ->
            when (animatedStatus) {
                PlaybackStatus.BUFFERING -> CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                )
                else -> Icon(
                    imageVector = if (animatedStatus == PlaybackStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = description,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}
