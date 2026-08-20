package com.miradio.app

import android.app.Application
import com.miradio.app.data.database.AppDatabase
import com.miradio.app.data.remote.RemoteStationsService
import com.miradio.app.data.repository.AlarmRepository
import com.miradio.app.data.repository.NewsRepository
import com.miradio.app.data.repository.PodcastRepository
import com.miradio.app.data.repository.PreferencesRepository
import com.miradio.app.data.repository.StationRepository
import com.miradio.app.domain.usecase.AddStationUseCase
import com.miradio.app.domain.usecase.DeleteStationUseCase
import com.miradio.app.domain.usecase.RefreshRemoteStationsUseCase
import com.miradio.app.domain.usecase.ToggleFavoriteUseCase
import com.miradio.app.domain.usecase.UpdateStationUseCase
import com.miradio.app.domain.usecase.ValidateStreamUrlUseCase
import com.miradio.app.util.AemetService
import com.miradio.app.util.DiagnosticsLog
import com.miradio.app.util.UpdateChecker
import com.miradio.app.util.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor manual de dependencias. Con el tamaño de esta app no hace
 * falta Hilt/Dagger: todo se instancia una vez aquí y las pantallas piden
 * lo que necesitan a través de [AppContainer].
 */
class AppContainer(app: Application) {
    private val database by lazy { AppDatabase.getInstance(app) }

    val preferencesRepository by lazy { PreferencesRepository(app) }
    val stationRepository by lazy {
        StationRepository(app, database.stationDao(), RemoteStationsService())
    }

    val validateStreamUrlUseCase by lazy { ValidateStreamUrlUseCase() }
    val addStationUseCase by lazy { AddStationUseCase(stationRepository) }
    val updateStationUseCase by lazy { UpdateStationUseCase(stationRepository) }
    val deleteStationUseCase by lazy { DeleteStationUseCase(stationRepository) }
    val toggleFavoriteUseCase by lazy { ToggleFavoriteUseCase(stationRepository) }
    val refreshRemoteStationsUseCase by lazy {
        RefreshRemoteStationsUseCase(stationRepository, preferencesRepository)
    }
    val newsRepository by lazy { NewsRepository(app) }
    val podcastRepository by lazy { PodcastRepository(app) }
    val weatherService by lazy { WeatherService() }
    val aemetService by lazy { AemetService() }
    val alarmRepository by lazy { AlarmRepository(app, database.alarmDao()) }
}

class RadioApp : Application() {
    lateinit var container: AppContainer
        private set

    // Vive mientras dura el proceso: sirve para comprobaciones que deben
    // seguir corriendo aunque la Activity se recree (giro de pantalla) o el
    // usuario navegue entre pantallas, como el aviso de actualización.
    private val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.installUncaughtExceptionLogger(this)
        DiagnosticsLog.log(this, "App", "onCreate versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        container = AppContainer(this)
        UpdateChecker.start(this, appScope)
    }
}
