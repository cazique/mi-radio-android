package com.miradio.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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

private val presetsMinutes = listOf(5, 15, 30, 45, 60)
private const val CUSTOM = -1

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    var selected by remember { mutableIntStateOf(15) }
    var customText by remember { mutableStateOf("") }
    val customMinutes = customText.toIntOrNull()
    val isCustomValid = customMinutes != null && customMinutes > 0
    val canConfirm = selected != CUSTOM || isCustomValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apagar la radio en…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetsMinutes.forEach { minutes ->
                        FilterChip(
                            selected = selected == minutes,
                            onClick = { selected = minutes },
                            label = { Text("$minutes min") },
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
                            customText = it.filter { c -> c.isDigit() }.take(4)
                            selected = CUSTOM
                        },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("min") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (selected == CUSTOM) customMinutes ?: 0 else selected) },
                enabled = canConfirm,
            ) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
