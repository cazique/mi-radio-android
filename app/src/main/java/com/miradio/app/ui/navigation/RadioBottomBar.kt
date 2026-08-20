package com.miradio.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.miradio.app.R

private data class BottomTab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    BottomTab(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    BottomTab(Routes.FAVORITES, R.string.nav_favorites, Icons.Filled.Favorite),
    BottomTab(Routes.EXPLORE, R.string.nav_explore, Icons.Filled.Explore),
    BottomTab(Routes.NEWS, R.string.nav_news, Icons.Filled.Newspaper),
    BottomTab(Routes.PODCASTS, R.string.nav_podcasts, Icons.Filled.Mic),
    BottomTab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/** Solo se muestra en las pantallas de nivel superior (Inicio/Favoritos/Ajustes);
 *  la pantalla de reproducción y los formularios de emisora ocupan toda la pantalla. */
val bottomBarRoutes = tabs.map { it.route }.toSet()

/** Con el modo simple activo, la barra se reduce a Inicio/Ajustes: Favoritos
 *  y Explorar ya no son pestañas propias, Inicio pasa a ser el propio
 *  listado de favoritas en grande. */
private val simpleModeTabs = tabs.filter { it.route == Routes.HOME || it.route == Routes.SETTINGS }

@Composable
fun RadioBottomBar(navController: NavHostController, simpleMode: Boolean = false) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val visibleTabs = if (simpleMode) simpleModeTabs else tabs

    NavigationBar {
        visibleTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
