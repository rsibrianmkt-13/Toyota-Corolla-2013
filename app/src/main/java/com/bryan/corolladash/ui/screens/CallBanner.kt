package com.bryan.corolladash.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.calls.CallAction
import com.bryan.corolladash.calls.CallState
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.PillButton
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import kotlinx.coroutines.delay

/**
 * Aviso de llamada (x330 y16 620×104), sobre todo lo demás, incluso el modo reposo.
 * Los botones son los de la notificación real; si la app no los publica, solo "Abrir".
 */
@Composable
fun CallBanner(call: CallState?, onAction: (CallAction) -> Unit, onOpen: () -> Unit) {
    // Conserva el último valor para animar la salida.
    val last = remember { arrayOfNulls<CallState>(1) }
    if (call != null) last[0] = call
    val c = call ?: last[0]

    AnimatedVisibility(
        visible = call != null,
        modifier = Modifier.at(330, 16, 620, 104),
        enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)),
    ) {
        if (c == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xF71C1D20))
                .border(1.dp, Dash.BorderStrong, shape)
                .clickable(onClickLabel = "Abrir llamada", onClick = onOpen)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Dash.Well), contentAlignment = Alignment.Center) {
                DashIcon(DashIcons.PhoneFill, 30, Dash.Icon)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.caller, style = DashType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle(c), style = DashType.Label.copy(fontSize = 17.sp, color = Dash.TextReadable),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))

            val decline = c.actions.firstOrNull { it.kind == CallAction.Kind.DECLINE }
                ?: c.actions.firstOrNull { it.kind == CallAction.Kind.HANG_UP }
            val answer = c.actions.firstOrNull { it.kind == CallAction.Kind.ANSWER }
            if (decline == null && answer == null) {
                PillButton("Abrir", onOpen)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    decline?.let { CallButton(it, Dash.CallEnd, endCall = true, onAction) }
                    if (c.incoming) answer?.let { CallButton(it, Dash.CallAccept, endCall = false, onAction) }
                }
            }
        }
    }
}

@Composable
private fun CallButton(a: CallAction, color: Color, endCall: Boolean, onAction: (CallAction) -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClickLabel = a.label) { onAction(a) },
        contentAlignment = Alignment.Center,
    ) {
        DashIcon(DashIcons.PhoneFill, 30, Color.White, if (endCall) Modifier.rotate(135f) else Modifier)
    }
}

@Composable
private fun subtitle(c: CallState): String {
    if (c.incoming) return c.detail ?: "Llamada entrante"
    val start = c.startedAt ?: return c.detail ?: "En llamada"
    val now by produceState(System.currentTimeMillis(), start) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val s = ((now - start) / 1000).coerceAtLeast(0)
    return "En llamada · %d:%02d".format(s / 60, s % 60)
}
