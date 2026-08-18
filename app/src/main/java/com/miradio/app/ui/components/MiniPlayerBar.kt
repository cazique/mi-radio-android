package com.miradio.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.domain.model.RadioStation

/**
 * Franja de "ahora suena" fija encima de la barra de navegación, visible
 * desde Inicio/Favoritos/Explorar/Ajustes (igual que Spotify o YouTube
 * Music): permite pausar o abrir el reproductor completo sin tener que
 * volver a Inicio. No se muestra si todavía no ha sonado nada en esta sesión.
 *
 * Sin estado propio (hoisted): quien la usa (MainActivity) es responsable
 * de observar PlaybackServiceConnector y pasarle el [uiState] ya resuelto,
 * en vez de que este composable tenga que saber nada de esa conexión. Así
 * se puede previsualizar y probar sin necesitar un RadioPlayer real.
 */
@Composable
fun MiniPlayerBar(
    uiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = uiState.station ?: return

    // Solo se anima la primera vez que aparece en esta composición (p. ej.
    // al arrancar la radio desde Inicio): visible arranca en false y pasa a
    // true en cuanto se monta, por lo que el deslizamiento hacia arriba +
    // aparición solo se ve una vez, no en cada recomposición mientras suena.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
        modifier = modifier,
    ) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                // La canción (metadatos ICY) es más relevante que la ciudad
                // cuando la emisora la manda; si no, se sigue viendo la ciudad.
                Text(
                    text = uiState.nowPlayingTitle ?: station.city,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                AnimatedContent(
                    targetState = uiState.status,
                    transitionSpec = {
                        (scaleIn(animationSpec = tween(180), initialScale = 0.7f) + fadeIn(tween(180)))
                            .togetherWith(scaleOut(animationSpec = tween(140), targetScale = 0.7f) + fadeOut(tween(140)))
                    },
                    label = "miniPlayPauseIcon",
                ) { animatedStatus ->
                    if (animatedStatus == PlaybackStatus.BUFFERING) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = if (animatedStatus == PlaybackStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
    }
}

@Preview(showBackground = true)
@Composable
private fun MiniPlayerBarPreview() {
    MiniPlayerBar(
        uiState = PlayerUiState(
            station = RadioStation(
                id = "preview",
                name = "COPE Madrid",
                city = "Madrid",
                streamUrl = "",
                logoUrl = null,
                description = null,
                category = null,
                isFavorite = false,
            ),
            status = PlaybackStatus.PLAYING,
        ),
        onTogglePlayPause = {},
        onClick = {},
    )
}
