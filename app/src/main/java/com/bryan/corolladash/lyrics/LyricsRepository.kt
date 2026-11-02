package com.bryan.corolladash.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.bryan.corolladash.media.MediaState
import com.bryan.corolladash.settings.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Letras de la canción actual. Totalmente separado de MediaSessionRepository: solo lee su estado.
 *
 * Flujo por canción (una sola vez):
 *  identidad nueva → limpiar (Loading) → caché memoria → caché disco → sin red: Offline → proveedor.
 * Un cambio de canción cancela la búsqueda anterior (collectLatest), así nunca se ve la letra previa.
 * Cambios de posición, pausa o seek NO generan consultas.
 */
class LyricsRepository(
    context: Context,
    private val config: AppConfig,
    private val media: StateFlow<MediaState>,
    scope: CoroutineScope,
    private val provider: LyricsProvider = LrclibProvider(),
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val cache = LyricsCache(context.applicationContext)

    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private val retryTick = MutableStateFlow(0)

    /** Último error técnico (solo Diagnóstico). */
    @Volatile var lastError: String? = null
        private set

    init {
        scope.launch {
            combine(
                media.map { songOf(it) }.distinctUntilChanged { a, b -> a?.cacheKey == b?.cacheKey },
                config.lyricsEnabled,
                retryTick,
            ) { song, enabled, _ -> if (enabled) song else null }
                .collectLatest { song -> load(song) }
        }
    }

    /** Reintentar tras Offline/Error (p. ej. al abrir la letra). */
    fun retry() {
        val r = _state.value.result
        if (r is LyricsResult.Offline || r is LyricsResult.Error) retryTick.value++
    }

    private suspend fun load(song: SongIdentity?) {
        if (song == null) {
            _state.value = LyricsState()
            return
        }
        // Limpiar de inmediato: nunca mostrar la letra de la canción anterior.
        _state.value = LyricsState(song, LyricsResult.Loading, provider.name)
        // Los metadatos de Bluetooth suelen llegar por partes (título, luego duración): esperar a que se asienten
        // y usar los datos más recientes de la misma canción (duración y álbum para la búsqueda).
        delay(SETTLE_MS)
        val song = songOf(media.value)?.takeIf { it.cacheKey == song.cacheKey } ?: song

        val key = song.cacheKey
        cache.memory(key)?.let {
            _state.value = LyricsState(song, it, provider.name, fromCache = true)
            return
        }
        withContext(Dispatchers.IO) { cache.disk(key) }?.let {
            cache.putMemory(key, it)
            _state.value = LyricsState(song, it, provider.name, fromCache = true)
            return
        }
        if (!online()) {
            _state.value = LyricsState(song, LyricsResult.Offline, provider.name)
            return
        }
        val result = try {
            val found = withContext(Dispatchers.IO) { provider.fetch(song) }
            lastError = null
            if (found == null) {
                LyricsResult.NotFound
            } else {
                withContext(Dispatchers.IO) { cache.putDisk(key, found) }
                toResult(found)
            }
        } catch (e: IOException) {
            lastError = "${e.javaClass.simpleName}: ${e.message} · causa: ${e.cause?.javaClass?.simpleName ?: "—"}"
            if (online()) LyricsResult.Error(e.message ?: e.javaClass.simpleName) else LyricsResult.Offline
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(TAG, "Error de letra", e)
            LyricsResult.Error(lastError.orEmpty())
        }
        // No encontrada también queda en memoria: una sola consulta por canción en esta sesión.
        if (result !is LyricsResult.Error && result !is LyricsResult.Offline) cache.putMemory(key, result)
        _state.value = LyricsState(song, result, provider.name)
    }

    private fun toResult(p: ProviderLyrics): LyricsResult {
        if (p.instrumental) return LyricsResult.Instrumental
        p.synced?.let { lrc ->
            val lines = LrcParser.parse(lrc)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        p.plain?.let { return LyricsResult.Plain(it.lines()) }
        return LyricsResult.NotFound
    }

    private fun online(): Boolean = runCatching {
        val c = cm ?: return false
        val caps = c.getNetworkCapabilities(c.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    /** Líneas para Diagnóstico (debug). */
    fun diagnostics(): List<Pair<String, String>> {
        val s = _state.value
        val r = s.result
        return listOf(
            "Función activada" to (if (config.lyricsEnabled.value) "sí" else "no"),
            "Identidad" to (s.song?.cacheKey ?: "—"),
            "Media ID" to (s.song?.mediaId ?: "—"),
            "Título / artista" to "${s.song?.title ?: "—"} / ${s.song?.artist ?: "—"}",
            "Álbum" to (s.song?.album ?: "—"),
            "Duración" to (s.song?.let { "${it.durationMs / 1000} s" } ?: "—"),
            "Proveedor" to provider.name,
            "Desde caché" to (if (s.fromCache) "sí" else "no"),
            "Tipo" to when (r) {
                is LyricsResult.Synced -> "sincronizada"
                is LyricsResult.Plain -> "normal (sin sincronizar)"
                LyricsResult.Instrumental -> "instrumental"
                LyricsResult.NotFound -> "no encontrada"
                LyricsResult.Offline -> "sin conexión"
                LyricsResult.Loading -> "buscando"
                LyricsResult.Idle -> "—"
                is LyricsResult.Error -> "error"
            },
            "Líneas" to when (r) {
                is LyricsResult.Synced -> r.lines.size.toString()
                is LyricsResult.Plain -> r.lines.size.toString()
                else -> "0"
            },
            "Última respuesta" to (provider.lastResponse ?: "—"),
            "Error técnico" to (lastError ?: "—"),
            "Letras guardadas en disco" to cache.count().toString(),
        )
    }

    private companion object {
        const val TAG = "CorollaLyrics"
        const val SETTLE_MS = 700L

        fun songOf(m: MediaState): SongIdentity? {
            val title = m.title?.trim()?.takeIf { m.hasSession && it.isNotEmpty() } ?: return null
            return SongIdentity(
                title = title,
                artist = m.artist?.trim().orEmpty(),
                album = m.album?.trim()?.takeIf { it.isNotEmpty() },
                // Redondeado a segundos: pequeñas variaciones de la duración no cuentan como canción nueva.
                durationMs = (m.durationMs / 1000) * 1000,
                mediaId = m.mediaId,
            )
        }
    }
}
