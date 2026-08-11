package com.miradio.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
            LazyColumn {
                items(presetsSeconds) { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == seconds, onClick = { selected = seconds })
                        Text(
                            text = if (seconds == 0) "Sin retardo (directo real)" else "$seconds segundos",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == CUSTOM, onClick = { selected = CUSTOM })
                        Text("Personalizado:", modifier = Modifier.padding(start = 8.dp, end = 8.dp))
                        OutlinedTextField(
                            value = customText,
                            onValueChange = {
                                customText = it.filter { c -> c.isDigit() }.take(3)
                                selected = CUSTOM
                            },
                            modifier = Modifier.padding(end = 4.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text("s") },
                        )
                    }
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
