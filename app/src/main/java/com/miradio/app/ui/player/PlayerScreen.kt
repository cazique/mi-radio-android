package com.miradio.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.R
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.ui.components.CastButton
import com.miradio.app.ui.components.DelayDialog
import com.miradio.app.ui.components.PlayPauseButton
import com.miradio.app.ui.components.SleepTimerDialog
import com.miradio.app.ui.components.StationLogo
import com.miradio.app.ui.components.Waveform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    val station = state.player.station
    val isPlaying = state.player.status == PlaybackStatus.PLAYING

    Scaffold(
        containerColor = Color(0xFF0E0E14),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (state.player.outputDevice == com.miradio.app.domain.model.OutputDevice.CAST) {
                            stringResource(R.string.cast_connected_to, state.player.castDeviceName.orEmpty())
                        } else {
                            stringResource(R.string.home_device_this_phone)
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                    }
                },
                actions = { CastButton() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .aspectRatio(1f)
                    .padding(top = 12.dp),
            ) {
                StationLogo(logoUrl = station?.logoUrl, modifier = Modifier.fillMaxSize(), cornerRadius = 20)
                if (isPlaying) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(bottomStart = 20.dp, topEnd = 20.dp),
                        modifier = Modifier.align(Alignment.BottomStart),
                    ) {
                        Text(
                            text = stringResource(R.string.player_live_badge),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            station?.category?.let { category ->
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Radio, contentDescription = null, tint = Color(0xFFB8C7FF))
                    Text(category.uppercase(), color = Color(0xFFB8C7FF), style = MaterialTheme.typography.labelLarge)
                }
            }

            Text(
                text = station?.name ?: stringResource(R.string.player_no_station),
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = station?.city.orEmpty(),
                color = Color(0xFFB0B0BC),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            Waveform(
                isActive = isPlaying,
                modifier = Modifier.padding(top = 28.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.onSkipStation(-1) }, enabled = station != null) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.cd_previous_station), tint = Color.White, modifier = Modifier.size(36.dp))
                }
                PlayPauseButton(status = state.player.status, enabled = station != null, onClick = viewModel::onPlayPauseClick)
                IconButton(onClick = { viewModel.onSkipStation(1) }, enabled = station != null) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.cd_next_station), tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { viewModel.onFavoriteToggle() }, enabled = station != null) {
                    Icon(
                        imageVector = if (station?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (station?.isFavorite == true) MaterialTheme.colorScheme.secondary else Color.White,
                    )
                }
                IconButton(onClick = { showDelayDialog = true }, enabled = station != null) {
                    Icon(Icons.Filled.Timer, contentDescription = "Retardo del directo", tint = Color.White)
                }
                IconButton(onClick = { showSleepTimerDialog = true }) {
                    Icon(Icons.Filled.Bedtime, contentDescription = stringResource(R.string.player_sleep_timer), tint = Color.White)
                }
                IconButton(onClick = { viewModel.onStop() }, enabled = station != null) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.cd_stop), tint = Color.White)
                }
            }

            state.player.sleepTimerSecondsLeft?.let { seconds ->
                Text(
                    text = stringResource(R.string.player_sleep_timer_active, seconds / 60, seconds % 60),
                    color = Color(0xFFB0B0BC),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.player.playbackDelaySeconds > 0 && state.player.outputDevice != com.miradio.app.domain.model.OutputDevice.CAST) {
                Text(
                    text = "Retardo: ${state.player.playbackDelaySeconds} s",
                    color = Color(0xFFB0B0BC),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.player.errorMessage?.let { key ->
                Text(
                    text = resolveErrorMessage(key),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onConfirm = { minutes ->
                viewModel.startSleepTimer(minutes * 60)
                showSleepTimerDialog = false
            },
        )
    }

    if (showDelayDialog) {
        DelayDialog(
            currentSeconds = state.player.playbackDelaySeconds,
            onDismiss = { showDelayDialog = false },
            onConfirm = { seconds ->
                viewModel.setPlaybackDelaySeconds(seconds)
                showDelayDialog = false
            },
        )
    }
}

@Composable
private fun resolveErrorMessage(key: String): String {
    val resId = when (key) {
        "error_stream_unavailable" -> R.string.error_stream_unavailable
        "error_invalid_url" -> R.string.error_invalid_url
        "error_timeout" -> R.string.error_timeout
        "error_station_offline" -> R.string.error_station_offline
        "error_cast_unavailable" -> R.string.error_cast_unavailable
        else -> R.string.error_generic_playback
    }
    return stringResource(resId)
}
