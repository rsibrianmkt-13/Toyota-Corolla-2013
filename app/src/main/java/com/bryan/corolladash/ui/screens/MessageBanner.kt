package com.bryan.corolladash.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.messages.MessageAlert
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType

/**
 * Aviso de mensaje (x330 y16 620×96): icono de la app, remitente y texto.
 * En movimiento no se muestra el texto, solo "Mensaje nuevo". Tocar abre la conversación; ✕ lo cierra.
 * Se oculta solo a los 8 s.
 */
@Composable
fun MessageBanner(alert: MessageAlert?, driving: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit) {
    // Conserva el último valor para animar la salida.
    val last = remember { arrayOfNulls<MessageAlert>(1) }
    if (alert != null) last[0] = alert
    val a = alert ?: last[0]

    AnimatedVisibility(
        visible = alert != null,
        modifier = Modifier.at(330, 16, 620, 96),
        enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)),
    ) {
        if (a == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xF71C1D20))
                .border(1.dp, Dash.BorderStrong, shape)
                .clickable(onClickLabel = "Abrir mensaje", onClick = onOpen)
                .padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Dash.Well), contentAlignment = Alignment.Center) {
                val icon = a.icon
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(52.dp))
                else DashIcon(DashIcons.Lyrics, 26, Dash.Icon)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.appName, style = DashType.Footnote.copy(fontSize = 13.sp, color = Dash.Text3),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    a.sender, style = DashType.Title.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (driving || a.text == null) "Mensaje nuevo" else a.text,
                    style = DashType.Label.copy(fontSize = 17.sp, color = Dash.TextReadable),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Cerrar aviso", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { DashIcon(DashIcons.X, 22, Dash.Icon2) }
        }
    }
}
