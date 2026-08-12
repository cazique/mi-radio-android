package com.miradio.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private val SectionPurple = Color(0xFF7C5CFC)
private val SectionBlue = Color(0xFF3B82F6)
private val SectionGreen = Color(0xFF2E9B5C)

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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(Icons.Filled.Accessibility, SectionGreen, "Modo simple")
                SimpleModeSection(enabled = state.simpleMode, onEnabledChange = viewModel::onSimpleModeChange)
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeader(Icons.Filled.Palette, SectionPurple, "Apariencia")
                ThemeCardPicker(selected = state.themeMode, onSelect = viewModel::onThemeModeChange)
                TextSizeSection(textScale = state.textScale, onTextScaleChange = viewModel::onTextScaleChange)
            }

            if (!state.simpleMode) {
                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(Icons.Filled.Sync, SectionPurple, "Catálogo")
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
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeader(Icons.Filled.Apps, SectionBlue, "Aplicación")

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
            }

            if (state.debugMode) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(Icons.Filled.Shield, SectionPurple, "Diagnóstico")
                    DiagnosticsSection()
                }
            }
        }
    }
}

/** Insignia circular de color + título, para agrupar visualmente ajustes
 *  relacionados en vez de una lista larga sin estructura. */
@Composable
private fun SectionHeader(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Selector de tema como tarjetas visuales (en vez de una lista de radio
 *  buttons): más fácil de reconocer de un vistazo, sobre todo para quien no
 *  está familiarizado con el vocabulario técnico de "modo claro/oscuro". */
@Composable
private fun ThemeCardPicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, Icons.Filled.BrightnessAuto, R.string.settings_theme_system),
        Triple(ThemeMode.LIGHT, Icons.Filled.LightMode, R.string.settings_theme_light),
        Triple(ThemeMode.DARK, Icons.Filled.DarkMode, R.string.settings_theme_dark),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (mode, icon, labelRes) ->
            val isSelected = selected == mode
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (isSelected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Descarga la última compilación publicada en GitHub y, si es más reciente
 * que la instalada, deja instalarla con un toque, sin salir de la app.
 * Android sigue exigiendo confirmar la instalación (y, la primera vez,
 * permitir "instalar apps de origen desconocido" para Radio Dari): eso no
 * se puede saltar sin un dispositivo rooteado.
 */
@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Buscar nuevas actualizaciones de la aplicación.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                checking = true
                result = null
                scope.launch {
                    result = AppUpdater.checkForUpdate(context)
                    checking = false
                    showResultDialog = true
                }
            },
            enabled = !checking,
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            }
            Text("Buscar actualizaciones")
        }
    }

    if (showResultDialog) {
        val current = result
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            icon = {
                val (icon, tint) = when (current) {
                    is UpdateCheckResult.UpdateAvailable -> Icons.Filled.CloudDownload to MaterialTheme.colorScheme.primary
                    is UpdateCheckResult.UpToDate -> Icons.Filled.CloudDone to MaterialTheme.colorScheme.primary
                    is UpdateCheckResult.Failure -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
                    null -> Icons.Filled.CloudDone to MaterialTheme.colorScheme.primary
                }
                Icon(icon, contentDescription = null, tint = tint)
            },
            title = {
                Text(
                    when (current) {
                        is UpdateCheckResult.UpdateAvailable -> "Hay una versión nueva"
                        is UpdateCheckResult.UpToDate -> "¡Tu aplicación está al día!"
                        is UpdateCheckResult.Failure -> "No se ha podido comprobar"
                        null -> ""
                    },
                )
            },
            text = {
                Text(
                    when (current) {
                        is UpdateCheckResult.UpdateAvailable -> "Versión ${current.versionName ?: current.versionCode} disponible."
                        is UpdateCheckResult.UpToDate -> "Versión ${BuildConfig.VERSION_NAME}."
                        is UpdateCheckResult.Failure -> current.reason
                        null -> ""
                    },
                )
            },
            confirmButton = {
                if (current is UpdateCheckResult.UpdateAvailable) {
                    Button(onClick = {
                        AppUpdater.installUpdate(context, current.apkFile)
                        showResultDialog = false
                    }) { Text("Instalar ahora") }
                } else {
                    Button(onClick = { showResultDialog = false }) { Text("Entendido") }
                }
            },
            dismissButton = {
                if (current is UpdateCheckResult.UpdateAvailable) {
                    TextButton(onClick = { showResultDialog = false }) { Text("Ahora no") }
                }
            },
        )
    }
}

/**
 * Interruptor del modo simple: reduce Inicio a "ahora suena" + favoritas en
 * tarjetas grandes, quita Explorar/Añadir emisora/Catálogo remoto de en
 * medio, y deja la barra inferior solo en Inicio/Ajustes. Pensado para
 * quien configura la app para un familiar mayor: se activa una vez, con
 * las favoritas ya elegidas, y el usuario final ya no ve nada más.
 */
@Composable
private fun SimpleModeSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(
                text = "Muestra solo las emisoras favoritas en tarjetas grandes y un botón de " +
                    "Reproducir/Pausa enorme. Se ocultan Explorar, Añadir emisora y el catálogo " +
                    "remoto. Ideal para dejar la app lista para alguien que solo quiere poner " +
                    "su emisora sin liarse con el resto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
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
