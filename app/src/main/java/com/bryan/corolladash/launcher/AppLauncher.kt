package com.bryan.corolladash.launcher

import androidx.compose.runtime.Immutable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    /** Activity de inicio (para identificar apps propietarias de la VECHTEL). */
    val activity: String?,
)

/** Resultado de un intento de abrir una app (para diagnóstico, no se muestra al conductor). */
data class LaunchReport(
    val packageName: String,
    val label: String?,
    val installed: Boolean,
    val launchIntent: String?,
    val error: String?,
    val ok: Boolean,
    val atMillis: Long = System.currentTimeMillis(),
)

/**
 * Abre apps externas por package name y lista las apps instaladas.
 * La lista (con iconos) se carga una sola vez y se guarda; solo se recarga cuando Android
 * avisa que se instaló, actualizó o desinstaló una app.
 */
class AppLauncher(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    @Volatile private var dirty = true
    private val loadLock = Mutex()
    private val labels = ConcurrentHashMap<String, String>()

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                dirty = true
                labels.clear()
            }
        }
        runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
    }

    /** Carga la lista si aún no existe o si cambiaron las apps instaladas. Barato si no hay cambios. */
    suspend fun ensureLoaded() {
        if (!dirty && _apps.value != null) return
        loadLock.withLock {
            if (!dirty && _apps.value != null) return
            dirty = false
            _apps.value = loadLaunchableApps()
        }
    }

    /** Últimos intentos (máx. 10) para la pantalla de diagnóstico. */
    val recentLaunches = ArrayDeque<LaunchReport>()

    /** Devuelve false si el paquete no está configurado, no existe o no se pudo abrir. */
    fun launch(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val label = labelOf(packageName)
        val installed = label != null
        // 1) Intent de inicio estándar. 2) Si no existe, cualquier Activity MAIN del package.
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName).takeIf { pm.resolveActivity(it, 0) != null }
        var error: String? = null
        val ok = if (intent == null) {
            error = if (installed) "Sin Activity de inicio" else "No instalada"
            false
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { context.startActivity(intent) }
                .onFailure { error = "${it.javaClass.simpleName}: ${it.message}" }
                .isSuccess
        }
        val report = LaunchReport(packageName, label, installed, intent?.component?.flattenToShortString() ?: intent?.toString(), error, ok)
        if (!ok) Log.w(TAG, "No se pudo abrir: $report") else Log.i(TAG, "Abierta: $packageName")
        recentLaunches.addFirst(report)
        while (recentLaunches.size > 10) recentLaunches.removeLast()
        return ok
    }

    fun labelOf(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        labels[packageName]?.let { return it }
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()?.also { labels[packageName] = it }
    }

    fun isInstalled(packageName: String?): Boolean = labelOf(packageName) != null

    /** Apps con icono en el menú. Se carga en IO y solo cuando hace falta. */
    private suspend fun loadLaunchableApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(query, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .mapNotNull { info ->
                runCatching {
                    AppEntry(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = info.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
                        activity = info.name,
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private companion object {
        const val TAG = "CorollaLauncher"
    }
}
