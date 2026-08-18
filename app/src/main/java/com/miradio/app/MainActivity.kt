package com.miradio.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miradio.app.domain.model.PlaybackStatus
import com.miradio.app.domain.model.PlayerUiState
import com.miradio.app.playback.PlaybackController
import com.miradio.app.playback.PlaybackServiceConnector
import com.miradio.app.ui.components.MiniPlayerBar
import com.miradio.app.ui.navigation.RadioBottomBar
import com.miradio.app.ui.navigation.RadioNavHost
import com.miradio.app.ui.navigation.Routes
import com.miradio.app.ui.navigation.bottomBarRoutes
import com.miradio.app.ui.theme.MiRadioTheme
import com.miradio.app.util.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// El botón de Cast (MediaRouteButton) exige que la Activity que lo aloja
// sea una FragmentActivity para poder mostrar su diálogo de selección de
// dispositivo; con ComponentActivity a secas la app cerraba al tocarlo.
class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: la notificación
            multimedia se seguirá mostrando aunque se deniegue, solo silenciosamente en segundo plano */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handlePlayDeepLink(intent)

        setContent {
            val container = (application as RadioApp).container
            val themeMode by container.preferencesRepository.themeMode.collectAsState(
                initial = com.miradio.app.domain.model.ThemeMode.SYSTEM,
            )
            // Escala de texto propia de Ajustes, independiente del tamaño de
            // letra del sistema: se sustituye por completo el fontScale de la
            // densidad de Compose en vez de multiplicarlo, para que no se
            // sume con el ajuste de accesibilidad de Android.
            val textScale by container.preferencesRepository.textScale.collectAsState(initial = 1f)
            val simpleMode by container.preferencesRepository.simpleMode.collectAsState(initial = false)
            val dynamicColor by container.preferencesRepository.dynamicColorEnabled.collectAsState(initial = false)
            val density = LocalDensity.current

            MiRadioTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = textScale),
                ) {
                    MiRadioApp(simpleMode = simpleMode)
                    CrashReportPrompt()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayDeepLink(intent)
    }

    /**
     * Enlace directo "miradio://play", usado por el acceso directo de App
     * Actions (Ajustes > acerca de en Assistant, "Ok Google, abre
     * reproducir en Mi Radio") para arrancar la última emisora escuchada,
     * o una favorita si no hay ninguna, sin pasar por la interfaz.
     */
    private fun handlePlayDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "miradio" || uri.host != "play") return
        DiagnosticsLog.log(this, "MainActivity", "App Action: reproducir vía $uri")

        val container = (application as RadioApp).container
        lifecycleScope.launch {
            runCatching {
                PlaybackController.ensureServiceStarted(this@MainActivity)
                val player = PlaybackController.awaitPlayer()
                if (player.uiState.value.station != null) {
                    player.play()
                    return@runCatching
                }
                val lastId = container.preferencesRepository.lastStationId.first()
                val stations = container.stationRepository.stations.first()
                val station = stations.find { it.id == lastId }
                    ?: stations.firstOrNull { it.isFavorite }
                    ?: stations.firstOrNull()
                station?.let {
                    player.playStation(it)
                    container.preferencesRepository.setLastStation(it.id)
                }
            }.onFailure { DiagnosticsLog.logThrowable(this@MainActivity, "MainActivity", "Fallo en el enlace directo de reproducir", it) }
        }
    }
}

/**
 * No hay servidor detrás de esta app, así que no existe un "enviar el
 * registro solo" de verdad sin construir una infraestructura que no hay.
 * Esto es lo más parecido y honesto: si la app se cerró de golpe hace
 * poco, se ofrece compartir el registro nada más volver a abrirla, en vez
 * de esperar a que alguien se acuerde de ir a Ajustes por su cuenta.
 */
@Composable
private fun CrashReportPrompt() {
    val context = LocalContext.current
    var showPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showPrompt = withContext(Dispatchers.IO) { DiagnosticsLog.hadRecentCrash(context) }
    }

    if (showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text("La app se cerró sola") },
            text = { Text("Parece que Radio Dari se cerró de forma inesperada hace un momento. ¿Quieres compartir el registro técnico para poder ver qué ha pasado?") },
            confirmButton = {
                Button(onClick = {
                    showPrompt = false
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(DiagnosticsLog.shareIntent(context), "Compartir registro"),
                        )
                    }.onFailure {
                        DiagnosticsLog.logThrowable(context, "MainActivity", "Fallo al compartir el registro tras un cierre", it)
                        Toast.makeText(context, "No se ha podido compartir el registro", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Compartir") }
            },
            dismissButton = {
                TextButton(onClick = { showPrompt = false }) { Text("No, gracias") }
            },
        )
    }
}

@Composable
private fun MiRadioApp(simpleMode: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in bottomBarRoutes

    // MiniPlayerBar ya no lee PlaybackServiceConnector por su cuenta (estado
    // "hoisted"): se observa aquí, en el único sitio donde vive, y se le
    // pasa ya resuelto. radioPlayer es null hasta que el servicio arranca
    // (p. ej. justo al abrir la app), de ahí el estado por defecto.
    val radioPlayer by PlaybackServiceConnector.player.collectAsState()
    val miniPlayerState by (radioPlayer?.uiState ?: remember { MutableStateFlow(PlayerUiState()) })
        .collectAsState()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // Franja de "ahora suena" fija encima de la barra de
                    // navegación, visible en Inicio/Favoritos/Explorar/Ajustes;
                    // se oculta ella sola si todavía no ha sonado nada.
                    MiniPlayerBar(
                        uiState = miniPlayerState,
                        onTogglePlayPause = {
                            val isActive = miniPlayerState.status == PlaybackStatus.PLAYING ||
                                miniPlayerState.status == PlaybackStatus.BUFFERING
                            if (isActive) radioPlayer?.pause() else radioPlayer?.play()
                        },
                        onClick = { navController.navigate(Routes.PLAYER) },
                    )
                    RadioBottomBar(navController, simpleMode = simpleMode)
                }
            }
        },
    ) { padding ->
        // fillMaxSize() es imprescindible aquí: sin él, este Box no tenía
        // garantizado ocupar toda el área que le reserva el Scaffold exterior,
        // lo que en algunas pantallas (p. ej. Ajustes, que a su vez tiene su
        // propio Scaffold interno) podía dejar la barra inferior mal medida
        // y sus botones de Inicio/Favoritos sin responder al toque.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp),
        ) {
            RadioNavHost(navController)
        }
    }
}
