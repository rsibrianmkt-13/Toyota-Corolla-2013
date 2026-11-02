package com.bryan.corolladash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.media.MediaState
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.WindowBrightness
import com.bryan.corolladash.ui.components.clockStrings
import com.bryan.corolladash.ui.components.rememberNowMillis
import com.bryan.corolladash.ui.components.tight
import com.bryan.corolladash.ui.theme.DashType

/** CLOCK = reloj tenue con la canción; OFF = pantalla negra con el brillo al mínimo. */
enum class RestMode { CLOCK, OFF }

/** Reposo: ocupa toda la pantalla; cualquier toque despierta. */
@Composable
fun RestScreen(mode: RestMode, media: MediaState, resumeTick: Int, onWake: () -> Unit) {
    WindowBrightness(if (mode == RestMode.OFF) 0f else 0.06f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onWake() } },
    ) {
        if (mode == RestMode.CLOCK) RestClock(media, resumeTick, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun RestClock(media: MediaState, resumeTick: Int, modifier: Modifier) {
    val now = rememberNowMillis(resumeTick)
    val (time, date) = remember(now) { clockStrings(now) }
    // Desplazamiento leve cada minuto para no dejar una imagen fija en la pantalla.
    val minute = (now / 60_000L).toInt()
    val dx = ((minute % 7) - 3) * 8
    val dy = ((minute % 5) - 2) * 8

    Column(modifier.offset(dx.dp, dy.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time,
            style = DashType.Clock.copy(
                fontSize = 172.sp, lineHeight = 172.sp, fontWeight = FontWeight.Normal,
                letterSpacing = (-6).sp, color = Color.White.copy(alpha = 0.42f),
            ).tight(),
        )
        Spacer(Modifier.height(18.dp))
        Text(date, style = DashType.Date.copy(fontSize = 26.sp, color = Color.White.copy(alpha = 0.26f)))

        val title = media.title?.takeIf { media.hasSession }
        if (title != null) {
            Spacer(Modifier.height(54.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DashIcon(DashIcons.Music, 22, Color.White.copy(alpha = 0.28f))
                Spacer(Modifier.width(12.dp))
                Text(
                    listOfNotNull(title, media.artist).joinToString(" · "),
                    style = DashType.Body.copy(fontSize = 22.sp, color = Color.White.copy(alpha = 0.32f)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }
    }
}
