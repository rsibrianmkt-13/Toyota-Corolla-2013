package com.bryan.corolladash.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.R
import com.bryan.corolladash.lyrics.LyricsState
import com.bryan.corolladash.media.MediaState
import com.bryan.corolladash.nav.Place
import com.bryan.corolladash.nav.RouteInfo
import com.bryan.corolladash.speed.SpeedState
import com.bryan.corolladash.ui.components.DashCard
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.TextAt
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.components.cachedImage
import com.bryan.corolladash.ui.music.LyricsPage
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType

/*
 * HOME — lienzo 1280×720. Tres vistas que se cambian deslizando (sobre el carro o el mapa) o tocando los puntos:
 *   FULL    Velocidad x133 · Música x514 · Mapa x895, todas 363×469 en y100
 *   CLEAN   una sola escena 1125×469: carro integrado al fondo + música sin tarjeta
 *   LYRICS  solo la letra (sin barra superior ni accesos; el menú lateral se mantiene)
 *   Accesos y587 h66: x133 · x514 (Radio | Música BT) · x895, todos 363 de ancho
 */
enum class HomePage { FULL, CLEAN, LYRICS }

class HomeActions(
    val requestLocation: () -> Unit,
    val openMusic: () -> Unit,
    val androidAuto: () -> Unit,
    val radio: () -> Unit,
    val musicBt: () -> Unit,
    val apps: () -> Unit,
    val openMap: () -> Unit,
    val go: () -> Unit,
    /** Tocar el carro abre Combustible (registrar recarga / consumo). */
    val openFuel: () -> Unit,
)

private const val TOP = 100
private const val CARD_W = 363
private const val CARD_H = 469
private val SpeedText = Color(0xB3F5F5F7)      // 70 %
private val SpeedLabel = Color(0xB3C7C7CC)
private val TopShade = Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent))
// Estilos creados una sola vez (no en cada recomposición).
private val LabelStyle = DashType.Label.copy(fontSize = 16.sp, color = SpeedLabel)
private val NumberStyle = DashType.Speed.copy(fontSize = 83.sp, lineHeight = 83.sp, letterSpacing = (-2.7).sp, color = SpeedText)
private val UnitStyle = DashType.Label.copy(fontSize = 23.sp, color = SpeedLabel)

@Composable
fun HomeScreen(
    page: HomePage,
    pages: List<HomePage>,
    onPage: (HomePage) -> Unit,
    speed: SpeedState,
    media: MediaState,
    phoneConnected: Boolean,
    phoneName: String?,
    lyrics: LyricsState,
    driving: Boolean,
    destination: Place?,
    route: RouteInfo?,
    music: MusicActions,
    a: HomeActions,
) {
    val current by rememberUpdatedState(page)
    val list by rememberUpdatedState(pages)
    fun go(delta: Int) {
        val i = list.indexOf(current).coerceAtLeast(0)
        list.getOrNull(i + delta)?.let(onPage)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            page,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "homePage",
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            Box(Modifier.fillMaxSize()) {
                when (p) {
                    HomePage.FULL -> {
                        SpeedCard(speed.speedKmh, speed.permissionGranted, speed.gpsEnabled, a.requestLocation, a.openFuel, Modifier.at(133, TOP, CARD_W, CARD_H).pageSwipe { go(it) })
                        HomeMusicCard(media, phoneConnected, phoneName, music, a.openMusic, Modifier.at(514, TOP, CARD_W, CARD_H))
                        MapCard(speed, destination, route, a.openMap, a.go, Modifier.at(895, TOP, CARD_W, CARD_H).pageSwipe { go(it) })
                    }
                    HomePage.CLEAN -> CleanScene(speed, media, phoneConnected, phoneName, music, a, onSwipe = { go(it) })
                    HomePage.LYRICS -> Box(Modifier.fillMaxSize()) {
                        LyricsPage(
                            media, lyrics, driving,
                            Modifier.at(133, 0, 1060, 720).pageSwipe { go(it) },
                        )
                        // Volumen táctil (+ / −, pasos de 10 %).
                        VolumeControl(music, Modifier.offset((1280 - 26 - 64).dp, 250.dp))
                    }
                }
            }
        }

        if (page != HomePage.LYRICS) {
            PageDots(pages, page, onTap = {
                val i = pages.indexOf(page)
                onPage(pages[(i + 1) % pages.size])
            })
            BottomCard(133, "Android Auto", DashIcons.Aa, 26, a.androidAuto)
            SplitBottomCard(514, "Radio FM", DashIcons.Radio, a.radio, "Música BT", DashIcons.Bt, a.musicBt)
            BottomCard(895, "Aplicaciones", DashIcons.Grid, 26, a.apps)
        }
    }
}

/** Deslizar horizontal para cambiar de vista: izquierda = siguiente, derecha = anterior. */
private fun Modifier.pageSwipe(onPage: (Int) -> Unit): Modifier = pointerInput(Unit) {
    val threshold = 80.dp.toPx()
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            when {
                total <= -threshold -> onPage(1)
                total >= threshold -> onPage(-1)
            }
        },
    ) { change, amount ->
        change.consume()
        total += amount
    }
}

@Composable
private fun PageDots(pages: List<HomePage>, current: HomePage, onTap: () -> Unit) {
    Row(
        Modifier.offset((133 + 1125 / 2 - 40).dp, 569.dp).size(80.dp, 18.dp)
            .clip(RoundedCornerShape(9.dp)).clickable(onClickLabel = "Cambiar vista", onClick = onTap),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEach { p ->
            Box(
                Modifier.size(if (p == current) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (p == current) Dash.Active else Color(0xFF3A3B3F))
            )
        }
    }
}

// ---------------------------------------------------------------- Vista completa: Velocidad + Corolla

/** Foto oscura del Corolla a tarjeta completa; velocidad GPS arriba con 70 % de opacidad. */
@Composable
private fun SpeedCard(speedKmh: Int?, permissionGranted: Boolean, gpsEnabled: Boolean, onRequestLocation: () -> Unit, onFuel: () -> Unit, modifier: Modifier) {
    DashCard(modifier, onClick = onFuel) {
        Image(
            cachedImage(R.drawable.corolla_card),
            contentDescription = "Toyota Corolla 2013 azul",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxWidth().height(200.dp).background(TopShade))
        SpeedText(speedKmh, permissionGranted, gpsEnabled, onRequestLocation)
    }
}

/** Solo recibe la velocidad (no la posición): no se recompone con cada fix GPS si los km/h no cambian. */
@Composable
private fun SpeedText(speedKmh: Int?, permissionGranted: Boolean, gpsEnabled: Boolean, onRequestLocation: () -> Unit) {
    TextAt("Velocidad", 30, 38, LabelStyle)
    if (speedKmh != null) {
        TextAt(speedKmh.toString(), 27, 56, NumberStyle)
    } else {
        TextAt("—", 30, 62, DashType.Speed.copy(fontSize = 68.sp, lineHeight = 68.sp, color = Dash.Text3))
    }
    TextAt("km/h", 30, 144, UnitStyle)
    when {
        !permissionGranted ->
            TextAt("Permitir ubicación", 30, 184, DashType.Footnote.copy(color = Dash.Text2), Modifier.clickable(onClick = onRequestLocation))
        !gpsEnabled ->
            TextAt("GPS desactivado", 30, 184, DashType.Footnote)
    }
}

// ---------------------------------------------------------------- Vista limpia: una sola escena

@Composable
private fun CleanScene(
    speed: SpeedState,
    media: MediaState,
    phoneConnected: Boolean,
    phoneName: String?,
    music: MusicActions,
    a: HomeActions,
    onSwipe: (Int) -> Unit,
) {
    Box(Modifier.at(133, TOP, 1125, CARD_H)) {
        // Lado izquierdo (carro + velocidad): aquí se desliza para cambiar de vista.
        Box(Modifier.at(0, 0, 580, CARD_H).pageSwipe(onSwipe)) {
            Image(
                cachedImage(R.drawable.corolla_scene),
                contentDescription = "Toyota Corolla 2013 azul",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.at(40, 60, 525, 305)
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = a.openFuel),
            )
            SpeedText(speed.speedKmh, speed.permissionGranted, speed.gpsEnabled, a.requestLocation)
        }
        CleanMusicPanel(media, phoneConnected, phoneName, music, a.openMusic, Modifier.at(600, 0, 495, CARD_H))
    }
}

// ---------------------------------------------------------------- Accesos inferiores (h66)

private const val BOTTOM_Y = 587
private const val BOTTOM_H = 66

@Composable
private fun IconWell(icon: ImageVector, size: Int, x: Int) {
    Box(
        Modifier.at(x, 11, 44, 44).clip(RoundedCornerShape(13.dp)).background(Dash.Well).border(1.dp, Dash.Border, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) { DashIcon(icon, size, Dash.Icon) }
}

@Composable
private fun BottomCard(x: Int, title: String, icon: ImageVector, iconSize: Int, onClick: () -> Unit) {
    DashCard(Modifier.at(x, BOTTOM_Y, CARD_W, BOTTOM_H), onClick = onClick) {
        IconWell(icon, iconSize, 11)
        TextAt(title, 68, 23, DashType.Headline.copy(fontSize = 19.sp))
        DashIcon(DashIcons.Chev, 22, Dash.Text3, Modifier.offset((CARD_W - 38).dp, 22.dp))
    }
}

/** Una tarjeta dividida en dos botones (Radio FM | Música BT). */
@Composable
private fun SplitBottomCard(
    x: Int,
    leftTitle: String, leftIcon: ImageVector, onLeft: () -> Unit,
    rightTitle: String, rightIcon: ImageVector, onRight: () -> Unit,
) {
    val half = CARD_W / 2
    DashCard(Modifier.at(x, BOTTOM_Y, CARD_W, BOTTOM_H)) {
        Box(Modifier.at(0, 0, half, BOTTOM_H).clickable(onClickLabel = leftTitle, onClick = onLeft)) {
            IconWell(leftIcon, 28, 11)
            TextAt(leftTitle, 68, 23, DashType.Headline.copy(fontSize = 19.sp), maxWidth = (half - 72).dp)
        }
        Box(Modifier.at(half, 14, 1, 38).fillMaxHeight().background(Color(0x17FFFFFF)))
        Box(Modifier.at(half, 0, CARD_W - half, BOTTOM_H).clickable(onClickLabel = rightTitle, onClick = onRight)) {
            IconWell(rightIcon, 24, 11)
            TextAt(rightTitle, 68, 23, DashType.Headline.copy(fontSize = 19.sp), maxWidth = (CARD_W - half - 72).dp)
        }
    }
}
