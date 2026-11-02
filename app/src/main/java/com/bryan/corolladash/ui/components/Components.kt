package com.bryan.corolladash.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Lienzo fijo de 1280×720 "dp" escalado al tamaño real de la pantalla.
 * Garantiza el mismo aspecto en la VECHTEL (1280×720 px) y en cualquier emulador.
 */
@Composable
fun FixedDesignCanvas(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Dash.Bg),
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(constraints.maxWidth / 1280f, constraints.maxHeight / 720f)
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            Box(
                Modifier
                    .size(1280.dp, 720.dp)
                    .clipToBounds()
                    .background(Dash.Bg),
                content = content,
            )
        }
    }
}

val CardShape = RoundedCornerShape(24.dp)

/** Tarjeta base: gris carbón casi negro, borde blanco al 8 %, leve reducción al presionar. */
@Composable
fun DashCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = CardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.985f else 1f, tween(150), label = "press")
    Box(
        modifier
            .scale(s)
            .clip(shape)
            .background(Dash.Card)
            .border(BorderStroke(1.dp, Dash.Border), shape)
            .then(if (onClick != null) Modifier.clickable(source, null, onClick = onClick) else Modifier),
        content = content,
    )
}

/** Botón circular sin fondo (o gris muy oscuro si [filled]). */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    size: Int,
    iconSize: Int,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when {
        pressed -> Color(0xFF2A2B2F)
        filled -> Color(0xFF1C1D20)
        else -> Color.Transparent
    }
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(source, null, enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DashIcon(icon, iconSize, if (enabled) Dash.Text else Dash.Text3)
    }
}

/** Botón tipo pastilla gris (acciones secundarias). [strong] = blanco con texto negro. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = DashType.Body.copy(
            fontSize = DashType.Label.fontSize,
            color = when {
                !enabled -> Dash.Text3
                strong -> Dash.Bg
                else -> Dash.Text
            },
        ),
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(if (strong && enabled) Dash.Active else Dash.Well)
            .border(1.dp, Dash.Border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
    )
}

/** Hora actual, actualizada al cambiar el minuto; [key] fuerza re-lectura (p. ej. al volver a primer plano). */
@Composable
fun rememberNowMillis(key: Any? = null): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 50)
        }
    }
    return now
}

/** Hora "HH:mm" y fecha corta "28 oct". */
fun clockStrings(now: Long): Pair<String, String> {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)) to
        "${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]}"
}

/**
 * Brillo solo de la ventana de CorollaDash mientras este composable está visible (0 = mínimo).
 * No toca el brillo del sistema ni necesita permisos; al salir se restaura.
 */
@Composable
fun WindowBrightness(value: Float) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(value) {
        val w = activity.window
        w.attributes = w.attributes.apply { screenBrightness = value }
        onDispose {
            w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
