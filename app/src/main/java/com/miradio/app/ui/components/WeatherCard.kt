package com.miradio.app.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.domain.model.AemetSnapshot
import com.miradio.app.domain.model.DailyForecast
import com.miradio.app.domain.model.WeatherInfo
import com.miradio.app.ui.weather.AemetComparisonState
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
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            is WeatherUiState.Loaded -> {
                var expanded by remember { mutableStateOf(false) }
                val aemetState by viewModel.aemetState.collectAsStateWithLifecycle()
                LaunchedEffect(expanded) {
                    if (expanded) viewModel.loadAemetComparison()
                }
                Column(
                    modifier = Modifier.clickable { expanded = !expanded },
                ) {
                    WeatherLoadedContent(current.weather)
                    if (expanded) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        WeatherComparisonSection(openMeteo = current.weather, aemet = aemetState)
                    }
                }
            }
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

/**
 * Comparativa AEMET vs Open-Meteo, solo visible al desplegar la tarjeta.
 * Si AEMET no está configurado (sin clave o sin municipio elegido en
 * Ajustes > Clima), se explica cómo activarlo en vez de dejar un hueco
 * vacío sin más.
 */
@Composable
private fun WeatherComparisonSection(openMeteo: WeatherInfo, aemet: AemetComparisonState) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Comparar fuentes", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Open-Meteo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("${openMeteo.currentTempC.roundToInt()}°  ${weatherDescription(openMeteo.currentWeatherCode)}", style = MaterialTheme.typography.bodyMedium)
                openMeteo.currentRainProbabilityPercent?.let {
                    Text("Lluvia: $it %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Ahora mismo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("AEMET", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                when {
                    !aemet.configured -> Text(
                        "Sin configurar. Añade tu clave gratuita en Ajustes > Clima.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    aemet.isLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Consultando…", style = MaterialTheme.typography.bodySmall)
                    }
                    aemet.error != null -> Text(
                        aemet.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    aemet.snapshot != null -> AemetSnapshotColumn(aemet.snapshot)
                }
            }
        }
    }
}

@Composable
private fun AemetSnapshotColumn(snapshot: AemetSnapshot) {
    val tempText = snapshot.tempC?.let { "${it.roundToInt()}°" } ?: "—"
    val skyText = snapshot.skyDescription ?: ""
    Text("$tempText  $skyText".trim(), style = MaterialTheme.typography.bodyMedium)
    snapshot.rainProbabilityPercent?.let {
        Text("Lluvia: $it %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Hora ${snapshot.hourLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun dayLabel(isoDate: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(isoDate)
    SimpleDateFormat("EEE", Locale("es", "ES")).format(parsed!!).replaceFirstChar { it.uppercase() }
}.getOrDefault(isoDate)
