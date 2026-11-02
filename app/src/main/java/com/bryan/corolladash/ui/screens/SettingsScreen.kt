package com.bryan.corolladash.ui.screens

import androidx.compose.runtime.Immutable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.launcher.AppEntry
import com.bryan.corolladash.settings.ExternalApp
import com.bryan.corolladash.ui.components.CircleButton
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.PillButton
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import java.util.Locale

/** Subpantallas de Ajustes. Atrás vuelve a [parent]. */
sealed interface SettingsPage {
    data object Root : SettingsPage
    data object Connectivity : SettingsPage
    data object SystemApps : SettingsPage
    data class AppSelect(val app: ExternalApp) : SettingsPage
    data object RadioLabel : SettingsPage
    data object Display : SettingsPage
    data object Sound : SettingsPage
    data object Driving : SettingsPage
    data object System : SettingsPage
    data object Permissions : SettingsPage
    data object Info : SettingsPage
    data object Diagnostics : SettingsPage
    data object InstalledApps : SettingsPage
}

/** Página a la que vuelve "Atrás". */
fun SettingsPage.parent(): SettingsPage = when (this) {
    SettingsPage.Root -> SettingsPage.Root
    is SettingsPage.AppSelect, SettingsPage.RadioLabel -> SettingsPage.SystemApps
    SettingsPage.Permissions, SettingsPage.Info -> SettingsPage.System
    SettingsPage.Diagnostics, SettingsPage.InstalledApps -> SettingsPage.Info
    else -> SettingsPage.Root
}

/** Bloque de diagnóstico: título + pares clave/valor. */
@Immutable
data class DiagSection(val title: String, val lines: List<Pair<String, String>>)

@Immutable
data class SettingsUiState(
    val packages: Map<ExternalApp, String>,
    val packageLabels: Map<ExternalApp, String?>,
    val bluetoothOn: Boolean,
    val phoneConnected: Boolean,
    val wifiConnected: Boolean,
    val speedKmh: Int?,
    val locationGranted: Boolean,
    val gpsEnabled: Boolean,
    val bluetoothPermission: Boolean,
    val notificationAccess: Boolean,
    val canWriteSettings: Boolean,
    val nightDim: Boolean,
    val otherMediaFallback: Boolean,
    val radioLabel: String,
    val autoRestMinutes: Int,
    val lyricsEnabled: Boolean,
    val onlineArt: Boolean,
    /** Vehículo en movimiento (velocidad GPS real ≥ 8 km/h): se bloquean cambios de configuración. */
    val locked: Boolean,
    val versionName: String,
    val showDiagnostics: Boolean,
)

class SettingsActions(
    val navigate: (SettingsPage) -> Unit,
    /** Vuelve a la página padre. */
    val back: () -> Unit,
    /** Abre una subpantalla de configuración aplicando el control de seguridad de conducción. */
    val navigateGuarded: (SettingsPage) -> Unit,
    val openBluetooth: () -> Unit,
    val openWifi: () -> Unit,
    val setNightDim: (Boolean) -> Unit,
    val setOtherMediaFallback: (Boolean) -> Unit,
    val testApp: (String) -> Unit,
    val useApp: (ExternalApp, String) -> Unit,
    val setRadioLabel: (String) -> Unit,
    val requestLocation: () -> Unit,
    val openLocationSettings: () -> Unit,
    val requestBluetoothPermission: () -> Unit,
    val openNotificationAccess: () -> Unit,
    val openWriteSettings: () -> Unit,
    val openDisplaySettings: () -> Unit,
    val brightness: () -> Float,
    val setBrightness: (Float) -> Unit,
    val setAutoRestMinutes: (Int) -> Unit,
    val setLyricsEnabled: (Boolean) -> Unit,
    val setOnlineArt: (Boolean) -> Unit,
    val volume: () -> Float,
    val setVolume: (Float) -> Unit,
    val openEqualizer: () -> Unit,
    val openFuel: () -> Unit,
    val startRest: (RestMode) -> Unit,
    val openHomeSettings: () -> Unit,
    val openAndroidSettings: () -> Unit,
    val openAppInfo: () -> Unit,
    val diagnostics: () -> List<DiagSection>,
)

@Composable
fun SettingsScreen(page: SettingsPage, s: SettingsUiState, a: SettingsActions, apps: List<AppEntry>?) {
    when (page) {
        SettingsPage.Root -> RootPage(s, a)
        SettingsPage.Connectivity -> ConnectivityPage(s, a)
        SettingsPage.SystemApps -> SystemAppsPage(s, a)
        is SettingsPage.AppSelect -> AppSelectPage(page.app, s, a, apps)
        SettingsPage.Driving -> DrivingPage(s, a)
        SettingsPage.Display -> DisplayPage(s, a)
        SettingsPage.Sound -> SoundPage(s, a)
        SettingsPage.System -> SystemPage(s, a)
        SettingsPage.RadioLabel -> RadioLabelPage(s, a)
        SettingsPage.Permissions -> PermissionsPage(s, a)
        SettingsPage.Info -> InfoPage(s, a)
        SettingsPage.Diagnostics -> DiagnosticsPage(a)
        SettingsPage.InstalledApps -> InstalledAppsPage(a, apps)
    }
}

// ================================================================= Lista principal

@Composable
private fun RootPage(s: SettingsUiState, a: SettingsActions) {
    val configured = ExternalApp.entries.count { !s.packages[it].isNullOrBlank() }
    val permsOk = listOf(s.locationGranted, s.bluetoothPermission, s.notificationAccess, s.canWriteSettings).count { it }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ajustes", style = DashType.LargeTitle)
            if (s.locked) {
                Spacer(Modifier.width(18.dp))
                Text("Detén el vehículo para cambiar la configuración", style = DashType.Label.copy(color = Dash.Warn))
            }
        }
        Spacer(Modifier.height(18.dp))
        Group(null) {
            CategoryRow(DashIcons.Wifi, "Conectividad", listOf(
                "Bluetooth: " + when { !s.bluetoothOn -> "desactivado"; s.phoneConnected -> "teléfono conectado"; else -> "activado" },
                "Wi-Fi: " + if (s.wifiConnected) "conectado" else "sin conexión",
            ).joinToString(" · ")) { a.navigate(SettingsPage.Connectivity) }
            CategoryRow(DashIcons.Grid, "Aplicaciones del sistema", "$configured de ${ExternalApp.entries.size} configuradas · Radio: ${s.radioLabel.ifBlank { "sin etiqueta" }}") {
                a.navigate(SettingsPage.SystemApps)
            }
            CategoryRow(DashIcons.Sun, "Pantalla y reposo", listOfNotNull(
                if (s.canWriteSettings) "Brillo" else "Brillo: permiso pendiente",
                if (s.nightDim) "Modo noche activado" else null,
                if (s.autoRestMinutes > 0) "Reposo automático ${s.autoRestMinutes} min" else "Reposo manual",
            ).joinToString(" · ")) { a.navigate(SettingsPage.Display) }
            CategoryRow(DashIcons.Vol, "Música y sonido", listOf(
                "Volumen",
                "Ecualizador",
                if (s.lyricsEnabled) "Letras activadas" else "Letras desactivadas",
            ).joinToString(" · ")) { a.navigate(SettingsPage.Sound) }
            CategoryRow(DashIcons.Gauge, "Conducción", listOf(
                "Velocidad GPS: " + when {
                    !s.locationGranted -> "sin permiso"
                    !s.gpsEnabled -> "GPS desactivado"
                    s.speedKmh != null -> "${s.speedKmh} km/h"
                    else -> "buscando señal"
                },
                "Bloqueo a 8 km/h",
            ).joinToString(" · ")) { a.navigate(SettingsPage.Driving) }
            CategoryRow(DashIcons.Gear, "Sistema", "App de inicio · Permisos $permsOk de 4 · Versión ${s.versionName}", last = true) {
                a.navigate(SettingsPage.System)
            }
        }
    }
}

/** Fila de categoría: icono, título y resumen del estado real. */
@Composable
private fun CategoryRow(icon: ImageVector, title: String, summary: String, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Dash.Well), contentAlignment = Alignment.Center) {
            DashIcon(icon, 22, Dash.Icon)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = DashType.Body.copy(fontSize = 20.sp), maxLines = 1)
            Text(summary, style = DashType.Footnote.copy(color = Dash.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DashIcon(DashIcons.Chev, 22, Dash.Text3)
    }
    if (!last) RowDivider(start = 76)
}

// ================================================================= Categorías

@Composable
private fun ConnectivityPage(s: SettingsUiState, a: SettingsActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Conectividad", a)
        Group(null) {
            SettingRow(DashIcons.Bt, "Bluetooth", when {
                !s.bluetoothOn -> "Desactivado"
                s.phoneConnected -> "Teléfono conectado"
                else -> "Activado"
            }, onClick = a.openBluetooth)
            SettingRow(DashIcons.Wifi, "Wi-Fi", if (s.wifiConnected) "Conectado" else "Sin conexión", onClick = a.openWifi, last = true)
        }
        Text(
            "Android 15 no permite que una app encienda o apague Bluetooth o Wi-Fi: se abre el panel del sistema.",
            style = DashType.Footnote, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SystemAppsPage(s: SettingsUiState, a: SettingsActions) {
    fun appValue(app: ExternalApp): String {
        val pkg = s.packages[app].orEmpty()
        return when {
            pkg.isBlank() -> "Configurar"
            s.packageLabels[app] == null -> "No instalada"
            else -> s.packageLabels[app].orEmpty()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Aplicaciones del sistema", a)
        Group(null) {
            SettingRow(DashIcons.AaFill, "Android Auto", appValue(ExternalApp.ANDROID_AUTO), enabled = !s.locked) { a.navigateGuarded(SettingsPage.AppSelect(ExternalApp.ANDROID_AUTO)) }
            SettingRow(DashIcons.Radio, "Radio FM", appValue(ExternalApp.RADIO), enabled = !s.locked) { a.navigateGuarded(SettingsPage.AppSelect(ExternalApp.RADIO)) }
            SettingRow(DashIcons.Music, "Música BT", appValue(ExternalApp.MUSIC_BT), enabled = !s.locked) { a.navigateGuarded(SettingsPage.AppSelect(ExternalApp.MUSIC_BT)) }
            SettingRow(DashIcons.PhoneFill, "Teléfono BT", appValue(ExternalApp.PHONE_BT), enabled = !s.locked) { a.navigateGuarded(SettingsPage.AppSelect(ExternalApp.PHONE_BT)) }
            SettingRow(DashIcons.Eq, "Ecualizador", appValue(ExternalApp.EQUALIZER), enabled = !s.locked) { a.navigateGuarded(SettingsPage.AppSelect(ExternalApp.EQUALIZER)) }
            SettingRow(DashIcons.Gps, "Navegación", appValue(ExternalApp.NAVIGATION), enabled = !s.locked, last = true) { a.navigateGuarded(SettingsPage.AppSelect(ExternalApp.NAVIGATION)) }
        }
        Group(null) {
            SettingRow(DashIcons.Radio, "Etiqueta de Radio FM", s.radioLabel.ifBlank { "Ninguna" }, enabled = !s.locked, last = true) { a.navigateGuarded(SettingsPage.RadioLabel) }
        }
    }
}

@Composable
private fun SoundPage(s: SettingsUiState, a: SettingsActions) {
    var vol by remember { mutableFloatStateOf(a.volume()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Música y sonido", a)
        Group("Volumen") {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                DashIcon(DashIcons.Vol, 22, Dash.Icon2)
                Spacer(Modifier.width(16.dp))
                GraySlider(vol, { vol = it; a.setVolume(it) }, Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                DashIcon(DashIcons.Vol, 30, Dash.Icon)
            }
        }
        Group(null) {
            SettingRow(DashIcons.Eq, "Ecualizador", "", onClick = a.openEqualizer, last = true)
        }
        Group("Música") {
            SwitchRow(DashIcons.Lyrics, "Mostrar función de letras", s.lyricsEnabled, a.setLyricsEnabled)
            SwitchRow(DashIcons.Music, "Carátulas desde internet", s.onlineArt, a.setOnlineArt)
            SwitchRow(DashIcons.Music, "Otras fuentes de música", s.otherMediaFallback, a.setOtherMediaFallback, last = true)
        }
        Text(
            "Carátulas: si Bluetooth no envía la imagen, se busca por artista y título (iTunes) y queda guardada. " +
                "Letras: se buscan en LRCLIB (lrclib.net) con título, artista y duración; requieren Internet y las ya vistas quedan guardadas. " +
                "Otras fuentes: si Música BT no está activa, la música puede venir de otra app que esté reproduciendo.",
            style = DashType.Footnote, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SystemPage(s: SettingsUiState, a: SettingsActions) {
    val permsOk = listOf(s.locationGranted, s.bluetoothPermission, s.notificationAccess, s.canWriteSettings).count { it }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Sistema", a)
        Group(null) {
            SettingRow(DashIcons.Home, "Aplicación de inicio", "Elegir", onClick = a.openHomeSettings)
            SettingRow(DashIcons.Lock, "Permisos", "$permsOk de 4") { a.navigate(SettingsPage.Permissions) }
            UpdateRow()
            SettingRow(DashIcons.Info, "Información de CorollaDash", s.versionName) { a.navigate(SettingsPage.Info) }
            SettingRow(DashIcons.Gear, "Ajustes de Android", "", onClick = a.openAndroidSettings, last = true)
        }
    }
}

// ================================================================= Selector de app del sistema

@Composable
private fun AppSelectPage(app: ExternalApp, s: SettingsUiState, a: SettingsActions, apps: List<AppEntry>?) {
    val current = s.packages[app].orEmpty()
    fun suggested(e: AppEntry): Boolean {
        val l = e.label.lowercase(Locale.ROOT)
        val p = e.packageName.lowercase(Locale.ROOT)
        return app.hints.any { h -> l == h || l.contains(h) || p.contains(h.replace(" ", "")) }
    }
    val sorted = apps.orEmpty().sortedWith(
        compareByDescending<AppEntry> { it.packageName == current }
            .thenByDescending { suggested(it) }
            .thenBy { it.label.lowercase(Locale.ROOT) }
    )

    Column(Modifier.fillMaxSize()) {
        PageHeader(app.label, a) {
            if (current.isNotBlank()) PillButton("Quitar", { a.useApp(app, "") }, enabled = !s.locked)
        }
        Text(
            when (app) {
                ExternalApp.ANDROID_AUTO -> "En esta radio Android Auto lo abre la app de proyección (p. ej. CarPlay / csrplay). Toca Probar para comprobarla."
                ExternalApp.RADIO -> "Elige la app de radio de la unidad (la que controla el sintonizador FM)."
                ExternalApp.MUSIC_BT -> "Elige la app de música Bluetooth de la radio. Su reproducción tendrá prioridad en Inicio."
                ExternalApp.PHONE_BT -> "Elige la app de llamadas Bluetooth de la radio."
                ExternalApp.EQUALIZER -> "Elige la app de ecualizador de la radio."
                ExternalApp.NAVIGATION -> "Elige la app de navegación (Waze, Google Maps…). Se abre al tocar el mapa en Inicio."
            },
            style = DashType.Label, modifier = Modifier.padding(bottom = 14.dp),
        )
        if (apps == null) {
            Text("Cargando aplicaciones…", style = DashType.Label)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sorted, key = { it.packageName }) { e ->
                    AppCandidateRow(e, inUse = e.packageName == current, suggested = suggested(e), locked = s.locked,
                        onTest = { a.testApp(e.packageName) }, onUse = { a.useApp(app, e.packageName) })
                }
            }
        }
    }
}

@Composable
private fun AppCandidateRow(e: AppEntry, inUse: Boolean, suggested: Boolean, locked: Boolean, onTest: () -> Unit, onUse: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(shape)
            .background(if (inUse) Dash.CardElevated else Dash.Card)
            .border(1.dp, if (inUse) Dash.BorderStrong else Dash.Border, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(e.icon, null, Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.label, style = DashType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (suggested && !inUse) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Sugerida", style = DashType.Footnote.copy(fontSize = 13.sp, color = Dash.Text2),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Dash.Well).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                e.packageName + (e.activity?.let { " · " + it.substringAfterLast('.') } ?: ""),
                style = DashType.Footnote, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        PillButton("Probar", onTest)
        Spacer(Modifier.width(10.dp))
        if (inUse) {
            Row(Modifier.width(118.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                DashIcon(DashIcons.Check, 20, Dash.Text)
                Spacer(Modifier.width(6.dp))
                Text("En uso", style = DashType.Label.copy(color = Dash.Text))
            }
        } else {
            PillButton("Usar", onUse, Modifier.width(118.dp), strong = true, enabled = !locked)
        }
    }
}

// ================================================================= Subpantallas simples

@Composable
private fun DrivingPage(s: SettingsUiState, a: SettingsActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Conducción", a)
        Group(null) {
            SettingRow(DashIcons.Fuel, "Combustible", "Rendimiento y recargas", onClick = a.openFuel, last = true)
        }
        Group(null) {
            SettingRow(DashIcons.Gauge, "Velocidad actual", s.speedKmh?.let { "$it km/h" } ?: "— km/h")
            SettingRow(
                DashIcons.Lock, "Permiso de ubicación", if (s.locationGranted) "Concedido" else "Permitir",
                onClick = if (s.locationGranted) null else a.requestLocation,
            )
            SettingRow(DashIcons.Gauge, "GPS del sistema", if (s.gpsEnabled) "Activado" else "Desactivado", onClick = a.openLocationSettings, last = true)
        }
        Text(
            "La velocidad viene solo del GPS de la radio. Sin señal se muestra «— km/h»; nunca se inventa un valor. " +
                "Con 8 km/h o más se bloquean los cambios de configuración, la búsqueda de apps y la letra completa (solo 3 líneas).",
            style = DashType.Footnote, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun DisplayPage(s: SettingsUiState, a: SettingsActions) {
    var level by remember { mutableFloatStateOf(a.brightness()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Pantalla y reposo", a)
        Group("Brillo") {
            if (s.canWriteSettings) {
                Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    DashIcon(DashIcons.Sun, 22, Dash.Icon2)
                    Spacer(Modifier.width(16.dp))
                    GraySlider(level, { level = it; a.setBrightness(it) }, Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    DashIcon(DashIcons.Sun, 30, Dash.Icon)
                }
            } else {
                SettingRow(DashIcons.Sun, "Permitir control de brillo", "Abrir", onClick = a.openWriteSettings, last = true)
            }
        }
        Group("Reposo") {
            SettingRow(DashIcons.Clock, "Reposo ahora", "Reloj tenue", onClick = { a.startRest(RestMode.CLOCK) })
            SettingRow(DashIcons.Power, "Apagar pantalla", "Toca para encender", onClick = { a.startRest(RestMode.OFF) })
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                RowIcon(DashIcons.Clock)
                Spacer(Modifier.width(14.dp))
                Text("Reposo automático", style = DashType.Body, modifier = Modifier.weight(1f))
                listOf(0, 2, 5, 10).forEach { m ->
                    Spacer(Modifier.width(8.dp))
                    PillButton(if (m == 0) "No" else "$m min", { a.setAutoRestMinutes(m) }, strong = s.autoRestMinutes == m)
                }
            }
        }
        Text(
            "El reposo automático se activa tras ese tiempo sin tocar la pantalla y solo con el vehículo detenido. " +
                "Una llamada entrante lo interrumpe.",
            style = DashType.Footnote, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
        Group(null) {
            SwitchRow(DashIcons.Moon, "Modo noche", s.nightDim, a.setNightDim)
            SettingRow(DashIcons.Display, "Ajustes de pantalla de Android", "", onClick = a.openDisplaySettings, last = true)
        }
        Text("En algunas radios el brillo lo controla el MCU y puede ignorar este valor.", style = DashType.Footnote, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun RadioLabelPage(s: SettingsUiState, a: SettingsActions) {
    // Frecuencia en décimas de MHz (FM 87.5–108.0).
    val initial = Regex("""\d{2,3}(?:[.,]\d)?""").find(s.radioLabel)?.value?.replace(',', '.')?.toFloatOrNull()
    var tenths by remember { mutableIntStateOf(((initial ?: 103.3f) * 10 + 0.5f).toInt().coerceIn(875, 1080)) }
    fun label(t: Int) = String.format(Locale.US, "%.1f MHz", t / 10f)

    Column(Modifier.fillMaxSize()) {
        PageHeader("Etiqueta de Radio FM", a)
        Text(
            "Texto que aparece bajo «Radio FM» en Inicio. Es solo una etiqueta: CorollaDash no sintoniza la radio; " +
                "al tocar la tarjeta se abre la app de radio de la unidad.",
            style = DashType.Label, modifier = Modifier.padding(bottom = 28.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PillButton("−1", { tenths = (tenths - 10).coerceAtLeast(875) })
            Spacer(Modifier.width(14.dp))
            CircleButton(DashIcons.ChevL, "Bajar 0.2", 64, 30, filled = true, onClick = { tenths = (tenths - 2).coerceAtLeast(875) })
            Text(label(tenths), style = DashType.Speed.copy(fontSize = 60.sp), textAlign = TextAlign.Center, modifier = Modifier.width(420.dp))
            CircleButton(DashIcons.Chev, "Subir 0.2", 64, 30, filled = true, onClick = { tenths = (tenths + 2).coerceAtMost(1080) })
            Spacer(Modifier.width(14.dp))
            PillButton("+1", { tenths = (tenths + 10).coerceAtMost(1080) })
        }
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PillButton("Sin etiqueta", { a.setRadioLabel(""); a.navigate(SettingsPage.SystemApps) })
            Spacer(Modifier.width(14.dp))
            PillButton("Guardar ${label(tenths)}", { a.setRadioLabel(label(tenths)); a.navigate(SettingsPage.SystemApps) }, strong = true)
        }
    }
}

@Composable
private fun PermissionsPage(s: SettingsUiState, a: SettingsActions) {
    fun st(ok: Boolean) = if (ok) "Concedido" else "Permitir"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Permisos", a)
        Group(null) {
            SettingRow(DashIcons.Gauge, "Ubicación (velocidad GPS)", st(s.locationGranted), onClick = a.requestLocation)
            SettingRow(DashIcons.Bt, "Dispositivos cercanos (estado Bluetooth)", st(s.bluetoothPermission), onClick = a.requestBluetoothPermission)
            SettingRow(DashIcons.Music, "Acceso multimedia (notificaciones)", st(s.notificationAccess), onClick = a.openNotificationAccess)
            SettingRow(DashIcons.Sun, "Modificar ajustes del sistema (brillo)", st(s.canWriteSettings), onClick = a.openWriteSettings, last = true)
        }
        Text(
            "El acceso multimedia solo se usa para leer la canción, controlarla y buscar su carátula en la notificación de Música BT. " +
                "No se guardan ni se muestran notificaciones.",
            style = DashType.Footnote, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun InfoPage(s: SettingsUiState, a: SettingsActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Información", a)
        Group(null) {
            SettingRow(DashIcons.Info, "Versión", s.versionName)
            if (s.showDiagnostics) SettingRow(DashIcons.Wrench, "Diagnóstico", "") { a.navigate(SettingsPage.Diagnostics) }
            SettingRow(DashIcons.GridO, "Apps instaladas (técnico)", "") { a.navigate(SettingsPage.InstalledApps) }
            SettingRow(DashIcons.Gear, "Información de la app en Android", "", onClick = a.openAppInfo, last = true)
        }
    }
}

@Composable
private fun DiagnosticsPage(a: SettingsActions) {
    var tick by remember { mutableIntStateOf(0) }
    val sections = remember(tick) { a.diagnostics() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Diagnóstico", a) { PillButton("Actualizar", { tick++ }) }
        LazyColumn(Modifier.weight(1f)) {
            items(sections) { sec ->
                Column { Group(sec.title) {
                    if (sec.lines.isEmpty()) {
                        Text("—", style = DashType.Label, modifier = Modifier.padding(16.dp))
                    }
                    sec.lines.forEachIndexed { i, line ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)) {
                            Text(line.first, style = DashType.Label.copy(fontSize = 16.sp), modifier = Modifier.width(300.dp))
                            Text(line.second, style = DashType.Label.copy(fontSize = 16.sp, color = Dash.Text), modifier = Modifier.weight(1f))
                        }
                        if (i < sec.lines.lastIndex) RowDivider(start = 18)
                    }
                } }
            }
        }
    }
}

@Composable
private fun InstalledAppsPage(a: SettingsActions, apps: List<AppEntry>?) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Apps instaladas", a)
        Text(
            "Nombre, package y Activity de inicio. Útil para identificar las apps propias de la VECHTEL.",
            style = DashType.Label, modifier = Modifier.padding(bottom = 12.dp),
        )
        if (apps == null) {
            Text("Cargando…", style = DashType.Label)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(apps, key = { it.packageName }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dash.Card).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(e.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.label, style = DashType.Body.copy(fontSize = 17.sp))
                            Text(e.packageName, style = DashType.Footnote.copy(color = Dash.Text2))
                            e.activity?.let { Text(it, style = DashType.Footnote, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        PillButton("Probar", { a.testApp(e.packageName) })
                    }
                }
            }
        }
    }
}

// ================================================================= Piezas estilo iOS

/** Encabezado con "Atrás" a la página padre (la página actual la conoce SettingsScreen). */
@Composable
private fun PageHeader(title: String, a: SettingsActions, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable { a.back() }.padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashIcon(DashIcons.ChevL, 30, Dash.Text2)
            Text("Atrás", style = DashType.Label.copy(fontSize = 19.sp))
        }
        Spacer(Modifier.width(18.dp))
        Text(title, style = DashType.Title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** Grupo redondeado con título opcional en mayúsculas pequeñas (como iOS). */
@Composable
private fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(title.uppercase(), style = DashType.Caption, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Dash.Card)
            .border(1.dp, Dash.Border, RoundedCornerShape(20.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider(start: Int = 64) {
    Box(Modifier.fillMaxWidth().padding(start = start.dp).height(1.dp).background(Dash.Border))
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Dash.Well), contentAlignment = Alignment.Center) {
        DashIcon(icon, 20, Dash.Icon)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    enabled: Boolean = true,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            title, style = DashType.Body.copy(color = if (enabled) Dash.Text else Dash.Text3),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(value, style = DashType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(200.dp))
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            DashIcon(DashIcons.Chev, 20, Dash.Text3)
        }
    }
    if (!last) RowDivider()
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit, last: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable { onChange(!checked) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(title, style = DashType.Body, modifier = Modifier.weight(1f))
        GraySwitch(checked)
    }
    if (!last) RowDivider()
}

/** Interruptor monocromático: encendido = gris claro, apagado = gris oscuro. */
@Composable
fun GraySwitch(checked: Boolean) {
    Box(
        Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(50)).background(if (checked) Dash.Active else Dash.Track),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(3.dp).size(26.dp).clip(CircleShape).background(if (checked) Dash.Bg else Dash.Icon2))
    }
}

/** Deslizador horizontal gris carbón + blanco (arrastrar o tocar). */
@Composable
fun GraySlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    Box(
        modifier
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> cb((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> cb((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Dash.Track)) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(Dash.Active))
        }
    }
}

/** Fila "Actualizaciones": busca en GitHub y, si hay versión nueva, la descarga e instala. */
@Composable
private fun UpdateRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val updater = (ctx.applicationContext as com.bryan.corolladash.CorollaDashApp).container.updater
    val st by updater.state.collectAsState()
    val value = when (val u = st) {
        com.bryan.corolladash.update.UpdateState.Idle -> "Buscar"
        com.bryan.corolladash.update.UpdateState.Checking -> "Buscando…"
        com.bryan.corolladash.update.UpdateState.UpToDate -> "Al día"
        is com.bryan.corolladash.update.UpdateState.Available -> "Instalar ${u.versionName}"
        is com.bryan.corolladash.update.UpdateState.Downloading -> "Descargando ${u.percent} %"
        com.bryan.corolladash.update.UpdateState.Installing -> "Instalando…"
        com.bryan.corolladash.update.UpdateState.NeedsPermission -> "Permite instalar y toca de nuevo"
        is com.bryan.corolladash.update.UpdateState.Error -> u.message
    }
    SettingRow(DashIcons.Download, "Actualizaciones", value) {
        when (st) {
            is com.bryan.corolladash.update.UpdateState.Available,
            com.bryan.corolladash.update.UpdateState.NeedsPermission -> updater.install()
            is com.bryan.corolladash.update.UpdateState.Downloading,
            com.bryan.corolladash.update.UpdateState.Installing -> Unit
            else -> updater.check(force = true)
        }
    }
}
