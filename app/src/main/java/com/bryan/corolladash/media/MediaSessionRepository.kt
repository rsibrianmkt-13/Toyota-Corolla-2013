package com.bryan.corolladash.media

import androidx.compose.runtime.Immutable

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import com.bryan.corolladash.settings.AppConfig
import com.bryan.corolladash.settings.ExternalApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class MediaState(
    /** El usuario concedió "Acceso a notificaciones" a esta app. */
    val hasAccess: Boolean = false,
    /** Hay una MediaSession elegida (Música BT o, si se permite, otra app reproduciendo). */
    val hasSession: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val mediaId: String? = null,
    /** Reproductor de origen ("Spotify") si la radio lo informa; para apps locales, su nombre. */
    val playerName: String? = null,
    /** La sesión es la música por Bluetooth del teléfono. */
    val isBluetooth: Boolean = false,
    val art: ImageBitmap? = null,
    /** De dónde salió la carátula (diagnóstico). */
    val artSource: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val positionUpdatedAt: Long = 0,
    val speed: Float = 1f,
    val isPlaying: Boolean = false,
    val sourcePackage: String? = null,
    /** PlaybackState.actions (0 = la app no declara acciones). */
    val actions: Long = 0,
    /** Aleatorio/repetir: solo se muestran si la app los declara (acciones de MediaSessionCompat). */
    val shuffleSupported: Boolean = false,
    val shuffleOn: Boolean = false,
    val repeatSupported: Boolean = false,
    /** PlaybackStateCompat.REPEAT_MODE_NONE / ONE / ALL. */
    val repeatMode: Int = PlaybackStateCompat.REPEAT_MODE_NONE,
) {
    val canSeek: Boolean get() = actions and PlaybackState.ACTION_SEEK_TO != 0L && durationMs > 0

    fun currentPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || positionUpdatedAt == 0L) return positionMs
        val p = positionMs + ((nowElapsed - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Una sesión activa tal como la ve Android (para la pantalla de diagnóstico). */
data class SessionInfo(
    val packageName: String,
    val playbackState: String,
    val title: String?,
    val artist: String?,
    val durationMs: Long,
    val hasArtBitmap: Boolean,
    val artUri: String?,
    val actions: String,
)

/**
 * Lee y controla la reproducción con la API pública de Android
 * (MediaSessionManager + NotificationListenerService).
 *
 * Prioridad de sesión:
 *  1. Música BT configurada y reproduciendo
 *  2. Música BT configurada aunque esté en pausa
 *  3. Otra app reproduciendo (solo si "Otras fuentes de música" está activo o Música BT no está configurada)
 *  4. Ninguna
 *
 * Carátula (sin Internet): bitmap de metadata → URI de metadata → notificación de la app Música BT → placeholder.
 */
class MediaSessionRepository(
    private val context: Context,
    private val config: AppConfig,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val component = ComponentName(context, MediaListenerService::class.java)

    private var controller: MediaController? = null
    /** Mismo controlador vía AndroidX: único modo público de leer/cambiar aleatorio y repetir. */
    private var compat: MediaControllerCompat? = null
    private var shuffleLocal: Boolean? = null
    private var repeatLocal: Int? = null
    private var listenerRegistered = false

    // Caché de carátula: una sola imagen, ligada a la canción.
    private var artKey: String? = null
    private var art: ImageBitmap? = null
    private var artSource: String? = null
    private var artSourceBitmap: Bitmap? = null
    private var artJob: Job? = null
    private var artTriedAt = 0L
    private val online = OnlineArt(context)

    /** Origen del último intento en línea (Diagnóstico). */
    val onlineArtResult: String? get() = online.lastResult

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Si Música BT empieza a reproducir mientras mostramos otra app, cambiar a ella.
            if (state?.state == PlaybackState.STATE_PLAYING) refresh() else publish()
        }
        override fun onSessionDestroyed() = refresh()
    }

    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onShuffleModeChanged(shuffleMode: Int) = publish()
        override fun onRepeatModeChanged(repeatMode: Int) = publish()
        override fun onSessionReady() = publish()
    }

    init {
        // Re-elegir sesión si cambian el package de Música BT o la opción de otras fuentes.
        scope.launch {
            combine(config.packages, config.otherMediaFallback) { p, f -> p[ExternalApp.MUSIC_BT] to f }
                .drop(1)
                .collect { refresh() }
        }
        // Vigilancia: al encender la radio la sesión de Bluetooth puede aparecer tarde o sin aviso.
        // Mientras no haya sesión, revisar cada 3 s (muy barato).
        scope.launch {
            while (true) {
                delay(3_000)
                if (controller == null) refresh()
            }
        }
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Llamar al volver a primer plano y cuando el servicio de notificaciones se conecta. */
    fun refresh() {
        // Si Android aún no conectó el servicio de notificaciones (arranque de la radio), pedirlo.
        if (MediaListenerService.instance == null && hasAccess()) {
            runCatching { android.service.notification.NotificationListenerService.requestRebind(component) }
        }
        if (!hasAccess() || msm == null) {
            release()
            _state.value = MediaState(hasAccess = false)
            return
        }
        try {
            if (!listenerRegistered) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, component, main)
                listenerRegistered = true
            }
            pick(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            _state.value = MediaState(hasAccess = false)
        }
    }

    private fun pick(list: List<MediaController>) {
        val musicPkg = config.packageOf(ExternalApp.MUSIC_BT)
        val allowOthers = musicPkg.isBlank() || config.otherMediaFallback.value
        fun playing(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val chosen =
            list.firstOrNull { it.packageName == musicPkg && playing(it) }
                ?: list.firstOrNull { it.packageName == musicPkg }
                ?: (if (allowOthers) list.firstOrNull { playing(it) } else null)
                ?: (if (musicPkg.isBlank()) list.firstOrNull() else null)

        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            compat?.unregisterCallback(compatCallback)
            controller = chosen
            chosen?.registerCallback(callback, main)
            compat = chosen?.let { c ->
                runCatching { MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(c.sessionToken)) }.getOrNull()
            }
            compat?.registerCallback(compatCallback, main)
            shuffleLocal = null
            repeatLocal = null
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            clearArt()
            _state.value = MediaState(hasAccess = true, hasSession = false)
            return
        }
        val md = c.metadata
        val pb = c.playbackState

        val title = md.text(MediaMetadata.METADATA_KEY_TITLE) ?: md.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = md.text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)

        updateArt(c, md, title, artist)

        val actions = pb?.actions ?: 0L
        val cShuffle = runCatching { compat?.shuffleMode }.getOrNull() ?: PlaybackStateCompat.SHUFFLE_MODE_INVALID
        val cRepeat = runCatching { compat?.repeatMode }.getOrNull() ?: PlaybackStateCompat.REPEAT_MODE_INVALID

        _state.value = MediaState(
            hasAccess = true,
            hasSession = true,
            title = title,
            artist = artist,
            album = md.text(MediaMetadata.METADATA_KEY_ALBUM),
            playerName = playerNameOf(c, isBt(c.packageName)),
            isBluetooth = isBt(c.packageName),
            mediaId = md.text(MediaMetadata.METADATA_KEY_MEDIA_ID),
            art = art,
            artSource = artSource,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = pb?.position?.takeIf { it >= 0 } ?: 0L,
            positionUpdatedAt = pb?.lastPositionUpdateTime ?: 0,
            speed = pb?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            isPlaying = pb?.state == PlaybackState.STATE_PLAYING,
            sourcePackage = c.packageName,
            actions = actions,
            shuffleSupported = actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L ||
                cShuffle != PlaybackStateCompat.SHUFFLE_MODE_INVALID,
            shuffleOn = if (cShuffle != PlaybackStateCompat.SHUFFLE_MODE_INVALID) cShuffle != PlaybackStateCompat.SHUFFLE_MODE_NONE
                else shuffleLocal ?: false,
            repeatSupported = actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE != 0L ||
                cRepeat != PlaybackStateCompat.REPEAT_MODE_INVALID,
            repeatMode = when (val r = if (cRepeat != PlaybackStateCompat.REPEAT_MODE_INVALID) cRepeat else repeatLocal ?: PlaybackStateCompat.REPEAT_MODE_NONE) {
                PlaybackStateCompat.REPEAT_MODE_GROUP -> PlaybackStateCompat.REPEAT_MODE_ALL
                else -> r
            },
        )
    }

    // ------------------------------------------------------------------ Origen de la música

    private fun isBt(pkg: String): Boolean {
        val music = config.packageOf(ExternalApp.MUSIC_BT)
        val p = pkg.lowercase()
        return (music.isNotBlank() && pkg == music) || "bluetooth" in p || "btmusic" in p || "a2dp" in p || p.endsWith(".bt")
    }

    private val labelCache = HashMap<String, String?>()

    /**
     * Bluetooth: algunas radios publican el reproductor del teléfono (Spotify, YouTube Music…) en el título
     * de la cola o en los extras de la sesión; si no está, null. Apps de la radio: su nombre.
     */
    @Suppress("DEPRECATION")
    private fun playerNameOf(c: MediaController, bt: Boolean): String? {
        if (!bt) {
            return labelCache.getOrPut(c.packageName) {
                runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
                }.getOrNull()
            }
        }
        fun ok(s: String?) = s?.trim()?.takeIf { it.length in 2..30 && !it.contains("bluetooth", true) }
        runCatching { ok(c.queueTitle?.toString()) }.getOrNull()?.let { return it }
        runCatching {
            val ex = c.extras ?: return null
            for (k in ex.keySet()) {
                val key = k.lowercase()
                if ("player" in key || "app_name" in key || "source" in key || "package" in key) {
                    ok((ex.get(k) as? CharSequence)?.toString())?.let { return it }
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ Carátula

    private fun updateArt(c: MediaController, md: MediaMetadata?, title: String?, artist: String?) {
        // Clave estable por canción. No se usa Media ID ni álbum: algunas radios los cambian mientras suena.
        val key = listOf(c.packageName, norm(title), norm(artist)).joinToString("|")
        val metaBitmap = md?.bitmapArt()

        when {
            key != artKey -> {
                // Canción nueva: nunca mantener la carátula anterior.
                artKey = key
                art = null; artSource = null; artSourceBitmap = null
                resolveArt(key, c.packageName, md, title, artist)
            }
            // Llegó la imagen en la metadata y la actual vino de otro lado: usarla. (No se compara la instancia:
            // Android entrega un Bitmap nuevo en cada lectura y eso cancelaba la búsqueda una y otra vez al reproducir.)
            metaBitmap != null && artSource != SRC_META && artJob?.isActive != true -> resolveArt(key, c.packageName, md, title, artist)
            // Sin carátula aún: reintentar sin interrumpir una búsqueda en curso y como mucho cada 5 s.
            art == null && artJob?.isActive != true && SystemClock.elapsedRealtime() - artTriedAt > 5_000 ->
                resolveArt(key, c.packageName, md, title, artist)
        }
    }

    private fun resolveArt(key: String, sessionPkg: String, md: MediaMetadata?, title: String?, artist: String?) {
        artJob?.cancel()
        artTriedAt = SystemClock.elapsedRealtime()
        artJob = scope.launch {
            var found = withContext(Dispatchers.IO) { findArt(sessionPkg, md, title) }
            if (found == null && config.onlineArt.value) {
                // Dar tiempo a que la app actualice su notificación (suele llegar un poco después).
                delay(1_500)
                if (artKey != key) return@launch
                found = withContext(Dispatchers.IO) {
                    findArt(sessionPkg, md, title)
                        ?: online.find(title, artist)?.let { downscale(it) to "internet (iTunes)" }
                }
            }
            if (found != null && artKey == key) {
                art = found.first.asImageBitmap()
                artSource = found.second
                artSourceBitmap = md?.bitmapArt()
                _state.value = _state.value.copy(art = art, artSource = artSource)
            }
        }
    }

    /** Devuelve (imagen reducida, origen) o null. Nunca lanza excepciones. */
    private fun findArt(sessionPkg: String, md: MediaMetadata?, title: String?): Pair<Bitmap, String>? {
        // Nivel 1 — Bitmap en metadata
        md?.bitmapArt()?.let { return downscale(it) to SRC_META }

        // Nivel 2 — URI en metadata (solo contenido local; nada de Internet)
        for (k in ART_URI_KEYS) {
            val uri = md.text(k) ?: continue
            decodeUri(uri)?.let { return it to "metadata (URI)" }
        }

        // Nivel 3 — Notificación de la MISMA app que reproduce (esté o no configurada como Música BT),
        // solo si la notificación ya muestra esta canción.
        notificationArt(sessionPkg, title)?.let { return it to "notificación" }

        // Nivel 4 — internet (en resolveArt) · Nivel 5 — placeholder (lo dibuja la UI)
        return null
    }

    private fun decodeUri(raw: String): Bitmap? = runCatching {
        val uri = Uri.parse(raw)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("content", "file", "android.resource")) return null
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_ART) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.let { downscale(it) }
    }.onFailure { Log.d(TAG, "URI de carátula no accesible: $raw (${it.javaClass.simpleName})") }.getOrNull()

    @Suppress("DEPRECATION")
    private fun notificationArt(pkg: String, title: String?): Bitmap? = runCatching {
        val service = MediaListenerService.instance ?: return null
        val sbns = service.activeNotifications.orEmpty().filter { it.packageName == pkg }
        for (sbn in sbns.sortedByDescending { it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }) {
            val n = sbn.notification
            val ex = n.extras
            // Evitar la carátula de la canción anterior: el título de la notificación debe coincidir.
            val nTitle = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            if (title != null && nTitle != null && !sameTitle(title, nTitle)) continue
            val candidates = listOf<Any?>(
                n.getLargeIcon(),
                ex.getParcelable<android.os.Parcelable>(Notification.EXTRA_LARGE_ICON_BIG),
                ex.getParcelable<android.os.Parcelable>(Notification.EXTRA_LARGE_ICON),
                ex.getParcelable<android.os.Parcelable>(Notification.EXTRA_PICTURE),
            )
            for (c in candidates) {
                val bmp = when (c) {
                    is Bitmap -> c
                    is Icon -> runCatching { c.loadDrawable(context)?.toBitmap() }.getOrNull()
                    else -> null
                } ?: continue
                // Ignorar iconos diminutos (suelen ser el logo de la app, no una carátula).
                if (minOf(bmp.width, bmp.height) >= 96) return downscale(bmp)
            }
        }
        null
    }.onFailure { Log.d(TAG, "Sin carátula en notificación: ${it.javaClass.simpleName}") }.getOrNull()

    /** Llamado por el servicio cuando la app Música BT publica/actualiza su notificación. */
    fun onNotificationChanged(pkg: String) {
        if (controller?.packageName == pkg && art == null) {
            main.post {
                artJob?.cancel()
                artTriedAt = 0L
                publish()
            }
        }
    }

    private fun clearArt() {
        artJob?.cancel()
        artKey = null; art = null; artSource = null; artSourceBitmap = null
    }

    // ------------------------------------------------------------------ Controles

    private fun has(action: Long): Boolean {
        val a = controller?.playbackState?.actions ?: 0L
        return a == 0L || a and action != 0L   // 0 = la app no declara: intentar igual
    }

    fun playPause() {
        val c = controller ?: return sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        if (_state.value.isPlaying) {
            if (has(PlaybackState.ACTION_PAUSE) || has(PlaybackState.ACTION_PLAY_PAUSE)) c.transportControls.pause()
            else sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else {
            if (has(PlaybackState.ACTION_PLAY) || has(PlaybackState.ACTION_PLAY_PAUSE)) c.transportControls.play()
            else sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    fun next() {
        val c = controller
        if (c != null && has(PlaybackState.ACTION_SKIP_TO_NEXT)) c.transportControls.skipToNext()
        else sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        val c = controller
        if (c != null && has(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) c.transportControls.skipToPrevious()
        else sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /** Aleatorio: apagado ↔ todo. */
    fun toggleShuffle() {
        val tc = compat?.transportControls ?: return
        val on = !_state.value.shuffleOn
        tc.setShuffleMode(if (on) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
        // Si la app no informa el modo, se refleja localmente lo pedido.
        shuffleLocal = on
        _state.value = _state.value.copy(shuffleOn = on)
    }

    /** Repetir: no → todas → una → no. */
    fun cycleRepeat() {
        val tc = compat?.transportControls ?: return
        val next = when (_state.value.repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
            PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
        tc.setRepeatMode(next)
        repeatLocal = next
        _state.value = _state.value.copy(repeatMode = next)
    }

    fun seekTo(ms: Long) {
        if (_state.value.canSeek) controller?.transportControls?.seekTo(ms)
    }

    /** Fallback: tecla multimedia estándar. La recibe la app que tenga el foco de audio. */
    private fun sendKey(code: Int) {
        val a = audio ?: return
        a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        // La tecla puede despertar la sesión de Bluetooth: volver a buscarla.
        main.postDelayed({ refresh() }, 1_200)
        main.postDelayed({ refresh() }, 3_500)
    }

    // ------------------------------------------------------------------ Diagnóstico

    fun debugSessions(): List<SessionInfo> = runCatching {
        if (!hasAccess() || msm == null) return emptyList()
        msm.getActiveSessions(component).map { c ->
            val md = c.metadata
            val pb = c.playbackState
            SessionInfo(
                packageName = c.packageName,
                playbackState = stateName(pb?.state),
                title = md.text(MediaMetadata.METADATA_KEY_TITLE),
                artist = md.text(MediaMetadata.METADATA_KEY_ARTIST),
                durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
                hasArtBitmap = md?.bitmapArt() != null,
                artUri = ART_URI_KEYS.firstNotNullOfOrNull { md.text(it) },
                actions = actionNames(pb?.actions ?: 0L),
            )
        }.also { list -> list.forEach { Log.i(TAG, "Sesión: $it") } }
    }.getOrDefault(emptyList())

    fun release() {
        controller?.unregisterCallback(callback)
        compat?.unregisterCallback(compatCallback)
        compat = null
        controller = null
        clearArt()
        if (listenerRegistered) {
            msm?.removeOnActiveSessionsChangedListener(sessionsListener)
            listenerRegistered = false
        }
    }

    private fun downscale(b: Bitmap, max: Int = MAX_ART): Bitmap {
        val s = max.toFloat() / maxOf(b.width, b.height)
        return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }

    private companion object {
        const val TAG = "CorollaMedia"
        const val MAX_ART = 512
        val ART_URI_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )

        const val SRC_META = "metadata (bitmap)"

        fun norm(s: String?): String = com.bryan.corolladash.lyrics.SongIdentity.normalize(s.orEmpty())

        fun sameTitle(a: String, b: String): Boolean {
            val x = com.bryan.corolladash.lyrics.SongIdentity.normalize(a)
            val y = com.bryan.corolladash.lyrics.SongIdentity.normalize(b)
            return x == y || x.startsWith(y) || y.startsWith(x)
        }

        fun MediaMetadata?.text(key: String): String? =
            this?.getString(key)?.takeIf { it.isNotBlank() }

        fun MediaMetadata.bitmapArt(): Bitmap? =
            getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        fun stateName(s: Int?): String = when (s) {
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_CONNECTING -> "CONNECTING"
            PlaybackState.STATE_NONE -> "NONE"
            PlaybackState.STATE_ERROR -> "ERROR"
            null -> "—"
            else -> "STATE_$s"
        }

        fun actionNames(a: Long): String {
            if (a == 0L) return "ninguna declarada"
            val names = listOf(
                PlaybackState.ACTION_PLAY to "play", PlaybackState.ACTION_PAUSE to "pause",
                PlaybackState.ACTION_PLAY_PAUSE to "play/pause", PlaybackState.ACTION_STOP to "stop",
                PlaybackState.ACTION_SKIP_TO_NEXT to "next", PlaybackState.ACTION_SKIP_TO_PREVIOUS to "previous",
                PlaybackState.ACTION_SEEK_TO to "seek",
            )
            return names.filter { a and it.first != 0L }.joinToString(", ") { it.second }
        }
    }
}
