package com.miradio.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val presetsMinutes = listOf(5, 15, 30, 45, 60)

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    var selected by remember { mutableIntStateOf(15) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apagar la radio en…") },
        text = {
            LazyColumn {
                items(presetsMinutes) { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == minutes, onClick = { selected = minutes })
                        Text("$minutes minutos", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
