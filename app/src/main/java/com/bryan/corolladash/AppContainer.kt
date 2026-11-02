package com.bryan.corolladash

import android.app.Application
import android.content.Context
import com.bryan.corolladash.calls.CallRepository
import com.bryan.corolladash.fuel.FuelRepository
import com.bryan.corolladash.fuel.Odometer
import com.bryan.corolladash.launcher.AppLauncher
import com.bryan.corolladash.lyrics.LyricsRepository
import com.bryan.corolladash.media.MediaSessionRepository
import com.bryan.corolladash.nav.NavRepository
import com.bryan.corolladash.settings.AppConfig
import com.bryan.corolladash.settings.SystemControls
import com.bryan.corolladash.speed.GpsSpeedRepository
import com.bryan.corolladash.speed.SpeedState
import com.bryan.corolladash.status.SystemStatusRepository
import com.bryan.corolladash.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

class CorollaDashApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Inyección manual de dependencias: sin frameworks, arranque rápido. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val config = AppConfig(app)
    val launcher = AppLauncher(app)
    val controls = SystemControls(app)
    val status = SystemStatusRepository(app)
    val media = MediaSessionRepository(app, config, scope)
    val gps = GpsSpeedRepository(app)
    val calls = CallRepository(app, config)
    val lyrics = LyricsRepository(app, config, media.state, scope)
    val odometer = Odometer(app)
    val fuel = FuelRepository(app)
    val weather = WeatherRepository()
    val nav = NavRepository(app)
    val updater = com.bryan.corolladash.update.Updater(app, scope)

    private val gpsRestart = MutableStateFlow(0)

    init {
        // Cuando el teléfono se conecta por Bluetooth, la sesión de música aparece después: releerla.
        scope.launch {
            status.phoneConnected.drop(1).collect {
                media.refresh()
                delay(2_000)
                media.refresh()
            }
        }
        // Buscar actualización en GitHub poco después de arrancar.
        scope.launch { delay(8_000); updater.check(force = true) }
    }

    /** Re-suscribe el GPS (al conceder permiso o al volver a primer plano). */
    fun restartGps() { gpsRestart.value++ }

    /** Velocidad real por GPS; null = sin dato. Se detiene 5 s después de que nadie la observe. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val speed: StateFlow<SpeedState> = gpsRestart
        .flatMapLatest { gps.observe() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SpeedState(permissionGranted = gps.hasPermission()))

    /** Todo lo que debe re-leerse cuando CorollaDash vuelve a primer plano. */
    fun onForeground() {
        status.refresh()
        media.refresh()
        restartGps()
        updater.check()
    }
}
