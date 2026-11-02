package com.bryan.corolladash.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Utilidades para el Home de posiciones fijas.
 * Dentro de FixedDesignCanvas 1 dp = 1 px del diseño de 1280×720,
 * así que las coordenadas del prototipo se usan tal cual.
 */

/** Posiciona en (x, y) y fija tamaño. */
fun Modifier.at(x: Int, y: Int, w: Int, h: Int): Modifier = offset(x.dp, y.dp).size(w.dp, h.dp)

fun Modifier.at(x: Int, y: Int): Modifier = offset(x.dp, y.dp)

/** Igual que CSS `line-height: 1`: la caja del texto mide exactamente el tamaño de fuente. */
fun TextStyle.tight(): TextStyle = copy(
    lineHeight = fontSize,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun TextAt(
    text: String,
    x: Int,
    y: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxWidth: Dp? = null,
) {
    Text(
        text,
        style = style.tight(),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(x.dp, y.dp)
            .then(modifier)
            .then(if (maxWidth != null) Modifier.width(maxWidth) else Modifier),
    )
}

@Composable
fun DashIcon(icon: ImageVector, size: Int, tint: Color, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size.dp))
}
