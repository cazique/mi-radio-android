package com.miradio.app.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.domain.model.DailyForecast
import com.miradio.app.domain.model.WeatherInfo
import com.miradio.app.ui.weather.WeatherUiState
import com.miradio.app.ui.weather.WeatherViewModel
import com.miradio.app.util.weatherDescription
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tiempo en la ubicación actual + previsión de los próximos días, en Inicio.
 * Pide el permiso de ubicación aproximada solo cuando el usuario toca
 * "Activar" (no al abrir la app), y si lo deniega deja un botón para
 * reintentar en vez de ocupar sitio con un error permanente.
 */
@Composable
fun WeatherCard(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        when (val current = state) {
            is WeatherUiState.NeedsPermission -> WeatherPermissionRequest(
                deniedBefore = current.deniedBefore,
                onRequest = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            )
            WeatherUiState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Consultando el tiempo…", style = MaterialTheme.typography.bodyMedium)
            }
            is WeatherUiState.Error -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "No se ha podido consultar el tiempo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::retry) { Text("Reintentar") }
            }
            is WeatherUiState.Loaded -> WeatherLoadedContent(current.weather)
        }
    }
}

@Composable
private fun WeatherPermissionRequest(deniedBefore: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Ver el tiempo aquí", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (deniedBefore) {
                    "Sin permiso de ubicación no se puede mostrar el tiempo local. Puedes activarlo también desde Ajustes del sistema."
                } else {
                    "Usa tu ubicación aproximada para mostrar el tiempo actual y los próximos días."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRequest) { Text("Activar") }
    }
}

@Composable
private fun WeatherLoadedContent(weather: WeatherInfo) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = weatherIcon(weather.currentWeatherCode, weather.isDay),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${weather.currentTempC.roundToInt()}°",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = weatherDescription(weather.currentWeatherCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (!weather.locationName.isNullOrBlank()) {
                    Text(
                        text = weather.locationName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (weather.daily.size > 1) {
            LazyRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Se salta el primer elemento (hoy, ya mostrado arriba en grande).
                items(weather.daily.drop(1)) { day -> DailyForecastItem(day) }
            }
        }
    }
}

@Composable
private fun DailyForecastItem(day: DailyForecast) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = dayLabel(day.date), style = MaterialTheme.typography.labelMedium)
        Icon(
            imageVector = weatherIcon(day.weatherCode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp).size(22.dp),
        )
        Text(text = "${day.maxTempC.roundToInt()}°", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "${day.minTempC.roundToInt()}°",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dayLabel(isoDate: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(isoDate)
    SimpleDateFormat("EEE", Locale("es", "ES")).format(parsed!!).replaceFirstChar { it.uppercase() }
}.getOrDefault(isoDate)
