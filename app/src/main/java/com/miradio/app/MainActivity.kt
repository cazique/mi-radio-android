package com.miradio.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miradio.app.playback.PlaybackController
import com.miradio.app.ui.components.MiniPlayerBar
import com.miradio.app.ui.navigation.RadioBottomBar
import com.miradio.app.ui.navigation.RadioNavHost
import com.miradio.app.ui.navigation.Routes
import com.miradio.app.ui.navigation.bottomBarRoutes
import com.miradio.app.ui.theme.MiRadioTheme
import com.miradio.app.util.DiagnosticsLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
            val density = LocalDensity.current

            MiRadioTheme(themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = textScale),
                ) {
                    MiRadioApp(simpleMode = simpleMode)
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
            PlaybackController.ensureServiceStarted(this@MainActivity)
            val player = PlaybackController.awaitPlayer()
            if (player.uiState.value.station != null) {
                player.play()
                return@launch
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
        }
    }
}

@Composable
private fun MiRadioApp(simpleMode: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // Franja de "ahora suena" fija encima de la barra de
                    // navegación, visible en Inicio/Favoritos/Explorar/Ajustes;
                    // se oculta ella sola si todavía no ha sonado nada.
                    MiniPlayerBar(onClick = { navController.navigate(Routes.PLAYER) })
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
