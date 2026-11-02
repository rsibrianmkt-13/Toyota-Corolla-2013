package com.bryan.corolladash.ui.screens

import android.os.SystemClock
import android.os.Build
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
    /** Fijar el volumen 0–1 (barra deslizable). */
    val setVolume: (Float) -> Unit = {},
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
    val w = HOME_MUSIC_W
    DashCard(modifier.trackSwipe(enabled, swipe, actions).holdForVolume(volume, actions), onClick = onOpen) {
        Box(Modifier.fillMaxSize().background(MusicCardBg))
        // Encabezado: reproductor de origen · teléfono (tarjeta 526×490 del Inicio)
        Box(Modifier.at(22, 20, 36, 36).clip(CircleShape).background(Dash.Well), contentAlignment = Alignment.Center) {
            DashIcon(DashIcons.Bt, 18, Dash.Icon)
        }
        Row(Modifier.offset(70.dp, 28.dp).width((w - 92).dp), verticalAlignment = Alignment.CenterVertically) {
            Text(source, style = DashType.Label.copy(fontSize = 17.sp, color = Dash.TextReadable), maxLines = 1)
            sub?.let { Text(" · $it", style = DashType.Label.copy(fontSize = 17.sp, color = Color(0xFF8E8E93)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }

        // Carátula y textos acompañan el dedo al deslizar.
        Box(Modifier.fillMaxSize().swipeFollow(swipe)) {
            Artwork(m.art, Modifier.at(22, 74, 140, 140), radius = 18)
            if (st == MusicStatus.PLAYING) {
                TextAt(m.title.orEmpty(), 182, 92, DashType.Headline.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp), maxWidth = 322.dp)
                m.artist?.let { TextAt(it, 182, 126, DashType.Label.copy(fontSize = 18.sp, color = Dash.TextReadable), maxWidth = 322.dp) }
                if (m.durationMs > 0) Progress(m, x = 182, y = 174, w = 322, seekable = false, onSeek = {}, small = true)
            } else {
                CompactEmptyText(st, actions, Modifier.offset(182.dp, 84.dp).width(322.dp))
            }
        }

        Controls(m, actions, centerX = w / 2, centerY = 268, playSize = 80, enabled = enabled, side = 64, gap = 120)

        // Abajo: próximas canciones (si el reproductor comparte su cola) o las escuchadas antes.
        val list = m.upNext.ifEmpty { m.recent }
        if (st == MusicStatus.PLAYING && list.isNotEmpty()) {
            Box(Modifier.at(22, 326, w - 44, 1).background(Dash.Border))
            TextAt(if (m.upNext.isNotEmpty()) "A continuación" else "Escuchadas antes", 22, 344, QueueLabel)
            list.forEachIndexed { i, song ->
                Row(Modifier.offset(22.dp, (378 + i * 34).dp).width((w - 44).dp)) {
                    Text(song.title, style = QueueTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    song.artist?.let { Text(" · $it", style = QueueArtist, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}

/** Ancho de la tarjeta de música del Inicio. */
const val HOME_MUSIC_W = 526
private val QueueLabel = DashType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xB38E8E93))
private val QueueTitle = DashType.Label.copy(fontSize = 17.sp, fontWeight = FontWeight.Light, color = Color(0x8CC7C7CC))
private val QueueArtist = DashType.Label.copy(fontSize = 17.sp, fontWeight = FontWeight.Light, color = Color(0x738E8E93))

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
 * Música compacta de la vista limpia (320×300): el carro domina la escena y la música queda a un lado,
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
    val w = 320
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
        Controls(m, actions, centerX = 120, centerY = 222, playSize = 68, enabled = enabled, side = 56, gap = 92)
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}

// ---------------------------------------------------------------- Pantalla completa (1125×571)

/**
 * Pantalla Música (1125×571, sin tarjeta): con carátula → carátula grande a la izquierda;
 * sin carátula → solo texto, más grande. Deslizar hacia arriba (o tocar el aviso) abre la letra en grande.
 */
@Composable
fun MusicScreen(
    m: MediaState,
    phoneConnected: Boolean,
    actions: MusicActions,
    lyrics: LyricsState,
    lyricsEnabled: Boolean,
    driving: Boolean,
    onLyricsOpened: () -> Unit,
    phoneName: String? = null,
    onRetryLyrics: () -> Unit = {},
    onSearch: () -> Unit = {},
    onVoice: () -> Unit = {},
) {
    val st = status(m, phoneConnected)
    var showLyrics by remember { mutableStateOf(false) }
    val canLyrics = lyricsEnabled && st == MusicStatus.PLAYING
    LaunchedEffect(canLyrics) { if (!canLyrics) showLyrics = false }
    BackHandler(enabled = showLyrics) { showLyrics = false }

    AnimatedContent(
        showLyrics,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(300)) { if (targetState) it / 6 else -it / 6 }) togetherWith fadeOut(tween(180))
        },
        label = "lyrics",
        modifier = Modifier.fillMaxSize(),
    ) { full ->
        if (full) {
            BigLyricsScreen(m, lyrics, driving, actions, onClose = { showLyrics = false }, onRetry = onRetryLyrics)
        } else {
            NowPlaying(m, phoneName, st, actions, canLyrics, onSearch, onVoice) {
                showLyrics = true
                onLyricsOpened()
            }
        }
    }
}

@Composable
private fun NowPlaying(
    m: MediaState,
    phoneName: String?,
    st: MusicStatus,
    actions: MusicActions,
    canLyrics: Boolean,
    onSearch: () -> Unit,
    onVoice: () -> Unit,
    onLyrics: () -> Unit,
) {
    val enabled = st == MusicStatus.PLAYING || st == MusicStatus.IDLE
    val swipe = rememberSwipe()
    val volume = rememberVolumeHud()
    val withArt = m.art != null && st == MusicStatus.PLAYING
    Box(
        Modifier
            .fillMaxSize()
            .trackSwipe(enabled, swipe, actions)
            .swipeUpToOpen(canLyrics, onLyrics)
            .holdForVolume(volume, actions)
    ) {
        val cx = if (withArt) 507 + 280 else 67 + 490
        val cy = if (withArt) 420 else 445
        val (source, sub) = sourceLines(m, phoneName)
        Box(Modifier.fillMaxSize().swipeFollow(swipe)) {
            if (withArt) {
                Artwork(m.art, Modifier.at(57, 50, 380, 380), radius = 24)
                val x = 507
                val w = 560
                SourceRow(source, sub, Modifier.offset(x.dp, 96.dp))
                TextAt(m.title.orEmpty(), x, 136, DashType.LargeTitle.copy(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp), maxWidth = w.dp)
                m.artist?.let { TextAt(it, x, 194, DashType.Body.copy(fontSize = 24.sp, color = Dash.TextReadable), maxWidth = w.dp) }
                m.album?.takeIf { it != m.title }?.let { TextAt(it, x, 230, DashType.Label.copy(fontSize = 18.sp, color = Dash.Text3), maxWidth = w.dp) }
                if (m.durationMs > 0) Progress(m, x = x, y = 312, w = w, seekable = m.canSeek, onSeek = actions.seekTo)
            } else {
                val x = 67
                val w = 980
                // Sin carátula: solo un icono pequeño y tenue, el texto manda.
                Box(
                    Modifier.at(x, 50, 64, 64).clip(RoundedCornerShape(18.dp)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) { DashIcon(DashIcons.Music, 28, Color(0x59EBEBF0)) }
                SourceRow(source, sub, Modifier.offset((x + 84).dp, 72.dp))
                if (st == MusicStatus.PLAYING) {
                    TextAt(m.title.orEmpty(), x, 150, DashType.LargeTitle.copy(fontSize = 54.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.4).sp), maxWidth = w.dp)
                    m.artist?.let { TextAt(it, x, 220, DashType.Body.copy(fontSize = 26.sp, color = Dash.TextReadable), maxWidth = w.dp) }
                    m.album?.takeIf { it != m.title }?.let { TextAt(it, x, 258, DashType.Label.copy(fontSize = 18.sp, color = Dash.Text3), maxWidth = w.dp) }
                    if (m.durationMs > 0) Progress(m, x = x, y = 332, w = w, seekable = m.canSeek, onSeek = actions.seekTo)
                } else {
                    EmptyText(st, x = x, y = 160, w = w, actions = actions, large = true)
                }
            }
        }
        Controls(m, actions, centerX = cx, centerY = cy, playSize = 96, enabled = enabled, side = 72, gap = 130)
        if (enabled && m.shuffleSupported) {
            ModeButton(
                DashIcons.Shuffle, "Aleatorio", on = m.shuffleOn,
                Modifier.offset((cx - 250 - 32).dp, (cy - 32).dp), onClick = actions.toggleShuffle,
            )
        }
        if (enabled && m.repeatSupported) {
            ModeButton(
                if (m.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) DashIcons.RepeatOne else DashIcons.Repeat,
                "Repetir", on = m.repeatMode != PlaybackStateCompat.REPEAT_MODE_NONE,
                Modifier.offset((cx + 250 - 32).dp, (cy - 32).dp), onClick = actions.cycleRepeat,
            )
        }
        // Buscar canción (teclado) y micrófono, arriba a la derecha.
        RoundLineButton(DashIcons.Search, "Buscar canción", Modifier.offset((1125 - 24 - 52 - 12 - 52).dp, 0.dp), onSearch)
        RoundLineButton(DashIcons.Mic, "Buscar por voz", Modifier.offset((1125 - 24 - 52).dp, 0.dp), onVoice)
        if (canLyrics) {
            Row(
                Modifier.offset((cx - 170).dp, 536.dp).size(340.dp, 32.dp).clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Ver letra", onClick = onLyrics),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DashIcon(DashIcons.ChevU, 16, HintColor)
                Spacer(Modifier.width(8.dp))
                Text("Desliza hacia arriba para ver la letra", style = DashType.Footnote.copy(fontSize = 14.sp, color = HintColor), maxLines = 1)
            }
        }
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}

private val HintColor = Color(0x998E8E93)

/** Botón redondo de solo línea (mismo estilo que los accesos del Inicio). */
@Composable
private fun RoundLineButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(52.dp).clip(CircleShape).border(1.dp, Color(0x21FFFFFF), CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DashIcon(icon, 24, Color(0xB3EBEBF0)) }
}

/**
 * Buscar una canción con el teclado (solo detenido). El texto se envía al reproductor por Bluetooth;
 * si la radio no lo permite, se avisa y se ofrece el micrófono ("Ok Google").
 */
@Composable
fun SongSearchOverlay(visible: Boolean, onSearch: (String) -> Unit, onVoice: () -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
        var text by remember { mutableStateOf("") }
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(Color(0xB3000000))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
        ) {
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier.offset(240.dp, 140.dp).size(800.dp, 76.dp).clip(shape).background(Color(0xFF16171A))
                    .border(1.dp, Dash.BorderStrong, shape)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DashIcon(DashIcons.Search, 26, Dash.Text2)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Canción o artista", style = DashType.Body.copy(fontSize = 22.sp, color = Dash.Text3), maxLines = 1)
                    androidx.compose.foundation.text.BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = DashType.Body.copy(fontSize = 22.sp, color = Dash.Text),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Dash.Text),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (text.isNotBlank()) onSearch(text.trim()) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Spacer(Modifier.width(12.dp))
                RoundLineButton(DashIcons.Mic, "Buscar por voz", Modifier, onVoice)
                Spacer(Modifier.width(10.dp))
                PillButton("Buscar", { if (text.isNotBlank()) onSearch(text.trim()) }, strong = true, enabled = text.isNotBlank())
            }
        }
    }
}

/** "Spotify · iPhone de Bryan" con el icono de Bluetooth. */
@Composable
private fun SourceRow(source: String, sub: String?, modifier: Modifier) {
    Row(modifier.width(560.dp), verticalAlignment = Alignment.CenterVertically) {
        DashIcon(DashIcons.Bt, 17, Color(0xFF8E8E93))
        Spacer(Modifier.width(10.dp))
        Text(source, style = DashType.Label.copy(fontSize = 16.sp, color = Dash.TextReadable), maxLines = 1)
        sub?.let { Text(" · $it", style = DashType.Label.copy(fontSize = 16.sp, color = Color(0xFF8E8E93)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * Fondo de la pantalla Música: los colores de la carátula, muy difuminados y tenues.
 * Se reduce la carátula a 12×12 px y se estira con filtrado suave (casi sin costo); en Android 12+ además se difumina.
 */
@Composable
internal fun MusicBackdrop(art: ImageBitmap?, modifier: Modifier = Modifier) {
    Crossfade(art, animationSpec = tween(700), label = "backdrop", modifier = modifier) { a ->
        if (a != null) {
            val tiny = remember(a) { tinyOf(a) }
            Box(Modifier.fillMaxSize()) {
                Image(
                    tiny, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp, BlurredEdgeTreatment.Rectangle) else Modifier)
                        .graphicsLayer { alpha = 0.34f },
                )
                Box(Modifier.fillMaxSize().background(Color(0x47030304)))
            }
        }
    }
}

private fun tinyOf(a: ImageBitmap): ImageBitmap = runCatching {
    val src = a.asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it }
    android.graphics.Bitmap.createScaledBitmap(src, 12, 12, true).asImageBitmap()
}.getOrDefault(a)

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

/** Deslizar hacia arriba (desde cualquier parte) abre la letra. */
private fun Modifier.swipeUpToOpen(enabled: Boolean, onUp: () -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        val threshold = 70.dp.toPx()
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total <= -threshold) onUp() },
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
        "Reproducir o pausar", playSize, when { playSize > 90 -> 56; playSize < 76 -> 38; else -> 46 },
        Modifier.offset((centerX - playSize / 2).dp, (centerY - playSize / 2).dp),
        enabled = enabled, onClick = actions.playPause,
    )
    CircleButton(DashIcons.Next, "Siguiente", side, sideIcon, Modifier.offset((centerX + gap - side / 2).dp, (centerY - side / 2).dp), enabled = enabled, onClick = actions.next)
}

/** Barra de progreso gris carbón + blanco. Solo avanza mientras se reproduce. */
@Composable
internal fun Progress(m: MediaState, x: Int, y: Int, w: Int, seekable: Boolean, onSeek: (Long) -> Unit, small: Boolean = false) {
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

    val t = DashType.Footnote.copy(fontSize = if (small) 13.sp else 16.sp, color = Dash.Text2)
    TextAt(fmt(pos), x, y + (if (small) 14 else 20), t)
    Text(
        fmt(m.durationMs), style = t.tight(), maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.End,
        modifier = Modifier.offset((x + w - 80).dp, (y + if (small) 14 else 20).dp).width(80.dp),
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

/** Cuenta toques en la pantalla (sin consumirlos) para mostrar controles que se ocultan solos. */
internal class TouchTicker {
    var tick by mutableIntStateOf(0)
}

internal fun Modifier.onAnyTouch(t: TouchTicker): Modifier = pointerInput(t) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.changes.any { it.pressed }) t.tick++
        }
    }
}

/** Barra de volumen discreta que aparece al tocar la pantalla y se oculta a los 2 s sin uso. */
@Composable
internal fun AutoHideVolume(actions: MusicActions, tick: Int, modifier: Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(tick) {
        visible = true
        delay(2_000)
        visible = false
    }
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
        VolumeSlider(actions)
    }
}

/**
 * Volumen como barra vertical delgada (sin caja): porcentaje arriba, icono abajo.
 * Tocar o arrastrar sobre la barra fija el nivel. Se relee cada 1,5 s por si cambia con el volante.
 */
@Composable
internal fun VolumeSlider(actions: MusicActions, modifier: Modifier = Modifier) {
    var level by remember { mutableFloatStateOf(actions.volumeLevel()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (!dragging) level = actions.volumeLevel()
        }
    }
    val set by rememberUpdatedState<(Float) -> Unit> { v ->
        val f = v.coerceIn(0f, 1f)
        level = f
        actions.setVolume(f)
    }
    val dim = Color(0x99EBEBF0)
    Column(modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${(level * 100).roundToInt()}", style = DashType.Footnote.copy(fontSize = 13.sp, color = dim))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .size(44.dp, 230.dp)
                .pointerInput(Unit) { detectTapGestures { o -> set(1f - o.y / size.height) } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { ch, _ ->
                        ch.consume()
                        set(1f - ch.position.y / size.height)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(6.dp).fillMaxHeight().clip(CircleShape).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(level.coerceIn(0f, 1f)).clip(CircleShape).background(Color(0xD9EBEBF0)))
            }
        }
        Spacer(Modifier.height(10.dp))
        DashIcon(DashIcons.Vol, 18, dim)
    }
}

// ---------------------------------------------------------------- Letra en grande (Música)

/**
 * Letra en grande con carátula pequeña y volumen táctil (1125×571, sin tarjeta).
 * Se abre deslizando hacia arriba en el reproductor; deslizar hacia abajo, a la izquierda o ✕ vuelve.
 */
@Composable
private fun BigLyricsScreen(m: MediaState, lyrics: LyricsState, driving: Boolean, actions: MusicActions, onClose: () -> Unit, onRetry: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    val volume = rememberVolumeHud()
    val touches = remember { TouchTicker() }
    Box(
        Modifier
            .fillMaxSize()
            .onAnyTouch(touches)
            .pointerInput(Unit) {
                val threshold = 90.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total <= -threshold) close() },
                ) { change, amount -> change.consume(); total += amount }
            }
            .pointerInput(Unit) {
                val threshold = 80.dp.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total >= threshold) close() },
                ) { change, amount -> change.consume(); total += amount }
            }
            .holdForVolume(volume, actions)
    ) {
        // Carátula en cuadrito + título y artista.
        Artwork(m.art, Modifier.at(32, 12, 72, 72), radius = 14)
        TextAt(m.title.orEmpty(), 122, 22, DashType.Title.copy(fontSize = 22.sp), maxWidth = 780.dp)
        m.artist?.let { TextAt(it, 122, 52, DashType.Label.copy(fontSize = 17.sp, color = Dash.TextReadable), maxWidth = 780.dp) }
        Box(
            Modifier.at(1125 - 32 - 52, 16, 52, 52).clip(CircleShape).background(Dash.Well)
                .clickable(onClickLabel = "Cerrar letra", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { DashIcon(DashIcons.X, 22, Dash.Icon) }

        LyricsPage(m, lyrics, driving, Modifier.at(20, 100, 1125 - 140, 400), onRetry = onRetry)
        AutoHideVolume(actions, touches.tick, Modifier.offset((1125 - 24 - 44).dp, 120.dp))

        if (m.durationMs > 0) Progress(m, x = 60, y = 528, w = 1005, seekable = m.canSeek && !driving, onSeek = actions.seekTo)
        VolumeHud(volume, Modifier.align(Alignment.Center))
    }
}
