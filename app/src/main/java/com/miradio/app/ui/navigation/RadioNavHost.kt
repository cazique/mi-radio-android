package com.miradio.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.miradio.app.ui.alarm.AlarmEditScreen
import com.miradio.app.ui.alarm.AlarmsScreen
import com.miradio.app.ui.home.HomeLandingScreen
import com.miradio.app.ui.home.HomeScreen
import com.miradio.app.ui.news.NewsScreen
import com.miradio.app.ui.player.PlayerScreen
import com.miradio.app.ui.podcast.PodcastEpisodesScreen
import com.miradio.app.ui.podcast.PodcastsScreen
import com.miradio.app.ui.settings.SettingsScreen
import com.miradio.app.ui.stations.StationEditScreen

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val FAVORITES = "favorites"
    const val NEWS = "news"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val STATION_ADD = "stations/add"
    const val STATION_EDIT = "stations/edit/{stationId}"
    const val ALARMS = "alarms"
    const val ALARM_ADD = "alarms/add"
    const val ALARM_EDIT = "alarms/edit/{alarmId}"
    const val PODCASTS = "podcasts"
    const val PODCAST_EPISODES = "podcasts/{collectionId}"

    fun stationEdit(id: String) = "stations/edit/$id"
    fun alarmEdit(id: Long) = "alarms/edit/$id"
    fun podcastEpisodes(collectionId: String) = "podcasts/$collectionId"
}

@Composable
fun RadioNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Fundido + pequeño deslizamiento en vez del cambio en seco de
        // antes (una pantalla sustituía a otra de golpe, sin transición).
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 } },
    ) {
        composable(Routes.HOME) {
            HomeLandingScreen(
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                onOpenExplore = { navController.navigate(Routes.EXPLORE) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onAddStation = { navController.navigate(Routes.STATION_ADD) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EXPLORE) {
            HomeScreen(
                showFavoritesOnly = false,
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                onAddStation = { navController.navigate(Routes.STATION_ADD) },
                onEditStation = { station -> navController.navigate(Routes.stationEdit(station.id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.FAVORITES) {
            HomeScreen(
                showFavoritesOnly = true,
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                onAddStation = { navController.navigate(Routes.STATION_ADD) },
                onEditStation = { station -> navController.navigate(Routes.stationEdit(station.id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.NEWS) {
            NewsScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.PLAYER,
            // El reproductor a pantalla completa se abre como una hoja
            // modal que sube desde abajo (como Spotify), en vez del mismo
            // fundido con deslizamiento lateral que el resto de pantallas.
            enterTransition = { slideInVertically(tween(280)) { fullHeight -> fullHeight } + fadeIn(tween(280)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutVertically(tween(240)) { fullHeight -> fullHeight } + fadeOut(tween(240)) },
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAlarms = { navController.navigate(Routes.ALARMS) },
            )
        }
        composable(Routes.STATION_ADD) {
            StationEditScreen(stationId = null, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.STATION_EDIT,
            arguments = listOf(navArgument("stationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val stationId = backStackEntry.arguments?.getString("stationId")
            StationEditScreen(stationId = stationId, onBack = { navController.popBackStack() })
        }
        composable(Routes.ALARMS) {
            AlarmsScreen(
                onBack = { navController.popBackStack() },
                onAddAlarm = { navController.navigate(Routes.ALARM_ADD) },
                onEditAlarm = { id -> navController.navigate(Routes.alarmEdit(id)) },
            )
        }
        composable(Routes.ALARM_ADD) {
            AlarmEditScreen(alarmId = null, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.ALARM_EDIT,
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val alarmId = backStackEntry.arguments?.getLong("alarmId")
            AlarmEditScreen(alarmId = alarmId, onBack = { navController.popBackStack() })
        }
        composable(Routes.PODCASTS) {
            PodcastsScreen(
                onOpenPodcast = { collectionId -> navController.navigate(Routes.podcastEpisodes(collectionId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.PODCAST_EPISODES,
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
            PodcastEpisodesScreen(
                collectionId = collectionId,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
            )
        }
    }
}
