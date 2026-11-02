package com.bryan.corolladash.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.support.v4.media.session.PlaybackStateCompat
import com.bryan.corolladash.lyrics.LyricsState
import com.bryan.corolladash.media.MediaState
import com.bryan.corolladash.ui.music.LyricsBottomSheet
import com.bryan.corolladash.ui.music.LyricsScreen
import com.bryan.corolladash.ui.music.LyricsPage
import com.bryan.corolladash.ui.components.CircleButton
import com.bryan.corolladash.ui.components.DashCard
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.PillButton
import com.bryan.corolladash.ui.components.TextAt
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.components.tight
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MusicActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val grantAccess: () -> Unit,
    val openBluetooth: () -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    /** Un paso de volumen (true = subir); devuelve el nivel nuevo 0–1. */
    val volumeStep: (Boolean) -> Float,
    /** Nivel actual 0–1 (para mostrarlo junto a los botones de volumen). */
    val volumeLevel: () -> Float = { 0f },
)

/** Situación real de la música, en orden de prioridad. */
private enum class MusicStatus { NO_ACCESS, PLAYING, IDLE, DISCONNECTED }

private fun status(m: MediaState, phoneConnected: Boolean): MusicStatus = when {
    !m.hasAccess -> MusicStatus.NO_ACCESS
    m.hasSession && m.title != null -> MusicStatus.PLAYING
    m.hasSession || phoneConnected -> MusicStatus.IDLE
    else -> MusicStatus.DISCONNECTED
}

/** Texto, subtítulo y acción de cada estado sin canción. */
private data class EmptyCopy(val title: String, val subtitle: String, val action: String?)

private fun emptyCopy(s: MusicStatus): EmptyCopy = when (s) {
    MusicStatus.NO_ACCESS -> EmptyCopy("Permitir acceso multimedia", "Necesario para ver la canción", "Permitir")
    MusicStatus.DISCONNECTED -> EmptyCopy("Conecta tu teléfono", "Bluetooth", "Abrir Bluetooth")
    else -> EmptyCopy("Sin reproducción", "Reproduce música desde tu teléfono", null)
}

// ---------------------------------------------------------------- Tarjeta de Inicio (540×415)

/** Origen visible: ("Spotify", "Bluetooth · iPhone de Bryan") o ("Música Bluetooth", "iPhone de Bryan"). */
internal fun sourceLines(m: MediaState, phoneName: String?): Pair<String, String?> = when {
    m.isBluetooth && m.playerName != null -> m.playerName to listOfNotNull("Bluetooth", phoneName).joinToString(" · ")
    m.isBluetooth || !m.hasSession -> "Música Bluetooth" to phoneName
    else -> (m.playerName ?: "Música") to null
}

private val MusicCardBg = Color(0xFF0C0D0E)   // 30 % más oscuro que las otras tarjetas

@Composable
fun HomeMusicCard(
    m: MediaState,
    phoneConnected: Boolean,
    phoneName: String?,
    actions: MusicActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val st = status(m, phoneConnected)
    val enabled = st == MusicStatus.PLAYING || st == MusicStatus.IDLE
    val swipe = rememberSwipe()
    val volume = rememberVolumeHud()
    val (source, sub) = sourceLines(m, phoneName)
    DashCard(modifier.trackSwipe(enabled, swipe, actions).holdForVolume(volume, actions), onClick = onOpen) {
        Box(Modifier.fillMaxSize().background(MusicCardBg))
        // Encabezado: reproductor de origen y teléfono (tarjeta 363×469 del Inicio)
        Box(Modifier.at(24, 24, 40, 40).clip(CircleShape).background(Dash.Well), contentAlignment = Alignment.Center) {
            DashIcon(DashIcons.Bt, 22, Dash.Icon)
        }
        if (sub != null) {
            TextAt(source, 76, 26, DashType.Body.copy(fontSize = 18.sp, color = Dash.Text), maxWidth = 263.dp)
            TextAt(sub, 76, 49, DashType.Footnote.copy(fontSize = 13.sp, color = Color(0xFF8E8E93)), maxWidth = 263.dp)
        } else {
            TextAt(source, 76, 36, DashType.Label.copy(fontSize = 18.sp), maxWidth = 263.dp)
        }

        // Carátula y textos acompañan el dedo al deslizar.
        Box(Modifier.fillMaxSize().swipeFollow(swipe)) {
            Artwork(m.art, Modifier.at(24, 80, 124, 124), radius = 14)
            if (st == MusicStatus.PLAYING) {
                TextAt(m.title.orEmpty(), 166, 104, DashType.Headline.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp), maxWidth = 173.dp)
                m.artist?.let { TextAt(it, 166, 136, DashType.Label.copy(fontSize = 16.sp, color = Dash.TextReadable), maxWidth = 173.dp) }
                if (m.durationMs > 0) Progress(m, x = 24, y = 276, w = 315, seekable = false, onSeek = {})
            } else {
                CompactEmptyText(st, actions, Modifier.offset(166.dp, 88.dp).width(173.dp))
            }
        }

        Controls(m, actions, centerX = 181, centerY = 385, playSize = 88, enabled = enabled, side = 64, gap = 104)
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}

/**
 * Música de la vista limpia del Inicio (495×469): sin tarjeta ni carátula, integrada al fondo.
 * Mismos gestos: deslizar para cambiar de canción y mantener presionado para el volumen.
 */
@Composable
fun CleanMusicPanel(
    m: MediaState,
    phoneConnected: Boolean,
    phoneName: String?,
    actions: MusicActions,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val st = status(m, phoneConnected)
    val enabled = st == MusicStatus.PLAYING || st == MusicStatus.IDLE
    val swipe = rememberSwipe()
    val volume = rememberVolumeHud()
    val (source, sub) = sourceLines(m, phoneName)
    val w = 495
    Box(
        modifier
            .trackSwipe(enabled, swipe, actions)
            .holdForVolume(volume, actions)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onOpen)
    ) {
        Row(Modifier.offset(0.dp, 44.dp), verticalAlignment = Alignment.CenterVertically) {
            DashIcon(DashIcons.Bt, 18, Color(0xFF8E8E93))
            Spacer(Modifier.width(10.dp))
            Text(source, style = DashType.Label.copy(fontSize = 16.sp, color = Dash.Icon), maxLines = 1)
            sub?.let { Text(" · $it", style = DashType.Label.copy(fontSize = 16.sp, color = Color(0xFF8E8E93)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Box(Modifier.fillMaxSize().swipeFollow(swipe)) {
            if (st == MusicStatus.PLAYING) {
                TextAt(m.title.orEmpty(), 0, 128, DashType.LargeTitle.copy(fontSize = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp), maxWidth = w.dp)
                m.artist?.let { TextAt(it, 0, 182, DashType.Body.copy(fontSize = 23.sp, color = Dash.TextReadable), maxWidth = w.dp) }
                if (m.durationMs > 0) Progress(m, x = 0, y = 256, w = w, seekable = m.canSeek, onSeek = actions.seekTo)
            } else {
                EmptyText(st, x = 0, y = 140, w = w, actions = actions, large = true)
            }
        }
        Controls(m, actions, centerX = w / 2, centerY = 385, playSize = 116, enabled = enabled, side = 80, gap = 160)
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}

/**
 * Música compacta de la vista limpia (290×300): el carro domina la escena y la música queda a un lado,
 * sin tarjeta ni carátula. Mismos gestos que las otras vistas.
 */
@Composable
fun CompactCleanMusicPanel(
    m: MediaState,
    phoneConnected: Boolean,
    phoneName: String?,
    actions: MusicActions,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val st = status(m, phoneConnected)
    val enabled = st == MusicStatus.PLAYING || st == MusicStatus.IDLE
    val swipe = rememberSwipe()
    val volume = rememberVolumeHud()
    val (source, sub) = sourceLines(m, phoneName)
    val w = 290
    Box(
        modifier
            .trackSwipe(enabled, swipe, actions)
            .holdForVolume(volume, actions)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onOpen)
    ) {
        Row(Modifier.offset(0.dp, 0.dp).width(w.dp), verticalAlignment = Alignment.CenterVertically) {
            DashIcon(DashIcons.Bt, 15, Color(0xFF8E8E93))
            Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(source, sub).joinToString(" · "),
                style = DashType.Label.copy(fontSize = 14.sp, color = Color(0xFF8E8E93)),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxSize().swipeFollow(swipe)) {
            if (st == MusicStatus.PLAYING) {
                TextAt(m.title.orEmpty(), 0, 32, DashType.Title.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp), maxWidth = w.dp)
                m.artist?.let { TextAt(it, 0, 70, DashType.Body.copy(fontSize = 18.sp, color = Dash.TextReadable), maxWidth = w.dp) }
                if (m.durationMs > 0) Progress(m, x = 0, y = 118, w = w, seekable = m.canSeek, onSeek = actions.seekTo)
            } else {
                EmptyText(st, x = 0, y = 36, w = w, actions = actions)
            }
        }
        Controls(m, actions, centerX = 104, centerY = 222, playSize = 64, enabled = enabled, side = 52, gap = 84)
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}

// ---------------------------------------------------------------- Pantalla completa (1125×571)

/** Vista de la pantalla Música: reproductor, hoja de letra (desde abajo) o letra a pantalla completa. */
enum class MusicView { NOW_PLAYING, SHEET, LYRICS }

@Composable
fun MusicScreen(
    m: MediaState,
    phoneConnected: Boolean,
    actions: MusicActions,
    lyrics: LyricsState,
    lyricsEnabled: Boolean,
    driving: Boolean,
    onLyricsOpened: () -> Unit,
) {
    val st = status(m, phoneConnected)
    var view by remember { mutableStateOf(MusicView.NOW_PLAYING) }
    val canLyrics = lyricsEnabled && st == MusicStatus.PLAYING
    LaunchedEffect(canLyrics) { if (!canLyrics) view = MusicView.NOW_PLAYING }
    // Atrás: letra completa / hoja → reproductor.
    BackHandler(enabled = view != MusicView.NOW_PLAYING) { view = MusicView.NOW_PLAYING }
    fun openLyrics(v: MusicView) {
        view = v
        onLyricsOpened()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            view == MusicView.LYRICS,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 6 }) togetherWith fadeOut(tween(180))
            },
            label = "lyrics",
        ) { full ->
            if (full) {
                BigLyricsScreen(m, lyrics, driving, actions, onClose = { view = MusicView.NOW_PLAYING })
            } else {
                NowPlaying(
                    m, st, actions,
                    canLyrics = canLyrics,
                    dimmed = view == MusicView.SHEET,
                    onLyrics = {
                        if (view == MusicView.NOW_PLAYING) {
                            openLyrics(MusicView.LYRICS)
                        } else {
                            view = MusicView.NOW_PLAYING
                        }
                    },
                    onSwipeUp = { openLyrics(MusicView.SHEET) },
                    onSwipeRight = { openLyrics(MusicView.LYRICS) },
                )
            }
        }
        AnimatedVisibility(
            view == MusicView.SHEET,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
        ) {
            LyricsBottomSheet(
                m, lyrics,
                onClose = { view = MusicView.NOW_PLAYING },
                onExpand = { view = MusicView.LYRICS },
            )
        }
    }
}

@Composable
private fun NowPlaying(
    m: MediaState,
    st: MusicStatus,
    actions: MusicActions,
    canLyrics: Boolean,
    dimmed: Boolean,
    onLyrics: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val enabled = st == MusicStatus.PLAYING || st == MusicStatus.IDLE
    val swipe = rememberSwipe()
    val up by rememberUpdatedState(onSwipeUp)
    val right by rememberUpdatedState(onSwipeRight)
    val dim by animateFloatAsState(if (dimmed) 0.35f else 1f, tween(250), label = "dim")
    val volume = rememberVolumeHud()
    val x = 456
    val w = 620
    DashCard(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = dim }
            // Deslizar a la derecha abre la letra en grande (si hay letra); a la izquierda, siguiente canción.
            .trackSwipe(enabled, swipe, actions, onRight = if (canLyrics) ({ right() }) else null)
            .swipeUpToOpen(canLyrics) { up() }
            .holdForVolume(volume, actions)
    ) {
        TextAt("Música Bluetooth", 44, 38, DashType.Label.copy(fontSize = 18.sp))
        if (canLyrics) LyricsButton(Modifier.offset((1125 - 44 - 124).dp, 24.dp), onLyrics)

        Box(Modifier.fillMaxSize().swipeFollow(swipe)) {
            Artwork(m.art, Modifier.at(44, 96, 360, 360), radius = 22)

            if (st == MusicStatus.PLAYING) {
                TextAt(m.title.orEmpty(), x, 150, DashType.LargeTitle.copy(fontWeight = FontWeight.SemiBold), maxWidth = w.dp)
                m.artist?.let { TextAt(it, x, 198, DashType.Body.copy(fontSize = 23.sp, color = Dash.TextReadable), maxWidth = w.dp) }
                m.album?.takeIf { it != m.title }?.let { TextAt(it, x, 234, DashType.Label.copy(fontSize = 18.sp), maxWidth = w.dp) }
                if (m.durationMs > 0) Progress(m, x = x, y = 286, w = w, seekable = m.canSeek, onSeek = actions.seekTo)
            } else {
                EmptyText(st, x = x, y = 160, w = w, actions = actions, large = true)
            }
        }

        val cx = x + w / 2
        Controls(m, actions, centerX = cx, centerY = 410, playSize = 116, enabled = enabled)
        if (enabled && m.shuffleSupported) {
            ModeButton(
                DashIcons.Shuffle, "Aleatorio", on = m.shuffleOn,
                Modifier.offset((cx - 290 - 32).dp, (410 - 32).dp), onClick = actions.toggleShuffle,
            )
        }
        if (enabled && m.repeatSupported) {
            ModeButton(
                if (m.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) DashIcons.RepeatOne else DashIcons.Repeat,
                "Repetir", on = m.repeatMode != PlaybackStateCompat.REPEAT_MODE_NONE,
                Modifier.offset((cx + 290 - 32).dp, (410 - 32).dp), onClick = actions.cycleRepeat,
            )
        }
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}

/** Aleatorio / repetir: blanco con punto debajo si está activo, gris si no. */
@Composable
private fun ModeButton(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(64.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DashIcon(icon, 30, if (on) Dash.Text else Dash.Text3)
        if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(5.dp).clip(CircleShape).background(Dash.Text))
    }
}

/** Botón "Letra": monocromático, arriba a la derecha del reproductor. */
@Composable
private fun LyricsButton(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .size(124.dp, 44.dp)
            .clip(shape)
            .background(Dash.Well)
            .border(1.dp, Dash.Border, shape)
            .clickable(onClickLabel = "Letra", onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashIcon(DashIcons.Lyrics, 22, Dash.Icon)
        Spacer(Modifier.width(8.dp))
        Text("Letra", style = DashType.Label.copy(color = Dash.Text))
    }
}

/** Deslizar hacia arriba desde la mitad inferior abre la hoja de letra. */
private fun Modifier.swipeUpToOpen(enabled: Boolean, onUp: () -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        val threshold = 70.dp.toPx()
        var startY = 0f
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { o -> startY = o.y; total = 0f },
            onDragEnd = { if (startY > size.height * 0.45f && total <= -threshold) onUp() },
        ) { change, amount ->
            change.consume()
            total += amount
        }
    }

// ---------------------------------------------------------------- Volumen manteniendo presionado

/** Nivel mostrado mientras se ajusta el volumen (null = oculto). */
private class VolumeHudState(val scope: CoroutineScope) {
    var level by mutableStateOf<Float?>(null)
    var raising by mutableStateOf(true)
    var hideJob: Job? = null
}

@Composable
private fun rememberVolumeHud(): VolumeHudState {
    val scope = rememberCoroutineScope()
    return remember { VolumeHudState(scope) }
}

/**
 * Mantener presionada la mitad derecha de la tarjeta sube el volumen; la izquierda lo baja.
 * Tras 0,45 s sin mover el dedo empieza a repetir un paso cada 0,22 s mientras se mantenga.
 * Un toque normal, deslizar o los botones siguen funcionando igual (si el dedo se mueve, se cancela).
 */
private fun Modifier.holdForVolume(hud: VolumeHudState, actions: MusicActions): Modifier = pointerInput(actions) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val raise = down.position.x > size.width / 2f
        val slop = viewConfiguration.touchSlop

        // Fase 1: esperar la pulsación larga sin consumir nada (taps y deslizamientos siguen normales).
        var cancelled = false
        val timedOut = withTimeoutOrNull(HOLD_MS) {
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id }
                if (ch == null || !ch.pressed || ch.isConsumed || (ch.position - down.position).getDistance() > slop) {
                    cancelled = true
                    break
                }
            }
        } == null
        if (!timedOut || cancelled) return@awaitEachGesture

        // Fase 2: ajustar volumen mientras se mantenga; consumir todo para que no haya clic ni deslizamiento.
        fun step() {
            hud.hideJob?.cancel()
            hud.raising = raise
            hud.level = actions.volumeStep(raise)
        }
        step()
        while (true) {
            val released = withTimeoutOrNull(REPEAT_MS) {
                var up = false
                while (!up) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.forEach { it.consume() }
                    up = ev.changes.none { it.pressed }
                }
                true
            } ?: false
            if (released) break
            step()
        }
        hud.hideJob = hud.scope.launch {
            delay(1200)
            hud.level = null
        }
    }
}

private const val HOLD_MS = 450L
private const val REPEAT_MS = 220L

/** Indicador de volumen: icono, barra blanca y porcentaje, centrado sobre la tarjeta. */
@Composable
private fun VolumeHud(hud: VolumeHudState, modifier: Modifier) {
    val last = remember { floatArrayOf(0f) }
    hud.level?.let { last[0] = it }
    AnimatedVisibility(hud.level != null, modifier = modifier, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .size(260.dp, 56.dp)
                .clip(shape)
                .background(Color(0xF21C1D20))
                .border(1.dp, Dash.BorderStrong, shape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashIcon(DashIcons.Vol, 24, Dash.Icon)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Dash.Track)) {
                Box(Modifier.fillMaxWidth(last[0].coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Dash.Active))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${(last[0] * 10).roundToInt() * 10}%",
                style = DashType.Label.copy(fontSize = 16.sp, color = Dash.Text),
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

// ---------------------------------------------------------------- Deslizar para cambiar de canción

/** Desplazamiento horizontal actual del dedo (px), animado de vuelta a 0 al soltar. */
private class SwipeState(val offset: Animatable<Float, AnimationVector1D>, val scope: CoroutineScope)

@Composable
private fun rememberSwipe(): SwipeState {
    val scope = rememberCoroutineScope()
    return remember { SwipeState(Animatable(0f), scope) }
}

/** Izquierda = siguiente, derecha = anterior. Umbral 90 px; los botones siguen funcionando con toque. */
private fun Modifier.trackSwipe(enabled: Boolean, s: SwipeState, actions: MusicActions, onRight: (() -> Unit)? = null): Modifier =
    if (!enabled) this else this.pointerInput(actions, onRight != null) {
        val threshold = 90.dp.toPx()
        var total = 0f
        fun settle() { s.scope.launch { s.offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f)) } }
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total <= -threshold -> actions.next()
                    total >= threshold -> (onRight ?: actions.previous)()
                }
                settle()
            },
            onDragCancel = { settle() },
        ) { change, amount ->
            change.consume()
            total += amount
            val t = total
            s.scope.launch { s.offset.snapTo(t) }
        }
    }

/** La carátula y el texto siguen al dedo con resistencia y se atenúan un poco. */
private fun Modifier.swipeFollow(s: SwipeState): Modifier = graphicsLayer {
    val v = s.offset.value
    translationX = v * 0.45f
    alpha = 1f - (abs(v) / 600f).coerceAtMost(0.45f)
}

// ---------------------------------------------------------------- Piezas

/** Carátula real o placeholder gris carbón con nota musical. */
@Composable
internal fun Artwork(art: ImageBitmap?, modifier: Modifier, radius: Int) {
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.clip(shape).border(1.dp, Dash.Border, shape)) {
        Crossfade(art, animationSpec = tween(220), label = "art") { a ->
            if (a != null) {
                Image(a, contentDescription = "Carátula", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Dash.Placeholder), contentAlignment = Alignment.Center) {
                    DashIcon(DashIcons.Music, 40, Color(0xFF4A4A50))
                }
            }
        }
    }
}

@Composable
private fun EmptyText(st: MusicStatus, x: Int, y: Int, w: Int, actions: MusicActions, large: Boolean = false) {
    val c = emptyCopy(st)
    TextAt(c.title, x, y, DashType.Headline.copy(fontSize = if (large) 30.sp else 23.sp), maxWidth = w.dp)
    TextAt(c.subtitle, x, y + (if (large) 46 else 36), DashType.Label.copy(fontSize = if (large) 20.sp else 17.sp), maxWidth = w.dp)
    c.action?.let { label ->
        val onClick = if (st == MusicStatus.NO_ACCESS) actions.grantAccess else actions.openBluetooth
        PillButton(label, onClick, Modifier.offset(x.dp, (y + if (large) 96 else 74).dp))
    }
}

/** Estados sin canción en la tarjeta angosta del Inicio: hasta 2 líneas por texto. */
@Composable
private fun CompactEmptyText(st: MusicStatus, actions: MusicActions, modifier: Modifier) {
    val c = emptyCopy(st)
    Column(modifier) {
        Text(c.title, style = DashType.Headline.copy(fontSize = 20.sp, lineHeight = 25.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(c.subtitle, style = DashType.Label.copy(fontSize = 15.sp, lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        c.action?.let { label ->
            Spacer(Modifier.height(12.dp))
            PillButton(label, if (st == MusicStatus.NO_ACCESS) actions.grantAccess else actions.openBluetooth)
        }
    }
}

@Composable
private fun Controls(
    m: MediaState,
    actions: MusicActions,
    centerX: Int,
    centerY: Int,
    playSize: Int,
    enabled: Boolean,
    side: Int = 72,
    gap: Int = if (playSize > 110) 160 else 150,
) {
    val sideIcon = when { side < 60 -> 26; side < 70 -> 36; else -> 40 }
    CircleButton(DashIcons.Prev, "Anterior", side, sideIcon, Modifier.offset((centerX - gap - side / 2).dp, (centerY - side / 2).dp), enabled = enabled, onClick = actions.previous)
    CircleButton(
        if (m.isPlaying) DashIcons.Pause else DashIcons.Play,
        "Reproducir o pausar", playSize, when { playSize > 110 -> 50; playSize < 80 -> 30; else -> 40 },
        Modifier.offset((centerX - playSize / 2).dp, (centerY - playSize / 2).dp),
        filled = true, enabled = enabled, onClick = actions.playPause,
    )
    CircleButton(DashIcons.Next, "Siguiente", side, sideIcon, Modifier.offset((centerX + gap - side / 2).dp, (centerY - side / 2).dp), enabled = enabled, onClick = actions.next)
}

/** Barra de progreso gris carbón + blanco. Solo avanza mientras se reproduce. */
@Composable
internal fun Progress(m: MediaState, x: Int, y: Int, w: Int, seekable: Boolean, onSeek: (Long) -> Unit) {
    val now by produceState(SystemClock.elapsedRealtime(), m.isPlaying, m.positionUpdatedAt) {
        value = SystemClock.elapsedRealtime()
        while (m.isPlaying) { delay(1000); value = SystemClock.elapsedRealtime() }
    }
    val pos = m.currentPosition(now)
    val f = (pos.toFloat() / m.durationMs).coerceIn(0f, 1f)

    // Zona táctil de 30 px alrededor de la barra de 5 px (solo si la app permite saltar).
    Box(
        Modifier
            .at(x, y - 13, w, 30)
            .then(
                if (seekable) Modifier.pointerInput(m.durationMs) {
                    detectTapGestures { o -> onSeek((o.x / size.width * m.durationMs).toLong()) }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.size(w.dp, 4.dp).clip(CircleShape).background(Dash.Track)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(Dash.Active))
        }
    }
    Box(Modifier.offset((x + w * f - 5.5f).dp, (y - 3.5f).dp).size(11.dp).clip(CircleShape).background(Dash.Text))

    val t = DashType.Footnote.copy(fontSize = 16.sp, color = Dash.Text2)
    TextAt(fmt(pos), x, y + 20, t)
    Text(
        fmt(m.durationMs), style = t.tight(), maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.End,
        modifier = Modifier.offset((x + w - 80).dp, (y + 20).dp).width(80.dp),
    )
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- Volumen táctil (+ / −)

/** Botones de volumen siempre visibles: + arriba, nivel en el centro (pasos de 10 %), − abajo. */
@Composable
internal fun VolumeControl(actions: MusicActions, modifier: Modifier) {
    var level by remember { mutableStateOf(actions.volumeLevel()) }
    LaunchedEffect(Unit) {
        // Si se cambia con las teclas físicas o el volante, el número también se actualiza.
        while (true) { delay(1500); level = actions.volumeLevel() }
    }
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .width(64.dp)
            .clip(shape)
            .background(Color(0xCC1A1B1E))
            .border(1.dp, Dash.Border, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).clickable(onClickLabel = "Subir volumen") { level = actions.volumeStep(true) },
            contentAlignment = Alignment.Center,
        ) { Text("+", style = DashType.Title.copy(fontSize = 32.sp, color = Dash.Text)) }
        DashIcon(DashIcons.Vol, 20, Dash.Icon2)
        Text(
            "${(level * 10).roundToInt() * 10}",
            style = DashType.Label.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Dash.Text),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Box(
            Modifier.size(64.dp).clip(CircleShape).clickable(onClickLabel = "Bajar volumen") { level = actions.volumeStep(false) },
            contentAlignment = Alignment.Center,
        ) { Text("−", style = DashType.Title.copy(fontSize = 32.sp, color = Dash.Text)) }
    }
}

// ---------------------------------------------------------------- Letra en grande (Música)

/**
 * Letra en grande con carátula pequeña y volumen táctil (1125×571).
 * Se abre deslizando a la derecha en el reproductor o con el botón «Letra»; deslizar a la izquierda o ✕ vuelve.
 */
@Composable
private fun BigLyricsScreen(m: MediaState, lyrics: LyricsState, driving: Boolean, actions: MusicActions, onClose: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    val volume = rememberVolumeHud()
    DashCard(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 90.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total <= -threshold) close() },
                ) { change, amount -> change.consume(); total += amount }
            }
            .holdForVolume(volume, actions)
    ) {
        // Carátula en cuadrito + título y artista.
        Artwork(m.art, Modifier.at(32, 28, 72, 72), radius = 12)
        TextAt(m.title.orEmpty(), 122, 36, DashType.Title.copy(fontSize = 22.sp), maxWidth = 780.dp)
        m.artist?.let { TextAt(it, 122, 66, DashType.Label.copy(fontSize = 17.sp, color = Dash.TextReadable), maxWidth = 780.dp) }
        Box(
            Modifier.at(1125 - 32 - 52, 32, 52, 52).clip(CircleShape).background(Dash.Well)
                .clickable(onClickLabel = "Cerrar letra", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { DashIcon(DashIcons.X, 22, Dash.Icon) }

        LyricsPage(m, lyrics, driving, Modifier.at(20, 112, 1125 - 140, 380))
        VolumeControl(actions, Modifier.offset((1125 - 32 - 64).dp, 170.dp))

        if (m.durationMs > 0) Progress(m, x = 60, y = 520, w = 1005, seekable = m.canSeek && !driving, onSeek = actions.seekTo)
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}
