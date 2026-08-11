package com.miradio.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miradio.app.ui.navigation.RadioBottomBar
import com.miradio.app.ui.navigation.RadioNavHost
import com.miradio.app.ui.navigation.bottomBarRoutes
import com.miradio.app.ui.theme.MiRadioTheme

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

        setContent {
            val container = (application as RadioApp).container
            val themeMode by container.preferencesRepository.themeMode.collectAsState(
                initial = com.miradio.app.domain.model.ThemeMode.SYSTEM,
            )

            MiRadioTheme(themeMode = themeMode) {
                MiRadioApp()
            }
        }
    }
}

@Composable
private fun MiRadioApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = { if (showBottomBar) RadioBottomBar(navController) },
    ) { padding ->
        Box(modifier = Modifier.padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp)) {
            RadioNavHost(navController)
        }
    }
}
