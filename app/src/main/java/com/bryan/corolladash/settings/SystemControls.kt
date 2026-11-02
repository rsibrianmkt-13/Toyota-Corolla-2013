package com.bryan.corolladash.settings

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Lo que Android 15 permite a una app normal:
 *  - Volumen: directo.
 *  - Brillo: directo SOLO con el permiso especial WRITE_SETTINGS (se concede a mano).
 *  - Bluetooth / Wi-Fi / Hotspot: NO se pueden encender/apagar desde apps (API 29+/33+).
 *    Se abren el panel o la pantalla de ajustes correspondiente.
 */
class SystemControls(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    // Volumen multimedia
    fun volumeFraction(): Float {
        val a = audio ?: return 0f
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max == 0) 0f else a.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    /** Un paso de volumen (como las teclas físicas), sin mostrar el panel del sistema. Devuelve el nivel nuevo. */
    /**
     * Sube o baja en pasos de 10 % (0 → 10 → 20 … 100), ajustado al nivel más cercano del sistema.
     * Si 10 % es menos de un nivel, mueve al menos un nivel para que cada pulsación cambie algo.
     */
    fun stepVolume(up: Boolean): Float {
        val a = audio ?: return 0f
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        val slot = cur * 10f / max            // posición actual en la rejilla de 10 %
        val target = if (up) kotlin.math.floor(slot + 0.01f) + 1f else kotlin.math.ceil(slot - 0.01f) - 1f
        var idx = kotlin.math.round(target.coerceIn(0f, 10f) * max / 10f).toInt()
        if (idx == cur) idx = cur + if (up) 1 else -1
        idx = idx.coerceIn(0, max)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0)
        return idx.toFloat() / max
    }

    fun setVolume(fraction: Float) {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).toInt().coerceIn(0, max), 0)
    }

    // Brillo
    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun brightnessFraction(): Float = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)

    fun setBrightness(fraction: Float) {
        if (!canWriteSettings()) return
        val cr = context.contentResolver
        runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (fraction * 255).toInt().coerceIn(5, 255))
        }
    }

    // Accesos a pantallas del sistema
    fun open(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun openSettings() = open(Intent(Settings.ACTION_SETTINGS))
    fun openBluetooth() = open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun openWifi() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) open(Intent(Settings.Panel.ACTION_WIFI))
        else open(Intent(Settings.ACTION_WIFI_SETTINGS))
    /** No hay intent público para hotspot; se abre "Redes e Internet". */
    fun openHotspot() = open(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    fun openHomeSettings() = open(Intent(Settings.ACTION_HOME_SETTINGS))
    fun openDisplaySettings() = open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    fun openLocationSettings() = open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun openNotificationAccess() = open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openWriteSettingsPermission() = open(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
    fun openAppDetails() = open(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}
