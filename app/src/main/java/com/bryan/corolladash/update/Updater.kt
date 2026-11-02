package com.bryan.corolladash.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Estado del actualizador (se muestra en Ajustes → Sistema). */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val versionName: String, val versionCode: Long) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object Installing : UpdateState
    data object NeedsPermission : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Actualizaciones desde GitHub: cada push a main publica en la rama `apk` el APK y `version.json`
 * (GitHub Actions). La app compara su versionCode con el publicado, descarga el APK y lo instala
 * con PackageInstaller. Android pide confirmar la primera vez; después, al ser la app su propia
 * instaladora, puede actualizarse sin preguntar (Android 12+, si la radio lo permite).
 */
class Updater(private val app: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private var lastCheck = 0L

    val currentVersionCode: Long = runCatching {
        val pi = app.packageManager.getPackageInfo(app.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    /** Busca actualización (como mucho una vez por hora si [force] es false). */
    fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 60 * 60 * 1000L) return
        val s = _state.value
        if (s is UpdateState.Downloading || s is UpdateState.Installing || s is UpdateState.Checking) return
        lastCheck = now
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val json = withContext(Dispatchers.IO) { JSONObject(httpText("$BASE/version.json?t=$now")) }
                val code = json.getLong("versionCode")
                if (code > currentVersionCode) UpdateState.Available(json.optString("versionName", code.toString()), code)
                else UpdateState.UpToDate
            } catch (e: Exception) {
                Log.d(TAG, "check: $e")
                UpdateState.Error("Sin conexión con GitHub")
            }
        }
    }

    /** Descarga e instala la versión publicada. */
    fun install() {
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(i) }
            return
        }
        scope.launch {
            try {
                _state.value = UpdateState.Downloading(0)
                val apk = withContext(Dispatchers.IO) { download("$BASE/$APK_NAME?t=${System.currentTimeMillis()}") }
                _state.value = UpdateState.Installing
                withContext(Dispatchers.IO) { installApk(apk) }
            } catch (e: Exception) {
                Log.d(TAG, "install: $e")
                _state.value = UpdateState.Error("No se pudo descargar la actualización")
            }
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            _state.value = when (status) {
                PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Idle
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    UpdateState.Error("Firma distinta: desinstala e instala el APK de GitHub una vez")
                else -> UpdateState.Error(message ?: "La instalación falló")
            }
        }
    }

    private fun httpText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    private fun download(url: String): File {
        val out = File(app.cacheDir, "update.apk")
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; _state.value = UpdateState.Downloading(pct) }
                        }
                    }
                }
            }
        } finally { c.disconnect() }
        return out
    }

    private fun installApk(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(app, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CorollaDash updater")
            setRequestProperty("Cache-Control", "no-cache")
        }

    companion object {
        const val TAG = "CorollaUpdate"
        const val BASE = "https://raw.githubusercontent.com/rsibrianmkt-13/Toyota-Corolla-2013/apk"
        const val APK_NAME = "CorollaDash-latest.apk"
    }
}

/** Resultado de PackageInstaller: si Android pide confirmación, abre su diálogo. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        val c = (context.applicationContext as? com.bryan.corolladash.CorollaDashApp)?.container
        c?.updater?.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
