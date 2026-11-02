package com.bryan.corolladash.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Apps del sistema de la unidad. El package se elige en Ajustes y se guarda explícitamente.
 * [hints] solo sirven para ordenar sugerencias en el selector; nunca se elige una app por nombre.
 */
enum class ExternalApp(val key: String, val label: String, val hints: List<String>) {
    ANDROID_AUTO("pkg_android_auto", "Android Auto", listOf("android auto", "androidauto", "carplay", "csrplay", "csr", "zlink", "tlink", "carlink", "autokit", "projection", "auto", "link")),
    RADIO("pkg_radio", "Radio FM", listOf("radio", "fmam", "fm", "tuner")),
    MUSIC_BT("pkg_music_bt", "Música BT", listOf("btmusic", "música bt", "musica bt", "bt music", "bluetooth music", "a2dp")),
    PHONE_BT("pkg_phone_bt", "Teléfono BT", listOf("btphone", "teléfono bt", "telefono bt", "bt phone", "phone", "dialer", "teléfono")),
    EQUALIZER("pkg_equalizer", "Ecualizador", listOf("eq", "equalizer", "ecualizador", "dsp", "audio")),
    NAVIGATION("pkg_navigation", "Navegación", listOf("waze", "maps", "mapas", "navegación", "navigation", "sygic", "osmand", "here")),
}

/** Configuración persistente (SharedPreferences, mismo archivo que versiones anteriores). */
class AppConfig(context: Context) {
    private val prefs = context.getSharedPreferences("corolla_dash_config", Context.MODE_PRIVATE)

    init { migrate() }

    private val _packages = MutableStateFlow(ExternalApp.entries.associateWith { prefs.getString(it.key, "").orEmpty() })
    val packages: StateFlow<Map<ExternalApp, String>> = _packages.asStateFlow()

    private val _favorites = MutableStateFlow(
        prefs.getString(KEY_FAVORITES, "").orEmpty().split(',').filter { it.isNotBlank() }
    )
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _nightDim = MutableStateFlow(prefs.getBoolean(KEY_NIGHT, false))
    val nightDim: StateFlow<Boolean> = _nightDim.asStateFlow()

    /** Texto bajo "Radio FM" en Inicio. Es una etiqueta elegida por el usuario, no se lee de la radio. */
    private val _radioLabel = MutableStateFlow(prefs.getString(KEY_STATION, DEFAULT_STATION).orEmpty())
    val radioLabel: StateFlow<String> = _radioLabel.asStateFlow()

    /** Si Música BT no tiene sesión, mostrar otra app que esté reproduciendo (Spotify, etc.). */
    private val _otherMediaFallback = MutableStateFlow(prefs.getBoolean(KEY_MEDIA_FALLBACK, true))
    val otherMediaFallback: StateFlow<Boolean> = _otherMediaFallback.asStateFlow()

    /** Reposo automático tras N minutos sin tocar la pantalla con el vehículo detenido (0 = desactivado). */
    private val _autoRestMinutes = MutableStateFlow(prefs.getInt(KEY_AUTO_REST, 0))
    val autoRestMinutes: StateFlow<Int> = _autoRestMinutes.asStateFlow()

    fun setAutoRestMinutes(min: Int) {
        prefs.edit().putInt(KEY_AUTO_REST, min).apply()
        _autoRestMinutes.value = min
    }

    /** Botón y vista de letras en la pantalla de Música. */
    private val _lyricsEnabled = MutableStateFlow(prefs.getBoolean(KEY_LYRICS, true))
    val lyricsEnabled: StateFlow<Boolean> = _lyricsEnabled.asStateFlow()

    fun setLyricsEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_LYRICS, on).apply()
        _lyricsEnabled.value = on
    }

    /** Buscar la carátula en internet cuando Bluetooth no la envía. */
    private val _onlineArt = MutableStateFlow(prefs.getBoolean(KEY_ONLINE_ART, true))
    val onlineArt: StateFlow<Boolean> = _onlineArt.asStateFlow()

    fun setOnlineArt(on: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_ART, on).apply()
        _onlineArt.value = on
    }

    /** Última vista del Inicio (0 = completa con mapa, 1 = limpia). */
    var homePage: Int
        get() = prefs.getInt(KEY_HOME_PAGE, 0)
        set(v) { prefs.edit().putInt(KEY_HOME_PAGE, v).apply() }

    fun packageOf(app: ExternalApp): String = _packages.value[app].orEmpty()

    fun setPackage(app: ExternalApp, pkg: String) {
        prefs.edit().putString(app.key, pkg).apply()
        _packages.value = _packages.value + (app to pkg)
    }

    fun setFavorites(list: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, list.joinToString(",")).apply()
        _favorites.value = list
    }

    fun setNightDim(on: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT, on).apply()
        _nightDim.value = on
    }

    fun setRadioLabel(text: String) {
        prefs.edit().putString(KEY_STATION, text).apply()
        _radioLabel.value = text
    }

    fun setOtherMediaFallback(on: Boolean) {
        prefs.edit().putBoolean(KEY_MEDIA_FALLBACK, on).apply()
        _otherMediaFallback.value = on
    }

    /** Solo para no repetir la petición inicial de permisos en cada arranque. */
    var askedStartupPermissions: Boolean
        get() = prefs.getBoolean(KEY_ASKED_PERMS, false)
        set(v) { prefs.edit().putBoolean(KEY_ASKED_PERMS, v).apply() }

    /**
     * v1 → v2:
     *  - "pkg_bluetooth" (Bluetooth / Teléfono) se reparte en Música BT o Teléfono BT según el package.
     *  - Se eliminan claves de OBD/CANBUS/fuente de datos que ya no existen.
     *  Se conservan: Android Auto, Radio, Ecualizador, favoritas, modo noche, etiqueta de radio.
     */
    private fun migrate() {
        if (prefs.getInt(KEY_VERSION, 1) >= 2) return
        val e = prefs.edit()
        prefs.getString(LEGACY_BT, null)?.takeIf { it.isNotBlank() }?.let { old ->
            val looksMusic = old.contains("music", ignoreCase = true) || old.contains("a2dp", ignoreCase = true)
            val target = if (looksMusic) ExternalApp.MUSIC_BT else ExternalApp.PHONE_BT
            if (prefs.getString(target.key, null).isNullOrBlank()) e.putString(target.key, old)
        }
        LEGACY_KEYS.forEach { e.remove(it) }
        e.putInt(KEY_VERSION, 2).apply()
    }

    private companion object {
        const val KEY_VERSION = "config_version"
        const val KEY_FAVORITES = "favorites"
        const val KEY_NIGHT = "night_dim"
        const val KEY_STATION = "radio_station"
        const val KEY_MEDIA_FALLBACK = "media_other_fallback"
        const val KEY_ASKED_PERMS = "asked_startup_permissions"
        const val KEY_AUTO_REST = "auto_rest_minutes"
        const val KEY_LYRICS = "lyrics_enabled"
        const val KEY_ONLINE_ART = "online_art"
        const val KEY_HOME_PAGE = "home_page"
        const val DEFAULT_STATION = ""
        const val LEGACY_BT = "pkg_bluetooth"
        val LEGACY_KEYS = listOf(LEGACY_BT, "vehicle_source", "obd_address", "obd_name", "obd_link")
    }
}
