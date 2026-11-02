package com.bryan.corolladash.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryan.corolladash.AppContainer
import com.bryan.corolladash.BuildConfig
import com.bryan.corolladash.launcher.AppEntry
import com.bryan.corolladash.media.MediaListenerService
import com.bryan.corolladash.fuel.OdometerService
import com.bryan.corolladash.nav.GeoPoint
import com.bryan.corolladash.nav.Place
import com.bryan.corolladash.settings.ExternalApp
import com.bryan.corolladash.weather.Weather
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.PillButton
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.components.clockStrings
import com.bryan.corolladash.ui.components.rememberNowMillis
import com.bryan.corolladash.ui.components.tight
import com.bryan.corolladash.ui.screens.AppsScreen
import com.bryan.corolladash.ui.screens.CallBanner
import com.bryan.corolladash.ui.screens.DiagSection
import com.bryan.corolladash.ui.screens.FuelScreen
import com.bryan.corolladash.ui.screens.FullMapScreen
import com.bryan.corolladash.ui.screens.HomeActions
import com.bryan.corolladash.ui.screens.HomePage
import com.bryan.corolladash.ui.screens.MapActions
import com.bryan.corolladash.ui.screens.HomeScreen
import com.bryan.corolladash.ui.screens.MusicActions
import com.bryan.corolladash.ui.screens.MusicScreen
import com.bryan.corolladash.ui.screens.QuickActions
import com.bryan.corolladash.ui.screens.QuickSettingsPanel
import com.bryan.corolladash.ui.screens.RestMode
import com.bryan.corolladash.ui.screens.RestScreen
import com.bryan.corolladash.ui.screens.SettingsActions
import com.bryan.corolladash.ui.screens.SettingsPage
import com.bryan.corolladash.ui.screens.SettingsScreen
import com.bryan.corolladash.ui.screens.SettingsUiState
import com.bryan.corolladash.ui.screens.SystemTile
import com.bryan.corolladash.ui.screens.parent
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Secciones de la barra lateral. Android Auto no es sección: abre la app externa. */
enum class Section { HOME, MUSIC, APPS, SETTINGS, FUEL, MAP }

/** Velocidad GPS real a partir de la cual se bloquean cambios de configuración. */
private const val LOCK_SPEED_KMH = 8
/** Tras confirmar "vehículo detenido" sin GPS, no volver a preguntar durante 5 min. */
private const val CONFIRM_VALID_MS = 5 * 60_000L

@Composable
fun DashboardApp(c: AppContainer, section: MutableState<Section>, resumeTick: Int) {
    val ctx = LocalContext.current
    val speed by c.speed.collectAsStateWithLifecycle()
    val media by c.media.state.collectAsStateWithLifecycle()
    val packages by c.config.packages.collectAsStateWithLifecycle()
    val night by c.config.nightDim.collectAsStateWithLifecycle()
    val radioLabel by c.config.radioLabel.collectAsStateWithLifecycle()
    val otherFallback by c.config.otherMediaFallback.collectAsStateWithLifecycle()
    val btOn by c.status.bluetoothOn.collectAsStateWithLifecycle()
    val phoneConnected by c.status.phoneConnected.collectAsStateWithLifecycle()
    val wifiOn by c.status.wifiConnected.collectAsStateWithLifecycle()
    val autoRest by c.config.autoRestMinutes.collectAsStateWithLifecycle()
    val call by c.calls.state.collectAsStateWithLifecycle()
    val lyrics by c.lyrics.state.collectAsStateWithLifecycle()
    val lyricsEnabled by c.config.lyricsEnabled.collectAsStateWithLifecycle()
    val onlineArt by c.config.onlineArt.collectAsStateWithLifecycle()
    val weather by c.weather.state.collectAsStateWithLifecycle()
    val odometerKm by c.odometer.km.collectAsStateWithLifecycle()
    val fills by c.fuel.fills.collectAsStateWithLifecycle()
    val phoneName by c.status.phoneName.collectAsStateWithLifecycle()
    val destination by c.nav.destination.collectAsStateWithLifecycle()
    val route by c.nav.route.collectAsStateWithLifecycle()
    val homePlace by c.nav.home.collectAsStateWithLifecycle()
    val workPlace by c.nav.work.collectAsStateWithLifecycle()
    val savedPlaces by c.nav.favorites.collectAsStateWithLifecycle()

    // Vistas del Inicio: completa (mapa) · limpia · letra (si está activada). Se recuerda completa/limpia.
    val homePages = remember(lyricsEnabled) {
        if (lyricsEnabled) listOf(HomePage.FULL, HomePage.CLEAN, HomePage.LYRICS) else listOf(HomePage.FULL, HomePage.CLEAN)
    }
    var homePage by remember { mutableStateOf(if (c.config.homePage == 1) HomePage.CLEAN else HomePage.FULL) }
    if (homePage !in homePages) homePage = HomePage.CLEAN
    fun setHomePage(p: HomePage) {
        homePage = p
        if (p != HomePage.LYRICS) c.config.homePage = p.ordinal
    }
    val lyricsPageOpen = section.value == Section.HOME && homePage == HomePage.LYRICS

    var settingsPage by remember { mutableStateOf<SettingsPage>(SettingsPage.Root) }
    var quickOpen by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmedUntil by remember { mutableLongStateOf(0L) }
    var toast by remember { mutableStateOf<String?>(null) }
    var rest by remember { mutableStateOf<RestMode?>(null) }
    /** Último toque en pantalla (no es estado de Compose para no recomponer en cada movimiento). */
    val lastTouch = remember { longArrayOf(SystemClock.elapsedRealtime()) }

    // Apps instaladas: lista en caché; se precarga unos segundos después del arranque (sin frenar el inicio)
    // y solo se recarga si Android avisa que cambiaron las apps.
    val apps: List<AppEntry>? by c.launcher.apps.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        delay(4_000)
        c.launcher.ensureLoaded()
    }
    LaunchedEffect(resumeTick, section.value) {
        if (section.value == Section.APPS || section.value == Section.SETTINGS) c.launcher.ensureLoaded()
    }
    // Nombres de las apps del sistema: solo se recalculan si cambia la configuración.
    val packageLabels = remember(packages, apps) { ExternalApp.entries.associateWith { c.launcher.labelOf(packages[it]) } }

    // ---------------------------------------------------------------- Permisos
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val btPerms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    val locPerms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    var permTick by remember { mutableIntStateOf(0) }
    val hasLocation = remember(resumeTick, permTick) { granted(Manifest.permission.ACCESS_FINE_LOCATION) }
    val hasBtPerm = remember(resumeTick, permTick) { btPerms.all { granted(it) } }
    val canWrite = remember(resumeTick) { c.controls.canWriteSettings() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        if (c.gps.hasPermission()) OdometerService.start(ctx)
        c.restartGps()
        c.status.refresh()
    }
    // Primera vez: pedir ubicación (velocidad) y dispositivos cercanos (estado Bluetooth) juntos.
    LaunchedEffect(Unit) {
        if (!c.config.askedStartupPermissions) {
            c.config.askedStartupPermissions = true
            val missing = (locPerms + btPerms).filterNot { granted(it) }
            if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
        }
    }

    // ---------------------------------------------------------------- Clima
    val latKey = speed.latitude?.let { (it * 10).roundToInt() }
    val lonKey = speed.longitude?.let { (it * 10).roundToInt() }
    LaunchedEffect(latKey, lonKey) {
        val lat = speed.latitude ?: return@LaunchedEffect
        val lon = speed.longitude ?: return@LaunchedEffect
        while (true) {
            c.weather.refresh(lat, lon)
            // Sin dato (sin internet): reintentar en 2 min; con dato, cada 15 min.
            delay(if (c.weather.state.value == null) 120_000L else 15 * 60_000L)
        }
    }

    // ---------------------------------------------------------------- Ruta al destino
    // Se recalcula al elegir destino y cada ~400 m recorridos (la distancia/tiempo se mantienen al día).
    val routeKey = speed.latitude?.let { la -> speed.longitude?.let { lo -> (la * 250).roundToInt() to (lo * 250).roundToInt() } }
    LaunchedEffect(destination, routeKey) {
        val la = speed.latitude ?: return@LaunchedEffect
        val lo = speed.longitude ?: return@LaunchedEffect
        if (destination != null) c.nav.updateRoute(GeoPoint(la, lo))
    }

    // ---------------------------------------------------------------- Seguridad al conducir
    val moving = (speed.speedKmh ?: 0) >= LOCK_SPEED_KMH

    // ---------------------------------------------------------------- Reposo
    fun startRest(mode: RestMode) {
        quickOpen = false
        rest = mode
    }
    fun wake() {
        lastTouch[0] = SystemClock.elapsedRealtime()
        rest = null
    }
    // Al volver de otra app se reinicia el temporizador y se sale del reposo.
    LaunchedEffect(resumeTick) { wake() }
    // Una llamada entrante siempre despierta la pantalla.
    LaunchedEffect(call?.key, call?.incoming) { if (call?.incoming == true) wake() }
    // Reposo automático: N minutos sin tocar la pantalla y con el vehículo detenido.
    val movingNow by rememberUpdatedState(moving)
    LaunchedEffect(autoRest, rest) {
        if (autoRest <= 0 || rest != null) return@LaunchedEffect
        val limit = autoRest * 60_000L
        while (true) {
            delay(10_000)
            val idle = SystemClock.elapsedRealtime() - lastTouch[0]
            if (idle >= limit && !movingNow && c.calls.state.value == null) {
                rest = RestMode.CLOCK
                break
            }
        }
    }

    /** Cambios de configuración: bloqueados en movimiento; sin GPS se pide confirmar que está detenido. */
    fun guard(action: () -> Unit) {
        when {
            moving -> { toast = "Detén el vehículo para cambiar la configuración" }
            speed.speedKmh == null && System.currentTimeMillis() > confirmedUntil -> { pendingConfirm = action }
            else -> action()
        }
    }

    fun openSettings(page: SettingsPage) {
        settingsPage = page
        section.value = Section.SETTINGS
    }

    /** Abre la app del sistema configurada; si no hay, va directo a su selector. */
    fun launchExternal(app: ExternalApp) {
        val pkg = packages[app].orEmpty()
        if (pkg.isBlank()) {
            if (moving) { toast = "Configura ${app.label} con el vehículo detenido" } else { guard { openSettings(SettingsPage.AppSelect(app)) } }
        } else if (!c.launcher.launch(pkg)) {
            toast = "No se pudo abrir ${app.label}"
        }
    }

    /** Abre la app de navegación con el destino (Waze, Google Maps u otra configurada). */
    fun navigateTo(p: Place) {
        val pkg = packages[ExternalApp.NAVIGATION]
        val ok = runCatching { ctx.startActivity(c.nav.navigationIntent(p, pkg)) }.isSuccess ||
            runCatching { ctx.startActivity(c.nav.navigationIntent(p, null)) }.isSuccess
        if (!ok) { toast = "No hay app de navegación para abrir el destino" }
    }

    fun launchApp(pkg: String): Boolean {
        val ok = c.launcher.launch(pkg)
        if (!ok) { toast = "No se pudo abrir la app" }
        return ok
    }

    BackHandler(enabled = rest != null || pendingConfirm != null || quickOpen || section.value != Section.HOME) {
        when {
            rest != null -> { wake() }
            pendingConfirm != null -> { pendingConfirm = null }
            quickOpen -> { quickOpen = false }
            section.value == Section.SETTINGS && settingsPage != SettingsPage.Root -> { settingsPage = settingsPage.parent() }
            else -> { section.value = Section.HOME }
        }
    }

    val musicActions = remember {
        MusicActions(
            playPause = c.media::playPause,
            next = c.media::next,
            previous = c.media::previous,
            seekTo = c.media::seekTo,
            grantAccess = { c.controls.openNotificationAccess() },
            openBluetooth = { c.controls.openBluetooth() },
            toggleShuffle = c.media::toggleShuffle,
            cycleRepeat = c.media::cycleRepeat,
            volumeStep = { up -> c.controls.stepVolume(up) },
            volumeLevel = { c.controls.volumeFraction() },
        )
    }

    // Acciones del Inicio: un solo objeto (las lambdas leen el estado actual al ejecutarse).
    // launchExternal usa valores calculados en cada recomposición (p. ej. si el carro está en movimiento):
    // se accede a la versión más reciente a través de rememberUpdatedState.
    val launchExternalNow by rememberUpdatedState<(ExternalApp) -> Unit> { launchExternal(it) }
    val homeActions = remember {
        HomeActions(
            requestLocation = { permLauncher.launch(locPerms) },
            openMusic = { section.value = Section.MUSIC },
            androidAuto = { launchExternalNow(ExternalApp.ANDROID_AUTO) },
            radio = { launchExternalNow(ExternalApp.RADIO) },
            musicBt = { launchExternalNow(ExternalApp.MUSIC_BT) },
            apps = { section.value = Section.APPS },
            openMap = { section.value = Section.MAP },
            go = { destination?.let { navigateTo(it) } },
            openFuel = { section.value = Section.FUEL },
        )
    }

    val mapActions = remember {
        MapActions(
            search = { q -> c.nav.search(q, speed.latitude?.let { la -> speed.longitude?.let { lo -> GeoPoint(la, lo) } }) },
            reverse = { p -> c.nav.reverse(p) },
            setDestination = { c.nav.setDestination(it) },
            saveHome = { c.nav.saveHome(it) },
            saveWork = { c.nav.saveWork(it) },
            addFavorite = { c.nav.addFavorite(it) },
            removeFavorite = { c.nav.removeFavorite(it) },
            navigate = { navigateTo(it) },
            toast = { toast = it },
        )
    }

    val systemTiles = remember(packages) {
        listOf(
            SystemTile("Android Auto", DashIcons.AaFill, 40, ExternalApp.ANDROID_AUTO, !packages[ExternalApp.ANDROID_AUTO].isNullOrBlank()),
            SystemTile("Radio FM", DashIcons.Radio, 42, ExternalApp.RADIO, !packages[ExternalApp.RADIO].isNullOrBlank()),
            SystemTile("Música BT", DashIcons.Music, 40, ExternalApp.MUSIC_BT, !packages[ExternalApp.MUSIC_BT].isNullOrBlank()),
            SystemTile("Teléfono BT", DashIcons.PhoneFill, 38, ExternalApp.PHONE_BT, !packages[ExternalApp.PHONE_BT].isNullOrBlank()),
            SystemTile("Ecualizador", DashIcons.Eq, 40, ExternalApp.EQUALIZER, !packages[ExternalApp.EQUALIZER].isNullOrBlank()),
            SystemTile("Combustible", DashIcons.Fuel, 40, null, true, action = { section.value = Section.FUEL }),
            SystemTile("Ajustes", DashIcons.Gear, 40, null, true),
        )
    }

    // ---------------------------------------------------------------- Interfaz
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Solo observa (pase inicial, sin consumir) para el temporizador de reposo.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch[0] = SystemClock.elapsedRealtime()
                    }
                }
            }
    ) {
        // En reposo la interfaz no se dibuja: el mapa, el progreso y las animaciones se detienen.
        if (rest == null) {
        // Fondo desenfocado mientras el centro de control está abierto (Android 12+).
        val bgBlur by animateDpAsState(if (quickOpen) 18.dp else 0.dp, tween(220), label = "quickBlur")
        Box(
            Modifier
                .fillMaxSize()
                .then(if (bgBlur > 0.dp) Modifier.blur(bgBlur, BlurredEdgeTreatment.Rectangle) else Modifier)
        ) {
        // En la página de letra solo queda el menú lateral.
        if (!lyricsPageOpen) {
            TopBar(
                btOn, phoneConnected, wifiOn, resumeTick,
                onQuick = { quickOpen = true },
                onClockLongPress = { startRest(RestMode.CLOCK) },
                onScreenOff = { startRest(RestMode.OFF) },
                weather = weather,
            )
        }
        Sidebar(
            current = section.value,
            onSelect = { s ->
                if (s == Section.SETTINGS) settingsPage = SettingsPage.Root
                section.value = s
            },
            onPhone = { launchExternal(ExternalApp.PHONE_BT) },
            onAndroidAuto = { launchExternal(ExternalApp.ANDROID_AUTO) },
        )

        AnimatedContent(
            section.value,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
            label = "section",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            if (s == Section.HOME) {
                HomeScreen(
                    page = homePage,
                    pages = homePages,
                    onPage = { setHomePage(it) },
                    speed = speed,
                    media = media,
                    phoneConnected = phoneConnected,
                    phoneName = phoneName,
                    lyrics = lyrics,
                    driving = moving,
                    destination = destination,
                    route = route,
                    music = musicActions,
                    a = homeActions,
                )
            } else {
                // AnimatedContent impone tamaño fijo 1280×720 a su contenido. El Box exterior (pantalla completa)
                // libera esa restricción para que el área de contenido mida exactamente 1125×571.
                Box(Modifier.fillMaxSize()) {
                Box(Modifier.wrapContentSize(Alignment.TopStart).at(133, 100, 1125, 571)) {
                    when (s) {
                        Section.MUSIC -> MusicScreen(
                            m = media,
                            phoneConnected = phoneConnected,
                            actions = musicActions,
                            lyrics = lyrics,
                            lyricsEnabled = lyricsEnabled,
                            driving = moving,
                            onLyricsOpened = { c.lyrics.retry() },
                        )
                        Section.APPS -> AppsScreen(
                            system = systemTiles,
                            apps = apps,
                            canSearch = !moving,
                            onSystem = { t ->
                                val app = t.app
                                val action = t.action
                                when {
                                    action != null -> action()
                                    app == null -> openSettings(SettingsPage.Root)
                                    else -> launchExternal(app)
                                }
                            },
                            onLaunch = { pkg -> launchApp(pkg) },
                        )
                        Section.MAP -> FullMapScreen(
                            speed = speed,
                            destination = destination,
                            route = route,
                            home = homePlace,
                            work = workPlace,
                            favorites = savedPlaces,
                            locked = moving,
                            navAppLabel = c.launcher.labelOf(packages[ExternalApp.NAVIGATION])?.takeIf { it.length <= 14 } ?: "navegación",
                            a = mapActions,
                        )
                        Section.FUEL -> FuelScreen(
                            // Recalcular solo cuando cambian las recargas o cada ~100 m recorridos.
                            stats = remember(fills, (odometerKm * 10).toInt()) { c.fuel.stats(odometerKm) },
                            odometerKm = odometerKm,
                            locked = moving,
                            onAdd = { g, p, full ->
                                c.fuel.add(g, p, full, odometerKm)
                                toast = "Recarga registrada"
                            },
                            onDelete = { id -> c.fuel.delete(id) },
                        )
                        else -> SettingsScreen(
                            page = settingsPage,
                            apps = apps,
                            s = SettingsUiState(
                                packages = packages,
                                packageLabels = packageLabels,
                                bluetoothOn = btOn,
                                phoneConnected = phoneConnected,
                                wifiConnected = wifiOn,
                                speedKmh = speed.speedKmh,
                                locationGranted = hasLocation,
                                gpsEnabled = speed.gpsEnabled,
                                bluetoothPermission = hasBtPerm,
                                notificationAccess = media.hasAccess,
                                canWriteSettings = canWrite,
                                nightDim = night,
                                otherMediaFallback = otherFallback,
                                radioLabel = radioLabel,
                                autoRestMinutes = autoRest,
                                lyricsEnabled = lyricsEnabled,
                                onlineArt = onlineArt,
                                locked = moving,
                                versionName = BuildConfig.VERSION_NAME,
                                showDiagnostics = true,
                            ),
                            a = SettingsActions(
                                navigate = { settingsPage = it },
                                back = { settingsPage = settingsPage.parent() },
                                navigateGuarded = { p -> guard { settingsPage = p } },
                                openBluetooth = { c.controls.openBluetooth() },
                                openWifi = { c.controls.openWifi() },
                                setNightDim = { c.config.setNightDim(it) },
                                setOtherMediaFallback = { c.config.setOtherMediaFallback(it) },
                                testApp = { pkg -> launchApp(pkg) },
                                useApp = { app, pkg ->
                                    guard {
                                        c.config.setPackage(app, pkg)
                                        toast = if (pkg.isBlank()) "${app.label}: quitada" else "${app.label}: ${c.launcher.labelOf(pkg) ?: pkg}"
                                        settingsPage = SettingsPage.SystemApps
                                    }
                                },
                                setRadioLabel = { c.config.setRadioLabel(it) },
                                requestLocation = {
                                    if (hasLocation) c.controls.openAppDetails() else permLauncher.launch(locPerms)
                                },
                                openLocationSettings = { c.controls.openLocationSettings() },
                                requestBluetoothPermission = {
                                    if (hasBtPerm || btPerms.isEmpty()) c.controls.openAppDetails() else permLauncher.launch(btPerms)
                                },
                                openNotificationAccess = { c.controls.openNotificationAccess() },
                                openWriteSettings = { c.controls.openWriteSettingsPermission() },
                                openDisplaySettings = { c.controls.openDisplaySettings() },
                                brightness = { c.controls.brightnessFraction() },
                                setBrightness = { c.controls.setBrightness(it) },
                                setAutoRestMinutes = { c.config.setAutoRestMinutes(it) },
                                setLyricsEnabled = { c.config.setLyricsEnabled(it) },
                                setOnlineArt = { c.config.setOnlineArt(it) },
                                volume = { c.controls.volumeFraction() },
                                setVolume = { c.controls.setVolume(it) },
                                openEqualizer = { launchExternal(ExternalApp.EQUALIZER) },
                                openFuel = { section.value = Section.FUEL },
                                startRest = { startRest(it) },
                                openHomeSettings = { c.controls.openHomeSettings() },
                                openAndroidSettings = { c.controls.openSettings() },
                                openAppInfo = { c.controls.openAppDetails() },
                                diagnostics = { buildDiagnostics(c, hasLocation, hasBtPerm) },
                            ),
                        )
                    }
                }
                }
            }
        }
        }

        QuickSettingsPanel(
            visible = quickOpen,
            bluetoothOn = btOn,
            phoneConnected = phoneConnected,
            wifiOn = wifiOn,
            nightOn = night,
            controls = c.controls,
            actions = QuickActions(
                bluetooth = { c.controls.openBluetooth() },
                wifi = { c.controls.openWifi() },
                toggleNight = { c.config.setNightDim(!night) },
                equalizer = {
                    quickOpen = false
                    launchExternal(ExternalApp.EQUALIZER)
                },
                settings = {
                    quickOpen = false
                    openSettings(SettingsPage.Root)
                },
                rest = { startRest(RestMode.CLOCK) },
                screenOff = { startRest(RestMode.OFF) },
                dismiss = { quickOpen = false },
            ),
        )

        pendingConfirm?.let { action ->
            ConfirmStopped(
                onConfirm = {
                    confirmedUntil = System.currentTimeMillis() + CONFIRM_VALID_MS
                    pendingConfirm = null
                    action()
                },
                onCancel = { pendingConfirm = null },
            )
        }

        // Aviso breve, no invasivo, que desaparece solo.
        AnimatedVisibility(
            toast != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
            enter = fadeIn(tween(180)), exit = fadeOut(tween(180)),
        ) {
            Text(
                toast.orEmpty(),
                style = DashType.Body,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xF21C1D20))
                    .border(1.dp, Dash.BorderStrong, RoundedCornerShape(50))
                    .padding(horizontal = 26.dp, vertical = 13.dp),
            )
        }
        // Aviso único cuando GitHub tiene una versión nueva.
        LaunchedEffect(Unit) {
            var shown = false
            c.updater.state.collect { u ->
                if (u is com.bryan.corolladash.update.UpdateState.Available && !shown) {
                    shown = true
                    toast = "Actualización ${u.versionName} disponible · Ajustes → Sistema"
                }
            }
        }
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(2500)
                toast = null
            }
        }

        // Modo noche: velo oscuro que no intercepta toques.
        if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))

        }

        rest?.let { mode -> RestScreen(mode, media, resumeTick, onWake = { wake() }) }

        // La llamada queda por encima de todo, incluso del reposo.
        CallBanner(
            call = call,
            onAction = { a ->
                if (!c.calls.send(a)) { toast = "No se pudo ${a.label.lowercase()}" }
            },
            onOpen = {
                if (!c.calls.open()) launchExternal(ExternalApp.PHONE_BT)
            },
        )
    }
}

// ---------------------------------------------------------------- Confirmación sin GPS

@Composable
private fun ConfirmStopped(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000)).pointerInput(Unit) { detectTapGestures { onCancel() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF151619))
                .border(1.dp, Dash.BorderStrong, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("¿El vehículo está detenido?", style = DashType.Title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "No hay señal GPS para comprobar la velocidad. Cambia la configuración solo con el vehículo detenido.",
                style = DashType.Label, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton("Cancelar", onCancel)
                PillButton("Sí, está detenido", onConfirm, strong = true)
            }
        }
    }
}

// ---------------------------------------------------------------- Diagnóstico (solo debug)

private fun buildDiagnostics(c: AppContainer, hasLocation: Boolean, hasBtPerm: Boolean): List<DiagSection> {
    fun yes(b: Boolean) = if (b) "sí" else "no"
    val sp = c.speed.value
    val m = c.media.state.value
    val sessions = c.media.debugSessions()
    val out = mutableListOf<DiagSection>()

    out += DiagSection("Velocidad GPS", listOf(
        "Velocidad" to (sp.speedKmh?.let { "$it km/h" } ?: "— (sin dato)"),
        "Permiso de ubicación" to yes(hasLocation),
        "GPS del sistema activado" to yes(sp.gpsEnabled),
    ))
    out += DiagSection("Conectividad", listOf(
        "Bluetooth encendido" to yes(c.status.bluetoothOn.value),
        "Teléfono conectado" to yes(c.status.phoneConnected.value),
        "Permiso dispositivos cercanos" to yes(hasBtPerm),
        "Wi-Fi conectado" to yes(c.status.wifiConnected.value),
    ))
    out += DiagSection("Música (sesión elegida)", listOf(
        "Acceso a notificaciones" to yes(m.hasAccess),
        "Servicio de notificaciones conectado" to yes(MediaListenerService.instance != null),
        "Package" to (m.sourcePackage ?: "ninguna"),
        "Título / artista" to "${m.title ?: "—"} / ${m.artist ?: "—"}",
        "Reproduciendo" to yes(m.isPlaying),
        "Duración" to "${m.durationMs / 1000} s",
        "Carátula" to (m.artSource ?: "ninguna (placeholder)"),
        "Carátula en internet (último intento)" to (c.media.onlineArtResult ?: "—"),
        "Otras fuentes permitidas" to yes(c.config.otherMediaFallback.value),
    ))
    out += DiagSection(
        "Sesiones activas (${sessions.size})",
        sessions.flatMapIndexed { i, s ->
            listOf(
                "#${i + 1} package" to s.packageName,
                "#${i + 1} estado" to s.playbackState,
                "#${i + 1} título / artista" to "${s.title ?: "—"} / ${s.artist ?: "—"}",
                "#${i + 1} duración" to "${s.durationMs / 1000} s",
                "#${i + 1} ALBUM_ART (bitmap)" to yes(s.hasArtBitmap),
                "#${i + 1} ART_URI" to (s.artUri ?: "—"),
                "#${i + 1} acciones" to s.actions,
            )
        },
    )
    val call = c.calls.state.value
    out += DiagSection("Llamadas", listOf(
        "Teléfono BT configurado" to c.config.packageOf(ExternalApp.PHONE_BT).ifBlank { "no" },
        "Llamada activa" to (call?.let { "${it.caller} · ${if (it.incoming) "entrante" else "en curso"} · ${it.packageName}" } ?: "no"),
        "Botones" to (call?.actions?.joinToString { it.label } ?: "—"),
    ) + synchronized(c.calls.recent) { c.calls.recent.toList() }.mapIndexed { i, l -> "Aviso ${i + 1}" to l })
    out += DiagSection("Lyrics", c.lyrics.diagnostics())
    out += DiagSection("Mapa y destino", listOf(
        "Destino" to (c.nav.destination.value?.let { "${it.name} (${it.lat}, ${it.lon})" } ?: "—"),
        "Ruta" to (c.nav.route.value?.let { "${(it.distanceM / 1000).toInt()} km · ${(it.durationS / 60).toInt()} min · ${it.points.size} puntos" } ?: "—"),
        "Último error" to (c.nav.lastError ?: "—"),
        "Teléfono" to (c.status.phoneName.value ?: "—"),
        "Reproductor de origen" to (c.media.state.value.playerName ?: "no informado"),
    ))
    out += DiagSection("Clima y kilometraje", listOf(
        "Clima" to (c.weather.state.value?.let { "${it.tempC}° · código ${it.code} · lluvia en ${it.rainInMin ?: "—"} min" } ?: "sin dato"),
        "Error de clima" to (c.weather.lastError ?: "—"),
        "Kilometraje GPS" to String.format(java.util.Locale.US, "%.2f km", c.odometer.km.value),
        "Recargas registradas" to c.fuel.fills.value.size.toString(),
    ))
    out += DiagSection("Apps del sistema", ExternalApp.entries.map { app ->
        val pkg = c.config.packageOf(app)
        app.label to when {
            pkg.isBlank() -> "sin configurar"
            c.launcher.isInstalled(pkg) -> pkg
            else -> "$pkg (no instalada)"
        }
    })
    out += DiagSection("Últimos intentos de abrir apps", c.launcher.recentLaunches.map { r ->
        r.packageName to (if (r.ok) "OK" else "falló: ${r.error ?: "?"} · intent=${r.launchIntent ?: "ninguno"}")
    })
    return out
}

// ---------------------------------------------------------------- Barra lateral

/* x22 y45 93×635; botones 76×78 con centros en y 88/184/280/376/472/568 (relativos a la barra). */
@Composable
private fun Sidebar(current: Section, onSelect: (Section) -> Unit, onPhone: () -> Unit, onAndroidAuto: () -> Unit) {
    Box(
        Modifier
            .at(22, 45, 93, 635)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0E0F11), Color(0xFF0A0B0D))))
            .border(1.dp, Dash.Border, RoundedCornerShape(30.dp))
    ) {
        SideItem(DashIcons.Home, current == Section.HOME, 88, "Inicio") { onSelect(Section.HOME) }
        SideItem(DashIcons.Music, current == Section.MUSIC, 184, "Música") { onSelect(Section.MUSIC) }
        SideItem(DashIcons.PhoneFill, false, 280, "Teléfono", onPhone)
        SideItem(DashIcons.AaFill, false, 376, "Android Auto", onAndroidAuto)
        SideItem(DashIcons.Grid, current == Section.APPS, 472, "Aplicaciones") { onSelect(Section.APPS) }
        SideItem(DashIcons.Gear, current == Section.SETTINGS, 568, "Ajustes") { onSelect(Section.SETTINGS) }
    }
}

@Composable
private fun SideItem(icon: ImageVector, active: Boolean, centerY: Int, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .at(8, centerY - 39, 76, 78)
            .clip(shape)
            .background(if (active) Dash.Selected else Color.Transparent)
            .border(1.dp, if (active) Dash.SelectedBorder else Color.Transparent, shape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(38.dp), tint = if (active) Dash.Text else Dash.Icon2)
    }
}

// ---------------------------------------------------------------- Barra superior

/** Código WMO de Open-Meteo → icono monocromático. */
private fun weatherIcon(w: Weather): ImageVector = when (w.code) {
    0 -> if (w.isDay) DashIcons.Sun else DashIcons.Moon
    1, 2 -> if (w.isDay) DashIcons.PartlyCloudy else DashIcons.Cloud
    45, 48 -> DashIcons.Fog
    in 51..67, in 80..82 -> DashIcons.Rain
    in 95..99 -> DashIcons.Storm
    else -> DashIcons.Cloud
}

@Composable
private fun TopRoundButton(icon: ImageVector, label: String, x: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .offset(x.dp, 26.dp)
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Dash.Well)
            .border(1.dp, Dash.Border, RoundedCornerShape(22.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = Dash.Icon)
    }
}

/* Hora x146 y28 (62 px) + fecha (mantener presionada = reposo). A la derecha solo estados reales: Bluetooth y Wi-Fi.
 * Tocar esa zona abre el centro de control. */
@Composable
private fun TopBar(
    btOn: Boolean,
    phoneConnected: Boolean,
    wifiOn: Boolean,
    resumeTick: Int,
    onQuick: () -> Unit,
    onClockLongPress: () -> Unit,
    onScreenOff: () -> Unit,
    weather: Weather?,
) {
    val now = rememberNowMillis(resumeTick)
    val (time, date) = remember(now) { clockStrings(now) }
    // Mantener presionada la hora = reposo.
    Row(Modifier.offset(146.dp, 28.dp).pointerInput(Unit) { detectTapGestures(onLongPress = { onClockLongPress() }) }) {
        Text(time, style = DashType.Clock.tight(), modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(30.dp))
        Text(date, style = DashType.Date.tight(), modifier = Modifier.alignByBaseline())
    }
    // Clima (Open-Meteo): icono, temperatura y aviso de lluvia; alineado a la derecha antes de los botones.
    weather?.let { w ->
        Row(
            Modifier.offset(600.dp, 24.dp).size(404.dp, 44.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rain = when {
                w.raining -> "Lloviendo"
                w.rainInMin != null -> "Lluvia en ~${w.rainInMin} min"
                else -> null
            }
            rain?.let {
                Text(it, style = DashType.Label.copy(fontSize = 16.sp, color = Dash.TextReadable), maxLines = 1)
                Spacer(Modifier.width(14.dp))
            }
            Icon(weatherIcon(w), null, Modifier.size(26.dp), tint = Dash.Icon)
            Spacer(Modifier.width(8.dp))
            Text("${w.tempC}°", style = DashType.Headline.copy(fontSize = 22.sp, color = Dash.Text), maxLines = 1)
        }
    }
    // Botones rápidos: reposo con reloj tenue y apagar pantalla (un toque; cualquier toque despierta).
    TopRoundButton(DashIcons.Clock, "Modo reposo", x = 1020, onClick = onClockLongPress)
    TopRoundButton(DashIcons.Power, "Apagar pantalla", x = 1076, onClick = onScreenOff)
    Row(
        Modifier
            .offset((1280 - 26 - 120).dp, 24.dp)
            .size(120.dp, 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClickLabel = "Centro de control", onClick = onQuick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (btOn) {
            Icon(
                DashIcons.Bt, if (phoneConnected) "Teléfono conectado" else "Bluetooth activado",
                Modifier.size(28.dp), tint = if (phoneConnected) Dash.Text else Dash.Icon2,
            )
        }
        if (wifiOn) Icon(DashIcons.Wifi, "Wi-Fi conectado", Modifier.size(28.dp), tint = Dash.Text)
        // Sin ningún estado activo: un control discreto para abrir el centro de control.
        if (!btOn && !wifiOn) Icon(DashIcons.More, "Centro de control", Modifier.size(28.dp), tint = Dash.Icon2)
    }
}
