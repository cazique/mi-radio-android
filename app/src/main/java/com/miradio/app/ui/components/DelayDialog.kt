package com.miradio.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val presetsSeconds = listOf(0, 1, 2, 3, 4, 5, 10, 30)
private const val CUSTOM = -1

/** Retardo del directo, pensado para sincronizar la radio con la tele
 *  (p. ej. un partido de fútbol) sin que se adelante el comentario. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DelayDialog(currentSeconds: Int, onDismiss: () -> Unit, onConfirm: (seconds: Int) -> Unit) {
    val startsCustom = currentSeconds !in presetsSeconds
    var selected by remember { mutableIntStateOf(if (startsCustom) CUSTOM else currentSeconds) }
    var customText by remember { mutableStateOf(if (startsCustom) currentSeconds.toString() else "") }
    val customSeconds = customText.toIntOrNull()
    val isCustomValid = customSeconds != null && customSeconds in 0..300
    val canConfirm = selected != CUSTOM || isCustomValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retardo del directo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Aproximado: se consigue aumentando el búfer de reproducción, así " +
                        "que el desfase real puede variar un poco según la emisora y la red.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetsSeconds.forEach { seconds ->
                        FilterChip(
                            selected = selected == seconds,
                            onClick = { selected = seconds },
                            label = { Text(if (seconds == 0) "Directo" else "${seconds}s") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected == CUSTOM,
                        onClick = { selected = CUSTOM },
                        label = { Text("Personalizado") },
                    )
                    OutlinedTextField(
                        value = customText,
                        onValueChange = {
                            customText = it.filter { c -> c.isDigit() }.take(3)
                            selected = CUSTOM
                        },
                        modifier = Modifier.width(88.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("s") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (selected == CUSTOM) customSeconds ?: 0 else selected) },
                enabled = canConfirm,
            ) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
