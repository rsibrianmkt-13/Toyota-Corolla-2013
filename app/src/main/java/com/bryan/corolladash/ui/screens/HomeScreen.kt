package com.bryan.corolladash.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import com.bryan.corolladash.fuel.Trip
import com.bryan.corolladash.ui.components.tight
import java.util.Locale
import kotlin.math.roundToInt
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
 *   FULL    Velocidad x133 (363) · Música x514 (526) · Mapa x1058 (200×200) + Este viaje (200×272); alto 490 en y100
 *   CLEAN   una sola escena 1125×490: carro integrado al fondo + música sin tarjeta
 *   LYRICS  solo la letra (sin barra superior ni accesos; el menú lateral se mantiene)
 *   Accesos y618 h58: botones de solo línea, iguales en FULL y CLEAN. Todas las esquinas: 24.
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
private const val CARD_H = 490
private const val SPEED_X = 133
private const val SPEED_W = 363
private const val MUSIC_X = 514
private const val SIDE_X = 1058
private const val SIDE_W = 200
private const val BOTTOM_Y = 618
private const val BOTTOM_H = 58

private val SpeedText = Color(0xC7F5F5F7)      // 78 %
private val SpeedLabel = Color(0xB3C7C7CC)
private val TopShade = Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent))
// Estilos creados una sola vez (no en cada recomposición).
private val LabelStyle = DashType.Label.copy(fontSize = 16.sp, color = SpeedLabel)
private val NumberStyle = DashType.Speed.copy(fontSize = 68.sp, lineHeight = 68.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp, color = SpeedText)
private val UnitStyle = DashType.Label.copy(fontSize = 20.sp, color = SpeedLabel)

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
    trip: Trip,
    tankUsed: Float?,
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
                        SpeedCard(speed.speedKmh, speed.permissionGranted, speed.gpsEnabled, a.requestLocation, a.openFuel, Modifier.at(SPEED_X, TOP, SPEED_W, CARD_H).pageSwipe { go(it) })
                        HomeMusicCard(media, phoneConnected, phoneName, music, a.openMusic, Modifier.at(MUSIC_X, TOP, HOME_MUSIC_W, CARD_H))
                        MapCard(speed, destination, route, a.openMap, a.go, Modifier.at(SIDE_X, TOP, SIDE_W, SIDE_W).pageSwipe { go(it) }, width = SIDE_W, height = SIDE_W, compact = true)
                        TripCard(trip, tankUsed, a.openFuel, Modifier.at(SIDE_X, TOP + SIDE_W + 18, SIDE_W, CARD_H - SIDE_W - 18))
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
            if (page == HomePage.FULL) {
                val half = (HOME_MUSIC_W - 12) / 2
                LineButton(SPEED_X, SPEED_W, "Android Auto", DashIcons.Aa, a.androidAuto)
                LineButton(MUSIC_X, half, "Radio FM", DashIcons.Radio, a.radio)
                LineButton(MUSIC_X + half + 12, half, "Música BT", DashIcons.Bt, a.musicBt)
                LineButton(SIDE_X, SIDE_W, "Apps", DashIcons.Grid, a.apps)
            } else {
                val w = (1125 - 36) / 4
                LineButton(133, w, "Android Auto", DashIcons.Aa, a.androidAuto)
                LineButton(133 + (w + 12), w, "Radio FM", DashIcons.Radio, a.radio)
                LineButton(133 + 2 * (w + 12), w, "Música BT", DashIcons.Bt, a.musicBt)
                LineButton(133 + 3 * (w + 12), w, "Aplicaciones", DashIcons.Grid, a.apps)
            }
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
        Modifier.offset((133 + 1125 / 2 - 40).dp, 594.dp).size(80.dp, 18.dp)
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

/** Foto oscura del Corolla a tarjeta completa; velocidad GPS arriba con barra de nivel. */
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
        SpeedText(speedKmh, permissionGranted, gpsEnabled, onRequestLocation, x = 26, y = 30)
    }
}

/**
 * Velocidad: número delgado + "km/h" y una barra de segmentos que se llena con la velocidad (0–140 km/h),
 * más brillante hacia la punta. Se anima suave al acelerar o frenar.
 * Solo recibe la velocidad (no la posición): no se recompone con cada fix GPS si los km/h no cambian.
 */
@Composable
private fun SpeedText(speedKmh: Int?, permissionGranted: Boolean, gpsEnabled: Boolean, onRequestLocation: () -> Unit, x: Int, y: Int) {
    TextAt("Velocidad", x, y, LabelStyle)
    Row(Modifier.offset((x - 3).dp, (y + 22).dp)) {
        Text(speedKmh?.toString() ?: "—", style = NumberStyle.tight(), modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(8.dp))
        Text("km/h", style = UnitStyle, modifier = Modifier.alignByBaseline())
    }
    SpeedBar(speedKmh ?: 0, Modifier.at(x, y + 104, 220, 10))
    when {
        !permissionGranted ->
            TextAt("Permitir ubicación", x, y + 130, DashType.Footnote.copy(color = Dash.Text2), Modifier.clickable(onClick = onRequestLocation))
        !gpsEnabled ->
            TextAt("GPS desactivado", x, y + 130, DashType.Footnote)
    }
}

@Composable
private fun SpeedBar(kmh: Int, modifier: Modifier) {
    val v by animateFloatAsState((kmh / SPEED_MAX).coerceIn(0f, 1f), tween(450), label = "speedBar")
    Canvas(modifier) {
        val n = SPEED_SEGMENTS
        val gap = 3.dp.toPx()
        val sw = (size.width - gap * (n - 1)) / n
        val on = v * n
        for (i in 0 until n) {
            val lit = i < on
            val h = if (lit) size.height else size.height * 0.6f
            val color = if (lit) {
                val k = (i + 1) / on.coerceAtLeast(1f)
                Color(0xFFF5F5F7).copy(alpha = (0.18f + 0.72f * k).coerceIn(0f, 0.9f))
            } else Color(0x12FFFFFF)
            drawRoundRect(color, topLeft = Offset(i * (sw + gap), size.height - h), size = Size(sw, h), cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
}

private const val SPEED_MAX = 140f
private const val SPEED_SEGMENTS = 28

// ---------------------------------------------------------------- Este viaje

/** Km y tiempo en marcha del viaje actual; barra = parte del tanque usada desde la última recarga (si se conoce). */
@Composable
private fun TripCard(trip: Trip, tankUsed: Float?, onOpen: () -> Unit, modifier: Modifier) {
    DashCard(modifier, onClick = onOpen) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0C0D0E)))
        TextAt("Este viaje", 20, 22, DashType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8E8E93)))
        Row(Modifier.offset(20.dp, 58.dp)) {
            Text(String.format(Locale.US, "%.1f", trip.km), style = TripNumber.tight(), modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(5.dp))
            Text("km", style = TripUnit, modifier = Modifier.alignByBaseline())
        }
        TextAt("Recorrido", 20, 96, TripLabel)
        if (tankUsed != null) {
            Box(Modifier.at(20, 124, 160, 6).clip(RoundedCornerShape(3.dp)).background(Color(0xFF24262A))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(tankUsed.coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF9A9AA0), Color(0xFFE7E7EA)))))
            }
            TextAt("${(tankUsed * 100).roundToInt()} % del tanque", 20, 140, DashType.Footnote.copy(fontSize = 12.sp))
        }
        Box(Modifier.at(20, 170, 160, 1).background(Dash.Border))
        val min = (trip.movingMs / 60_000L).toInt()
        Row(Modifier.offset(20.dp, 184.dp)) {
            Text("%d:%02d".format(min / 60, min % 60), style = TripNumber.tight(), modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(5.dp))
            Text("h", style = TripUnit, modifier = Modifier.alignByBaseline())
        }
        TextAt("Manejando", 20, 222, TripLabel)
    }
}

private val TripNumber = DashType.Title.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, color = Color(0xFFE5E5E7))
private val TripUnit = DashType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF8E8E93))
private val TripLabel = DashType.Footnote.copy(fontSize = 14.sp)

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
        // Carro + velocidad: aquí se desliza para cambiar de vista.
        Box(Modifier.at(0, 0, 750, CARD_H).pageSwipe(onSwipe)) {
            Image(
                cachedImage(R.drawable.corolla_scene),
                contentDescription = "Toyota Corolla 2013 azul",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.at(10, -6, 800, 464)
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = a.openFuel),
            )
            SpeedText(speed.speedKmh, speed.permissionGranted, speed.gpsEnabled, a.requestLocation, x = 30, y = 38)
        }
        // Música compacta a la derecha, más cerca del carro.
        CompactCleanMusicPanel(media, phoneConnected, phoneName, music, a.openMusic, Modifier.at(770, 140, 320, 300))
    }
}

// ---------------------------------------------------------------- Accesos inferiores: solo línea

@Composable
private fun LineButton(x: Int, w: Int, title: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.at(x, BOTTOM_Y, w, BOTTOM_H).clip(shape).border(1.dp, Color(0x21FFFFFF), shape)
            .clickable(onClickLabel = title, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashIcon(icon, 24, Color(0x80EBEBF0))
        Spacer(Modifier.width(12.dp))
        Text(title, style = DashType.Headline.copy(fontSize = 18.sp, color = Color(0x9EEBEBF0)), maxLines = 1)
    }
}
