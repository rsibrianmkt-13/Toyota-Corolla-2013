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
 * Centro de control (inspirado en iOS), monocromático, centrado en la pantalla.
 * Se cierra tocando fuera; el fondo se desenfoca desde DashboardApp.
 * Bluetooth y Wi-Fi no se pueden conmutar desde una app en Android 15: abren su panel del sistema.
 * Brillo y volumen se cambian directamente (brillo requiere el permiso "Modificar ajustes del sistema").
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
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x80000000))
                .clickable(remember { MutableInteractionSource() }, null, onClick = actions.dismiss)
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.at(PANEL_X, PANEL_Y, PANEL_W, PANEL_H),
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.94f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(40.dp))
                .background(Color(0xF0121315))
                .border(1.dp, Dash.BorderStrong, RoundedCornerShape(40.dp))
                // Toques dentro del panel no lo cierran.
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(26.dp),
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                Column(Modifier.width(540.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                        WideTile(
                            DashIcons.Bt, "Bluetooth",
                            when { !bluetoothOn -> "Desactivado"; phoneConnected -> "Conectado"; else -> "Activado" },
                            active = bluetoothOn, onClick = actions.bluetooth, modifier = Modifier.weight(1f),
                        )
                        WideTile(
                            DashIcons.Wifi, "Wi-Fi", if (wifiOn) "Conectado" else "Sin conexión",
                            active = wifiOn, onClick = actions.wifi, modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                        SquareTile(DashIcons.Moon, "Modo noche", nightOn, actions.toggleNight, Modifier.weight(1f))
                        SquareTile(DashIcons.Eq, "Ecualizador", false, actions.equalizer, Modifier.weight(1f))
                        SquareTile(DashIcons.Gear, "Ajustes", false, actions.settings, Modifier.weight(1f))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                        WideTile(DashIcons.Clock, "Reposo", "Reloj tenue", active = false, onClick = actions.rest, modifier = Modifier.weight(1f))
                        WideTile(DashIcons.Power, "Pantalla", "Apagar", active = false, onClick = actions.screenOff, modifier = Modifier.weight(1f))
                    }
                }

                var vol by remember(visible) { mutableFloatStateOf(controls.volumeFraction()) }
                val canWrite = remember(visible) { controls.canWriteSettings() }
                var bri by remember(visible) { mutableFloatStateOf(controls.brightnessFraction()) }

                if (canWrite) {
                    VerticalSlider(bri, DashIcons.Sun, "Brillo", { bri = it; controls.setBrightness(it) }, Modifier.weight(1f))
                } else {
                    // Sin permiso: el control abre la pantalla para concederlo.
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(Color(0xFF232427))
                            .clickable { controls.openWriteSettingsPermission() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DashIcon(DashIcons.Sun, 36, Dash.Icon2)
                            Spacer(Modifier.height(10.dp))
                            Text("Permitir", style = DashType.Footnote.copy(fontSize = 16.sp, color = Dash.Text2))
                        }
                    }
                }
                VerticalSlider(vol, DashIcons.Vol, "Volumen", { vol = it; controls.setVolume(it) }, Modifier.weight(1f))
            }
        }
    }
}

// Panel centrado en el lienzo de 1280 × 720.
private const val PANEL_W = 900
private const val PANEL_H = 470
private const val PANEL_X = (1280 - PANEL_W) / 2
private const val PANEL_Y = (720 - PANEL_H) / 2
private const val GAP = 16

@Composable
private fun WideTile(icon: ImageVector, title: String, sub: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF1C1D20))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(if (active) Dash.Active else Color(0xFF2C2D31)),
            contentAlignment = Alignment.Center,
        ) { DashIcon(icon, 32, if (active) Dash.Bg else Dash.Icon) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = DashType.Body.copy(fontSize = 21.sp), maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(sub, style = DashType.Footnote.copy(fontSize = 16.sp, color = Dash.Text2), maxLines = 1)
        }
    }
}

@Composable
private fun SquareTile(icon: ImageVector, title: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(32.dp))
            .background(if (active) Dash.Active else Color(0xFF1C1D20))
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        DashIcon(icon, 38, if (active) Dash.Bg else Dash.Icon)
        Text(title, style = DashType.Footnote.copy(fontSize = 17.sp, color = if (active) Dash.Bg else Dash.Text2), maxLines = 1)
    }
}

/** Barra vertical tipo iOS: gris carbón con relleno blanco desde abajo. Tocar o arrastrar. */
@Composable
private fun VerticalSlider(value: Float, icon: ImageVector, label: String, onChange: (Float) -> Unit, modifier: Modifier) {
    val cb by rememberUpdatedState(onChange)
    val v = value.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF232427))
            .pointerInput(Unit) {
                detectTapGestures { o -> cb((1f - o.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ -> cb((1f - change.position.y / size.height).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(v).background(Dash.Active))
        DashIcon(icon, 36, if (v > 0.12f) Dash.Bg else Dash.Icon, Modifier.padding(bottom = 22.dp))
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${(v * 100).roundToInt()}%",
                style = DashType.Body.copy(fontSize = 20.sp, color = if (v > 0.9f) Dash.Bg else Dash.Text),
            )
            Text(label, style = DashType.Footnote.copy(fontSize = 15.sp, color = if (v > 0.84f) Dash.Bg else Dash.Text2))
        }
    }
}
