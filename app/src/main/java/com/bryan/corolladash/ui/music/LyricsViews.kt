package com.bryan.corolladash.ui.music

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.lyrics.LrcParser
import com.bryan.corolladash.lyrics.LyricLine
import com.bryan.corolladash.lyrics.LyricsResult
import com.bryan.corolladash.lyrics.LyricsState
import com.bryan.corolladash.media.MediaState
import com.bryan.corolladash.ui.components.DashCard
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.TextAt
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.screens.Artwork
import com.bryan.corolladash.ui.screens.Progress
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import kotlinx.coroutines.delay

/*
 * Letras. Colores: línea actual blanca semibold, siguiente gris medio, resto gris oscuro.
 * Nada de azul ni brillos. En conducción (≥ 8 km/h) solo 3 líneas y sin desplazamiento manual.
 */

// ---------------------------------------------------------------- Posición → línea activa

/** Índice de la línea activa; avanza solo mientras se reproduce y se recoloca tras un seek. */
@Composable
internal fun rememberActiveLine(lines: List<LyricLine>, m: MediaState): Int {
    val pos = produceState(m.currentPosition(), m.isPlaying, m.positionMs, m.positionUpdatedAt) {
        value = m.currentPosition()
        while (m.isPlaying) {
            delay(200)
            value = m.currentPosition()
        }
    }
    // derivedStateOf: la posición cambia 5 veces por segundo, pero quien llama solo se
    // recompone cuando cambia la línea. Ligero adelanto para que cambie justo al cantarse.
    val index by remember(lines, pos) { derivedStateOf { LrcParser.activeIndex(lines, pos.value + 150) } }
    return index
}

private fun message(r: LyricsResult): String? = when (r) {
    LyricsResult.Loading -> "Buscando letra…"
    LyricsResult.Offline -> "Letra no disponible sin conexión"
    LyricsResult.NotFound -> "No encontramos la letra de esta canción"
    LyricsResult.Instrumental -> "Pista instrumental"
    is LyricsResult.Error -> "No se pudo cargar la letra"
    LyricsResult.Idle -> "Sin reproducción"
    else -> null
}

// ---------------------------------------------------------------- Pantalla completa (1125×571)

@Composable
fun LyricsScreen(m: MediaState, lyrics: LyricsState, driving: Boolean, onSeek: (Long) -> Unit, onClose: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    DashCard(Modifier.fillMaxSize().pointerInput(Unit) { swipeDown { close() } }) {
        // Encabezado: atrás, carátula pequeña, título y artista.
        Box(
            Modifier.at(24, 22, 52, 52).clip(CircleShape).background(Dash.Well).clickable(onClickLabel = "Cerrar letra", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { DashIcon(DashIcons.ChevD, 26, Dash.Icon) }
        Artwork(m.art, Modifier.at(92, 16, 64, 64), radius = 12)
        TextAt(m.title.orEmpty(), 174, 24, DashType.Title.copy(fontSize = 22.sp), maxWidth = 720.dp)
        m.artist?.let { TextAt(it, 174, 54, DashType.Label.copy(fontSize = 17.sp, color = Dash.TextReadable), maxWidth = 720.dp) }
        TextAt("Letras: ${lyrics.provider ?: "—"}", 1125 - 44 - 160, 38, DashType.Footnote.copy(fontSize = 13.sp, textAlign = TextAlign.End), maxWidth = 160.dp)

        Box(Modifier.at(0, 96, 1125, 404)) {
            val r = lyrics.result
            when {
                r is LyricsResult.Synced && driving -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ThreeLines(r.lines, rememberActiveLine(r.lines, m), big = true)
                }
                r is LyricsResult.Synced -> SyncedList(r.lines, m, onSeek = if (m.canSeek) onSeek else null)
                r is LyricsResult.Plain && driving -> Centered("Letra sin sincronizar", "Disponible con el vehículo detenido")
                r is LyricsResult.Plain -> PlainList(r.lines)
                else -> Centered(message(r).orEmpty(), (r as? LyricsResult.Error)?.technical)
            }
            // Bordes que se desvanecen arriba y abajo.
            Box(Modifier.fillMaxWidth().height(40.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Dash.Card, Color.Transparent))))
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Dash.Card)))
            )
        }

        if (m.durationMs > 0) Progress(m, x = 60, y = 520, w = 1005, seekable = m.canSeek && !driving, onSeek = onSeek)
    }
}

/** Letra sincronizada completa: sigue la reproducción; con el vehículo detenido se puede desplazar y tocar. */
@Composable
private fun SyncedList(lines: List<LyricLine>, m: MediaState, onSeek: ((Long) -> Unit)?) {
    val active = rememberActiveLine(lines, m)
    val listState = rememberLazyListState()
    var userScrollAt by remember { mutableLongStateOf(0L) }
    var auto by remember { mutableStateOf(false) }

    // Desplazamiento manual del usuario: pausa el seguimiento automático 4 s.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !auto) userScrollAt = SystemClock.uptimeMillis()
        }
    }
    // Seguir la línea activa con un desplazamiento suave (~320 ms), centrada.
    LaunchedEffect(active, lines) {
        if (active < 0) return@LaunchedEffect
        if (SystemClock.uptimeMillis() - userScrollAt < 4000) return@LaunchedEffect
        auto = true
        try {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == active }
            if (item != null) {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                listState.animateScrollBy((item.offset + item.size / 2 - center).toFloat(), tween(320, easing = FastOutSlowInEasing))
            } else {
                listState.scrollToItem(active)   // salto grande (seek o apertura)
            }
        } finally {
            auto = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = maxHeight / 2 - 30.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad, bottom = pad),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(lines) { i, line ->
                LyricLineItem(
                    line.text,
                    role = when {
                        i == active -> LineRole.CURRENT
                        i == active + 1 -> LineRole.NEXT
                        else -> LineRole.OTHER
                    },
                    onClick = onSeek?.let { seek -> { seek(line.timeMs) } },
                )
            }
        }
    }
}

enum class LineRole { CURRENT, NEXT, OTHER }

/** Una línea: mismo tamaño de caja para todas; la actual se escala a 100 % y las demás a 75 %. */
@Composable
fun LyricLineItem(text: String, role: LineRole, onClick: (() -> Unit)?) {
    val color by animateColorAsState(
        when (role) { LineRole.CURRENT -> Dash.Text; LineRole.NEXT -> Dash.Text2; LineRole.OTHER -> Dash.Text3 },
        tween(300), label = "lineColor",
    )
    val scale by animateFloatAsState(if (role == LineRole.CURRENT) 1f else 0.75f, tween(300), label = "lineScale")
    Text(
        text.ifBlank { "♪" },
        style = DashType.Title.copy(
            fontSize = 32.sp, lineHeight = 40.sp, color = color, textAlign = TextAlign.Center,
            fontWeight = if (role == LineRole.CURRENT) FontWeight.SemiBold else FontWeight.Medium,
        ),
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 90.dp, vertical = 6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    )
}

/** Letra sin sincronizar: texto completo, sin resaltar ni inventar tiempos. */
@Composable
private fun PlainList(lines: List<String>) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lines) { _, l ->
            Text(
                l, style = DashType.Body.copy(fontSize = 23.sp, lineHeight = 34.sp, color = Dash.TextReadable, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 90.dp),
            )
        }
    }
}

/** Anterior pequeña · actual grande · siguiente pequeña (conducción y hoja). */
@Composable
private fun ThreeLines(lines: List<LyricLine>, active: Int, big: Boolean) {
    AnimatedContent(
        active,
        transitionSpec = {
            (fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 5 }) togetherWith
                (fadeOut(tween(200)) + slideOutVertically(tween(320)) { -it / 5 })
        },
        label = "threeLines",
    ) { i ->
        val small = if (big) 23.sp else 20.sp
        Column(Modifier.fillMaxWidth().padding(horizontal = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                lines.getOrNull(i - 1)?.text?.ifBlank { "♪" }.orEmpty(),
                style = DashType.Body.copy(fontSize = small, color = Dash.Text3, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (big) 18.dp else 12.dp))
            Text(
                (if (i >= 0) lines[i].text else "").ifBlank { "♪" },
                style = DashType.Title.copy(fontSize = if (big) 36.sp else 30.sp, lineHeight = if (big) 44.sp else 38.sp, textAlign = TextAlign.Center),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (big) 18.dp else 12.dp))
            Text(
                lines.getOrNull(i + 1)?.text?.ifBlank { "♪" }.orEmpty(),
                style = DashType.Body.copy(fontSize = small, color = Dash.Text2, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Centered(title: String, subtitle: String?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(title, style = DashType.Headline.copy(fontSize = 22.sp, color = Dash.Text2))
        subtitle?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = DashType.Label.copy(color = Dash.Text3))
        }
    }
}

// ---------------------------------------------------------------- Página de letra del Inicio (sin barra superior ni accesos)

/**
 * Letra a pantalla completa en el Inicio: solo queda visible el menú lateral.
 * Detenido: 5 líneas (2 anteriores, la actual grande, 2 siguientes). En movimiento: 3 líneas.
 */
@Composable
fun LyricsPage(m: MediaState, lyrics: LyricsState, driving: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val r = lyrics.result) {
            is LyricsResult.Synced -> {
                val active = rememberActiveLine(r.lines, m)
                AnimatedContent(
                    active,
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically(tween(340)) { it / 8 }) togetherWith
                            (fadeOut(tween(220)) + slideOutVertically(tween(340)) { -it / 8 })
                    },
                    label = "lyricsPage",
                ) { i ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val span = if (driving) 1 else 2
                        for (k in -span..span) {
                            val t = lines(r.lines, i + k)
                            val style = when {
                                k == 0 -> DashType.Title.copy(fontSize = 54.sp, lineHeight = 64.sp, fontWeight = FontWeight.SemiBold, color = Dash.Text)
                                k == 1 -> DashType.Body.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, color = Dash.Text2)
                                k == -1 -> DashType.Body.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, color = Dash.Text3)
                                else -> DashType.Body.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = Dash.Text3.copy(alpha = 0.55f))
                            }
                            Text(
                                t, style = style.copy(textAlign = TextAlign.Center, letterSpacing = if (k == 0) (-0.8).sp else (-0.2).sp),
                                maxLines = if (k == 0) 2 else 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = if (k == 0) 14.dp else 8.dp),
                            )
                        }
                    }
                }
            }
            is LyricsResult.Plain -> if (driving) {
                Centered("Letra sin sincronizar", "Disponible con el vehículo detenido")
            } else {
                PlainList(r.lines)
            }
            else -> Centered(
                if (m.title == null) "Sin reproducción" else message(r).orEmpty(),
                (r as? LyricsResult.Error)?.technical ?: m.title?.let { listOfNotNull(it, m.artist).joinToString(" · ") },
            )
        }
    }
}

private fun lines(list: List<LyricLine>, i: Int): String = when {
    i < 0 && i == -1 -> "♪"
    i < 0 -> ""
    else -> list.getOrNull(i)?.text?.ifBlank { "♪" }.orEmpty()
}

// ---------------------------------------------------------------- Hoja desplegable (≈ 52 % de la pantalla)

/** Panel desde abajo: 3 líneas + expandir. Deslizar abajo = cerrar; arriba = pantalla completa. */
@Composable
fun LyricsBottomSheet(m: MediaState, lyrics: LyricsState, onClose: () -> Unit, onExpand: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    val expand by rememberUpdatedState(onExpand)
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(shape)
            .background(Color(0xFF17181B))
            .border(1.dp, Dash.BorderStrong, shape)
            .clickable(enabled = false) {}
            .pointerInput(Unit) { swipeVertical(onDown = { close() }, onUp = { expand() }) },
    ) {
        Box(Modifier.align(Alignment.TopCenter).offset(y = 10.dp).size(44.dp, 5.dp).clip(CircleShape).background(Dash.Track))
        TextAt("Letra", 32, 32, DashType.Title.copy(fontSize = 22.sp))
        SheetButton(DashIcons.ChevD, "Cerrar", Modifier.align(Alignment.TopEnd).offset(x = (-28).dp, y = 20.dp), onClose)

        Box(Modifier.fillMaxWidth().padding(top = 92.dp, bottom = 24.dp).padding(horizontal = 80.dp), contentAlignment = Alignment.Center) {
            when (val r = lyrics.result) {
                is LyricsResult.Synced -> ThreeLines(r.lines, rememberActiveLine(r.lines, m), big = false)
                is LyricsResult.Plain -> Centered("Letra sin sincronizar", "Ábrela en pantalla completa")
                else -> Centered(message(r).orEmpty(), (r as? LyricsResult.Error)?.technical)
            }
        }
        SheetButton(DashIcons.Expand, "Pantalla completa", Modifier.align(Alignment.CenterEnd).offset(x = (-28).dp, y = 30.dp), onExpand)
    }
}

@Composable
private fun SheetButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(56.dp).clip(CircleShape).background(Dash.Well).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DashIcon(icon, 26, Dash.Icon) }
}

// ---------------------------------------------------------------- Gestos verticales

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.swipeVertical(onDown: () -> Unit, onUp: () -> Unit) {
    val threshold = 70.dp.toPx()
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            when {
                total >= threshold -> onDown()
                total <= -threshold -> onUp()
            }
        },
    ) { change, amount ->
        change.consume()
        total += amount
    }
}

/** Deslizar hacia abajo cierra la letra completa (solo si el gesto no lo usó la lista). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.swipeDown(onDown: () -> Unit) =
    swipeVertical(onDown = onDown, onUp = {})
