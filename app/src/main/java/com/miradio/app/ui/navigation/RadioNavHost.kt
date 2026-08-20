package com.miradio.app.ui.navigation

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
    NavHost(navController = navController, startDestination = Routes.HOME) {
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
        composable(Routes.PLAYER) {
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
            PodcastEpisodesScreen(collectionId = collectionId, onBack = { navController.popBackStack() })
        }
    }
}
