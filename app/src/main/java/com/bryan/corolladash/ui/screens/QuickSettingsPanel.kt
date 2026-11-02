package com.bryan.corolladash.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.settings.SystemControls
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import kotlin.math.roundToInt

class QuickActions(
    val bluetooth: () -> Unit,
    val wifi: () -> Unit,
    val toggleNight: () -> Unit,
    val equalizer: () -> Unit,
    val settings: () -> Unit,
    val rest: () -> Unit,
    val screenOff: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * Centro de control, con el mismo lenguaje de líneas finas del resto de la app (sin cajas pesadas):
 *  arriba  Bluetooth · Wi-Fi · Ecualizador · Ajustes (punto blanco = activo)
 *  medio   Brillo y Volumen en barras horizontales anchas
 *  abajo   Modo noche · Reposo · Apagar pantalla
 * Se cierra tocando fuera; el fondo se desenfoca desde DashboardApp.
 * Bluetooth y Wi-Fi no se pueden conmutar desde una app en Android 15: abren su panel del sistema.
 */
@Composable
fun QuickSettingsPanel(
    visible: Boolean,
    bluetoothOn: Boolean,
    phoneConnected: Boolean,
    wifiOn: Boolean,
    nightOn: Boolean,
    controls: SystemControls,
    actions: QuickActions,
    phoneName: String? = null,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x73000000))
                .clickable(remember { MutableInteractionSource() }, null, onClick = actions.dismiss)
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.at(PANEL_X, PANEL_Y, PANEL_W, PANEL_H),
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
    ) {
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xDB121316))
                .border(1.dp, Color(0x1AFFFFFF), shape)
                // Toques dentro del panel no lo cierran.
                .clickable(remember { MutableInteractionSource() }, null) {}
        ) {
            // ---- Arriba: interruptores
            val cw = (PANEL_W - 48) / 4
            val toggles = listOf(
                ToggleItem(DashIcons.Bt, "Bluetooth", when { !bluetoothOn -> "Desactivado"; phoneConnected -> phoneName ?: "Conectado"; else -> "Activado" }, bluetoothOn, actions.bluetooth),
                ToggleItem(DashIcons.Wifi, "Wi-Fi", if (wifiOn) "Conectado" else "Sin conexión", wifiOn, actions.wifi),
                ToggleItem(DashIcons.Eq, "Ecualizador", "Abrir", false, actions.equalizer),
                ToggleItem(DashIcons.Gear, "Ajustes", "CorollaDash", false, actions.settings),
            )
            toggles.forEachIndexed { i, t ->
                val x = 24 + i * cw
                if (i > 0) Box(Modifier.at(x, 48, 1, 92).background(LineColor))
                Column(
                    Modifier.at(x, 24, cw, 140).clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = t.label, onClick = t.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DashIcon(t.icon, 32, if (t.on) Dash.Text else Color(0x80EBEBF0))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (t.on) Dash.Text else Color.Transparent))
                    Spacer(Modifier.height(8.dp))
                    Text(t.label, style = DashType.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = if (t.on) Dash.Text else Color(0xB3EBEBF0)), maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(t.state, style = DashType.Footnote.copy(fontSize = 13.sp, color = Color(0xFF8E8E93)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
            Box(Modifier.at(24, 172, PANEL_W - 48, 1).background(LineColor))

            // ---- Medio: barras
            var vol by remember(visible) { mutableFloatStateOf(controls.volumeFraction()) }
            val canWrite = remember(visible) { controls.canWriteSettings() }
            var bri by remember(visible) { mutableFloatStateOf(controls.brightnessFraction()) }
            HBarRow(DashIcons.Sun, bri, enabled = canWrite, y = 196, onChange = { bri = it; controls.setBrightness(it) }, onDisabledClick = { controls.openWriteSettingsPermission() })
            HBarRow(DashIcons.Vol, vol, enabled = true, y = 268, onChange = { vol = it; controls.setVolume(it) }, onDisabledClick = {})
            Box(Modifier.at(24, 348, PANEL_W - 48, 1).background(LineColor))

            // ---- Abajo: modos
            val aw = (PANEL_W - 48) / 3
            val modes = listOf(
                ToggleItem(DashIcons.Moon, "Modo noche", "", nightOn, actions.toggleNight),
                ToggleItem(DashIcons.Clock, "Reposo", "", false, actions.rest),
                ToggleItem(DashIcons.Power, "Apagar pantalla", "", false, actions.screenOff),
            )
            modes.forEachIndexed { i, t ->
                val x = 24 + i * aw
                if (i > 0) Box(Modifier.at(x, 376, 1, 56).background(LineColor))
                Row(
                    Modifier.at(x, 360, aw, 88).clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = t.label, onClick = t.onClick),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DashIcon(t.icon, 26, if (t.on) Dash.Text else Color(0xB3EBEBF0))
                    Spacer(Modifier.width(14.dp))
                    Text(t.label, style = DashType.Body.copy(fontSize = 18.sp, color = if (t.on) Dash.Text else Color(0xCCEBEBF0)), maxLines = 1)
                }
            }
        }
    }
}

private class ToggleItem(val icon: ImageVector, val label: String, val state: String, val on: Boolean, val onClick: () -> Unit)

private val LineColor = Color(0x14FFFFFF)

// Panel centrado en el lienzo de 1280 × 720.
private const val PANEL_W = 900
private const val PANEL_H = 470
private const val PANEL_X = (1280 - PANEL_W) / 2
private const val PANEL_Y = (720 - PANEL_H) / 2

/** Barra horizontal ancha: icono, pista de 16 px con relleno degradado y perilla, porcentaje a la derecha. Tocar o arrastrar. */
@Composable
private fun HBarRow(icon: ImageVector, value: Float, enabled: Boolean, y: Int, onChange: (Float) -> Unit, onDisabledClick: () -> Unit) {
    val cb by rememberUpdatedState(onChange)
    val v = value.coerceIn(0f, 1f)
    val trackX = 98
    val trackW = PANEL_W - 40 - 30 - 28 - 120
    DashIcon(icon, 28, Color(0xB3EBEBF0), Modifier.offset(40.dp, (y + 9).dp))
    if (!enabled) {
        // Sin permiso para el brillo del sistema: tocar abre la pantalla para concederlo.
        Box(
            Modifier.at(trackX, y + 4, trackW, 38).clip(RoundedCornerShape(19.dp)).border(1.dp, LineColor, RoundedCornerShape(19.dp))
                .clickable(onClick = onDisabledClick),
            contentAlignment = Alignment.Center,
        ) { Text("Permitir control de brillo", style = DashType.Label.copy(fontSize = 15.sp, color = Color(0xB3EBEBF0))) }
        return
    }
    Box(
        Modifier
            .at(trackX - 13, y, trackW + 26, 46)
            .pointerInput(Unit) { detectTapGestures { o -> cb(((o.x - 13.dp.toPx()) / (size.width - 26.dp.toPx())).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    cb(((change.position.x - 13.dp.toPx()) / (size.width - 26.dp.toPx())).coerceIn(0f, 1f))
                }
            }
    ) {
        Box(Modifier.at(13, 15, trackW, 16).clip(RoundedCornerShape(8.dp)).background(Color(0x1AFFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(v.coerceAtLeast(0.04f)).clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF9A9AA0), Color(0xFFF2F2F4)))))
        }
        Box(Modifier.at((13 + trackW * v - 13).toInt(), 10, 26, 26).clip(CircleShape).background(Dash.Text))
    }
    Text(
        "${(v * 100).roundToInt()}%",
        style = DashType.Body.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Dash.Text, textAlign = TextAlign.End),
        modifier = Modifier.offset((PANEL_W - 104).dp, (y + 10).dp).width(64.dp),
    )
}
