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
    val messages = com.bryan.corolladash.messages.MessageRepository(app, scope)
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
            status.phoneConnected.drop(1).collect { connected ->
                media.refresh()
                delay(2_000)
                media.refresh()
                // Recién conectado y sin reproductor: despertar Música BT para que play funcione a la primera.
                if (connected) {
                    delay(2_000)
                    if (!media.state.value.hasSession) wakeBtMusic(thenPlay = false)
                }
            }
        }
        // Buscar actualización en GitHub poco después de arrancar.
        scope.launch { delay(8_000); updater.check(force = true) }
    }

    /** CorollaDash está en pantalla (lo marca MainActivity). */
    @Volatile var inForeground = false
    private var waking = false

    /**
     * La música por Bluetooth solo existe cuando la app Música BT de la radio se abrió al menos una vez.
     * 1) Intento silencioso: tecla multimedia dirigida a esa app.
     * 2) Si no aparece el reproductor: abrirla un instante y volver solos a CorollaDash.
     */
    fun wakeBtMusic(thenPlay: Boolean) {
        if (waking) return
        val pkg = config.packageOf(com.bryan.corolladash.settings.ExternalApp.MUSIC_BT)
        if (pkg.isBlank()) {
            if (thenPlay) media.playPause()
            return
        }
        waking = true
        scope.launch {
            try {
                if (thenPlay) media.sendMediaButtonTo(pkg, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                repeat(4) {
                    delay(500)
                    media.refresh()
                    if (media.state.value.hasSession) return@launch
                }
                if (!inForeground || !launcher.launch(pkg)) return@launch
                delay(1_800)
                runCatching {
                    app.startActivity(
                        android.content.Intent(app, MainActivity::class.java).addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                    )
                }
                for (i in 0 until 8) {
                    delay(500)
                    media.refresh()
                    if (media.state.value.hasSession) break
                }
                val st = media.state.value
                if (thenPlay && st.hasSession && !st.isPlaying) media.playPause()
            } finally {
                waking = false
            }
        }
    }

    /** Play desde CorollaDash: si aún no hay reproductor Bluetooth, despertarlo primero. */
    fun playPause() {
        if (media.state.value.hasSession) media.playPause() else wakeBtMusic(thenPlay = true)
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
