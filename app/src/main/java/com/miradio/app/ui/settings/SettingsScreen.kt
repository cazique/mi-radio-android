package com.miradio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.BuildConfig
import com.miradio.app.R
import com.miradio.app.domain.model.ThemeMode
import com.miradio.app.util.AppUpdater
import com.miradio.app.util.DiagnosticsLog
import com.miradio.app.util.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionLabel("Apariencia")

            Column {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                ThemeOption(ThemeMode.SYSTEM, R.string.settings_theme_system, state.themeMode, viewModel::onThemeModeChange)
                ThemeOption(ThemeMode.LIGHT, R.string.settings_theme_light, state.themeMode, viewModel::onThemeModeChange)
                ThemeOption(ThemeMode.DARK, R.string.settings_theme_dark, state.themeMode, viewModel::onThemeModeChange)
            }

            TextSizeSection(textScale = state.textScale, onTextScaleChange = viewModel::onTextScaleChange)

            Divider()
            SectionLabel("Catálogo")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_remote_catalog), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.remoteCatalogUrl,
                    onValueChange = viewModel::onRemoteUrlChange,
                    label = { Text(stringResource(R.string.settings_remote_catalog_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = viewModel::refreshRemoteCatalog, enabled = !state.isSyncing) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.settings_remote_catalog_refresh))
                }
                val lastSyncText = state.lastSyncMillis?.let {
                    DateFormat.getDateTimeInstance().format(Date(it))
                } ?: stringResource(R.string.settings_remote_catalog_never)
                Text(
                    text = stringResource(R.string.settings_remote_catalog_last_sync, lastSyncText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.syncMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.syncFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Divider()
            SectionLabel("Aplicación")

            Column {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            UpdateSection()

            DebugModeSection(enabled = state.debugMode, onEnabledChange = viewModel::onDebugModeChange)

            if (state.debugMode) {
                DiagnosticsSection()
            }
        }
    }
}

/** Cabecera de sección en mayúsculas, para agrupar visualmente ajustes
 *  relacionados (Apariencia / Catálogo / Aplicación) en vez de una lista
 *  larga sin estructura. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Descarga la última compilación publicada en GitHub y, si es más reciente
 * que la instalada, deja instalarla con un toque, sin salir de la app.
 * Android sigue exigiendo confirmar la instalación (y, la primera vez,
 * permitir "instalar apps de origen desconocido" para Mi Radio): eso no se
 * puede saltar sin un dispositivo rooteado.
 */
@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = {
                checking = true
                result = null
                scope.launch {
                    result = AppUpdater.checkForUpdate(context)
                    checking = false
                }
            },
            enabled = !checking,
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            }
            Text("Buscar actualizaciones")
        }
        when (val current = result) {
            is UpdateCheckResult.UpdateAvailable -> {
                Text(
                    text = "Versión ${current.versionName ?: current.versionCode} disponible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = { AppUpdater.installUpdate(context, current.apkFile) }) {
                    Text("Instalar ahora")
                }
            }
            is UpdateCheckResult.UpToDate -> Text(
                text = "Ya tienes la última versión (${BuildConfig.VERSION_NAME}).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is UpdateCheckResult.Failure -> Text(
                text = "No se ha podido comprobar: ${current.reason}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            null -> Unit
        }
    }
}

/**
 * Interruptor para mostrar u ocultar el bloque de "Diagnóstico" (registro
 * técnico interno, pensado para detectar fallos durante las pruebas). Para
 * un uso normal de la app, sobre todo pensada para gente que no está
 * familiarizada con términos técnicos, mejor mantenerlo oculto.
 */
@Composable
private fun DebugModeSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text("Modo depuración", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Muestra el registro técnico interno más abajo. Actívalo solo si vas a " +
                    "diagnosticar un problema; para el uso normal es mejor dejarlo apagado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

/**
 * Registro técnico local (nunca sale del teléfono salvo que el usuario lo
 * copie a mano): sirve para ver qué ha pasado justo antes de un cierre
 * inesperado sin necesidad de conectar el móvil a un ordenador.
 */
@Composable
private fun DiagnosticsSection() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var logText by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Diagnóstico", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Registro técnico local (emisora reproducida, errores de conexión y cierres inesperados). " +
                "Si la app se cierra sola, cópialo y compártelo para poder ver qué ha pasado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    logText = withContext(Dispatchers.IO) { DiagnosticsLog.readAll(context) }
                    copied = false
                }
            }) { Text("Ver registro") }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val text = logText.ifBlank { withContext(Dispatchers.IO) { DiagnosticsLog.readAll(context) } }
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    }
                },
            ) { Text(if (copied) "¡Copiado!" else "Copiar registro") }
        }
        if (logText.isNotBlank()) {
            Text(
                text = logText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * Tamaño de letra propio de la app, independiente del ajuste de accesibilidad
 * del sistema: se guarda como una escala (1f = normal) que se aplica a toda
 * la interfaz mediante la densidad de Compose (ver MainActivity).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextSizeSection(textScale: Float, onTextScaleChange: (Float) -> Unit) {
    val options = listOf(
        0.85f to "Pequeño",
        1f to "Normal",
        1.15f to "Grande",
        1.3f to "Muy grande",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tamaño del texto", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (scale, label) ->
                FilterChip(
                    selected = textScale == scale,
                    onClick = { onTextScaleChange(scale) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    labelRes: Int,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected == mode, onClick = { onSelect(mode) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
    }
}
