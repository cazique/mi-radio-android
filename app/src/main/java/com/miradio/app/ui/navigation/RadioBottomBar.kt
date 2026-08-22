package com.miradio.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
)

/** Ajustes ya no vive aquí (a petición: solo se llega a él con el icono de
 *  engranaje que ya tienen todas estas pantallas arriba); se comporta como
 *  cualquier otra pantalla de detalle (Alarmas, editar emisora...), sin
 *  barra inferior propia, solo con su flecha de volver. */
val bottomBarRoutes = tabs.map { it.route }.toSet()

/** Con el modo simple activo, la barra se reduce solo a Inicio: Favoritos y
 *  Explorar ya no son pestañas propias (Inicio pasa a ser el propio listado
 *  de favoritas en grande), y Ajustes se alcanza con su icono de arriba,
 *  igual que en el modo normal. */
private val simpleModeTabs = tabs.filter { it.route == Routes.HOME }

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
                // Con 5 pestañas, "Favoritos" y "Podcasts" no cabían en una
                // línea con el tamaño de letra normal de la barra y se
                // partían en dos (se veía "Favorito" / "s"). labelSmall (más
                // pequeño que el label por defecto) más una sola línea que
                // recorta con puntos suspensivos si aun así no cupiera, en
                // vez de partir la palabra.
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                },
            )
        }
    }
}
