package com.miradio.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miradio.app.BuildConfig
import com.miradio.app.R
import com.miradio.app.domain.model.AemetMunicipio
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
                HideAddButtonSection(enabled = state.hideAddButton, onEnabledChange = viewModel::onHideAddButtonChange)
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeader(Icons.Filled.Palette, SectionPurple, "Apariencia")
                ThemeCardPicker(selected = state.themeMode, onSelect = viewModel::onThemeModeChange)
                TextSizeSection(textScale = state.textScale, onTextScaleChange = viewModel::onTextScaleChange)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    DynamicColorSection(enabled = state.dynamicColor, onEnabledChange = viewModel::onDynamicColorChange)
                }
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

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(Icons.Filled.Newspaper, SectionPurple, "Noticias")
                    NewsAutoRefreshSection(enabled = state.newsAutoRefresh, onEnabledChange = viewModel::onNewsAutoRefreshChange)
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(Icons.Filled.Cloud, SectionPurple, "Clima")
                    AemetSection(
                        apiKey = state.aemetApiKey,
                        municipioName = state.aemetMunicipioName,
                        searchState = viewModel.aemetSearch.collectAsState().value,
                        onApiKeySave = viewModel::onAemetApiKeySave,
                        onDisable = viewModel::onAemetDisable,
                        onQueryChange = viewModel::onAemetMunicipioQuery,
                        onMunicipioSelect = viewModel::onAemetMunicipioSelect,
                    )
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

                ChangelogSection()

                NotificationSettingsSection()

                ClearCacheSection()

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
 * Historial de cambios en español sencillo (ver [com.miradio.app.util.Changelog]):
 * qué ha cambiado para quien usa la app, no una lista de commits técnicos.
 * Se mantiene a mano (una entrada por cada cambio que de verdad se nota),
 * así que hay que acordarse de añadir una entrada nueva cuando toque.
 */
@Composable
private fun ChangelogSection() {
    var showDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Historial de cambios", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Ver qué ha cambiado en cada versión, explicado sin tecnicismos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { showDialog = true }) {
            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Ver historial de cambios")
        }
    }
    if (showDialog) {
        ChangelogDialog(onDismiss = { showDialog = false })
    }
}

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novedades") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                com.miradio.app.util.Changelog.entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall)
                        entry.bullets.forEach { bullet ->
                            Text(
                                text = "•  $bullet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

/**
 * Si el permiso de notificaciones está desactivado, Android no puede
 * mostrar la emisora que suena ni los controles de pausa en la barra de
 * notificaciones (sustituye la notificación real por un aviso genérico del
 * sistema): se explica aquí, con acceso directo a activarlo, en vez de
 * dejarlo como un misterio.
 */
@Composable
private fun NotificationSettingsSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Notificaciones", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (enabled) {
                "Activadas: se muestra la emisora que suena y los controles de pausa en la notificación."
            } else {
                "Desactivadas: Android no puede mostrar la emisora ni los controles mientras suena."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        if (!enabled) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) {
                Icon(Icons.Filled.NotificationsOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Activar notificaciones")
            }
        } else {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Borra los archivos temporales propios (APK de actualizaciones ya
 *  instaladas, etc.): no afecta a favoritos, emisoras ni ajustes, que viven
 *  en almacenamiento aparte y nunca se tocan aquí. */
@Composable
private fun ClearCacheSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSizeLabel by remember { mutableStateOf(formatCacheSize(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Almacenamiento", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Archivos temporales (actualizaciones descargadas, imágenes en caché): $cacheSizeLabel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { context.cacheDir?.deleteRecursively() }
                cacheSizeLabel = formatCacheSize(context)
                Toast.makeText(context, "Caché borrada", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Borrar caché")
        }
    }
}

private fun formatCacheSize(context: android.content.Context): String {
    val bytes = context.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
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
        // OJO: weight(1f) es imprescindible aquí. Sin él, un Text que
        // necesita varias líneas mide su ancho como el máximo disponible en
        // el Row entero (no se "encoge" a lo que realmente ocupa), dejando
        // al Switch sin sitio y empujándolo fuera de la pantalla por la
        // derecha. Con weight(1f) el texto se queda con el ancho restante
        // tras reservarle sitio de verdad al Switch.
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
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
 * Con esto activo, la pestaña Noticias se refresca sola de vez en cuando
 * mientras se tiene abierta, sin tener que tirar hacia abajo a mano cada
 * vez para ver si hay algo nuevo.
 */
@Composable
private fun NewsAutoRefreshSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text("Actualizar noticias automáticamente", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Refresca la pestaña Noticias sola de vez en cuando mientras la tienes abierta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

/**
 * AEMET OpenData es opcional y de cada usuario: se pide la clave gratuita
 * en aemet.es y se pega aquí. Nunca se manda a ningún sitio nuestro ni va
 * en el APK — solo se guarda en este dispositivo y se usa directamente
 * contra la API de AEMET. Sin clave (el caso por defecto), el tiempo sigue
 * funcionando con Open-Meteo tal cual.
 */
@Composable
private fun AemetSection(
    apiKey: String,
    municipioName: String?,
    searchState: AemetSearchState,
    onApiKeySave: (String) -> Unit,
    onDisable: () -> Unit,
    onQueryChange: (String) -> Unit,
    onMunicipioSelect: (AemetMunicipio) -> Unit,
) {
    var editableKey by remember(apiKey) { mutableStateOf(apiKey) }
    var query by remember { mutableStateOf("") }

    Text(
        text = "Usa AEMET (además de Open-Meteo) para comparar el tiempo oficial de España. " +
            "Necesita una clave gratuita de aemet.es/opendata; se guarda solo en este móvil.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = editableKey,
        onValueChange = { editableKey = it },
        label = { Text("Clave de AEMET OpenData") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onApiKeySave(editableKey) }, enabled = editableKey.isNotBlank()) { Text("Guardar clave") }
        if (apiKey.isNotBlank()) {
            OutlinedButton(onClick = onDisable) { Text("Desactivar AEMET") }
        }
    }

    if (apiKey.isNotBlank()) {
        Text(
            text = municipioName?.let { "Municipio: $it" } ?: "Todavía no has elegido municipio.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            label = { Text("Buscar tu municipio") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (searchState.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Descargando listado de municipios (la primera vez tarda un poco)…", style = MaterialTheme.typography.bodySmall)
            }
        }
        searchState.error?.let { error ->
            Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        searchState.results.forEach { municipio ->
            Text(
                text = municipio.nombre,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        query = ""
                        onMunicipioSelect(municipio)
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * Colores del sistema (Material You, Android 12+): sustituye la paleta de
 * marca (azul/naranja/crema) por una generada a partir del fondo de
 * pantalla del usuario. Desactivado por defecto para que la app se vea
 * siempre igual con su propia identidad; solo se ofrece a quien lo prefiera.
 */
@Composable
private fun DynamicColorSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text("Usar colores del sistema (Material You)", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Colorea la app a partir del fondo de pantalla del móvil en vez de usar " +
                    "los colores propios de Radio Dari.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

/**
 * Oculta solo el botón "+ Añadir emisora" (en Inicio y en Explorar), sin
 * activar el resto del modo simple: para quien quiere la app normal pero
 * sin que nadie pueda tocar "añadir" por accidente.
 */
@Composable
private fun HideAddButtonSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = "Ocultar el botón de añadir emisora",
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
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
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
@OptIn(ExperimentalLayoutApi::class)
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
        // FlowRow en vez de Row: con 3 botones no siempre caben todos en una
        // sola línea en pantallas estrechas; así el que sobra pasa a la
        // siguiente línea en vez de salirse por el borde de la pantalla.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(DiagnosticsLog.shareIntent(context), "Compartir registro"),
                        )
                    }.onFailure {
                        DiagnosticsLog.logThrowable(context, "SettingsScreen", "Fallo al compartir el registro", it)
                        Toast.makeText(context, "No se ha podido compartir el registro", Toast.LENGTH_SHORT).show()
                    }
                },
            ) { Text("Compartir registro") }
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
