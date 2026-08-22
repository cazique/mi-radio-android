package com.miradio.app.ui.alarm

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.domain.model.RadioStation
import com.miradio.app.ui.components.StationLogo

/**
 * Crear/editar una alarma: hora con el selector de Material 3, chips de
 * días de la semana, buscador de emisora (favoritas primero, como en el
 * resto de la app) y volumen. Todo con textos y controles grandes, mismo
 * criterio de accesibilidad que el resto de Radio Dari.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Long?,
    onBack: () -> Unit,
    viewModel: AlarmEditViewModel = viewModel(factory = AlarmEditViewModel.factory(alarmId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stationResults by viewModel.stationResults.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var stationQuery by remember { mutableStateOf("") }
    var showExactAlarmExplainer by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    if (!state.isLoaded) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Editar alarma") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {}
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Editar alarma" else "Nueva alarma") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar alarma")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val timePickerState = rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute, is24Hour = true)
        LaunchedEffect(timePickerState.hour, timePickerState.minute) {
            viewModel.onTimeChange(timePickerState.hour, timePickerState.minute)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth())

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Repetir", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK_DAYS_ES.forEach { (label, day) ->
                        FilterChip(
                            selected = day in state.repeatDays,
                            onClick = { viewModel.onDayToggle(day) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Emisora", style = MaterialTheme.typography.titleMedium)
                state.station?.let { station ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StationLogo(logoUrl = station.logoUrl, modifier = Modifier.size(40.dp), cornerRadius = 10)
                        Text(station.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(
                    value = stationQuery,
                    onValueChange = {
                        stationQuery = it
                        viewModel.onStationQueryChange(it)
                    },
                    label = { Text("Buscar emisora") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // heightIn(max = ...) es imprescindible aquí: esta lista vive
                // dentro de un Column con verticalScroll(), que le da altura
                // infinita a sus hijos; un LazyColumn sin límite de altura
                // propio lanza IllegalStateException al medirse. Con un
                // máximo fijo, esta lista concreta scrollea por su cuenta.
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(stationResults, key = { it.id }) { station ->
                        StationPickRow(station = station, onClick = { viewModel.onStationSelect(station) })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Subir el volumen poco a poco", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = state.volumeIncreasing, onCheckedChange = viewModel::onVolumeIncreasingChange)
                }
                if (state.volumeIncreasing) {
                    Text(
                        "Empieza flojito (${(state.startVolume * 100).toInt()} %) y sube hasta el máximo en ${state.fadeInSeconds} s.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = state.startVolume,
                        onValueChange = viewModel::onStartVolumeChange,
                        valueRange = 0f..0.8f,
                    )
                }
            }

            Button(
                onClick = {
                    val alarmManager = context.getSystemService<AlarmManager>()
                    val needsExactAlarmPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        alarmManager?.canScheduleExactAlarms() == false
                    if (needsExactAlarmPermission) {
                        showExactAlarmExplainer = true
                    } else {
                        viewModel.save()
                    }
                },
                enabled = state.station != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar alarma", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showExactAlarmExplainer) {
        AlertDialog(
            onDismissRequest = { showExactAlarmExplainer = false },
            title = { Text("Permiso de alarmas exactas") },
            text = {
                Text(
                    "Para que la alarma suene justo a la hora elegida (incluso con el móvil en reposo), " +
                        "Android pide activar \"Alarmas y recordatorios\" para Radio Dari en Ajustes del sistema.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showExactAlarmExplainer = false
                    runCatching {
                        exactAlarmSettingsLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    }
                }) { Text("Abrir ajustes") }
            },
            dismissButton = {
                Button(onClick = {
                    showExactAlarmExplainer = false
                    viewModel.save()
                }) { Text("Guardar de todos modos") }
            },
        )
    }
}

@Composable
private fun StationPickRow(station: RadioStation, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StationLogo(logoUrl = station.logoUrl, modifier = Modifier.size(36.dp).clip(CircleShape), cornerRadius = 18)
            Text(station.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
