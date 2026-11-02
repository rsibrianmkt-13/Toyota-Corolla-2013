package com.bryan.corolladash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.R

/**
 * Sistema monocromático: negro, blanco y grises.
 * El único azul de la interfaz es el propio Corolla (y los colores de carátulas / iconos de apps).
 */
object Dash {
    val Bg = Color(0xFF030304)
    val BgElevated = Color(0xFF08090B)
    val Card = Color(0xFF111214)
    val CardElevated = Color(0xFF161719)
    val Well = Color(0xFF1A1B1E)
    val Border = Color(0x12FFFFFF)        // 7 %
    val BorderStrong = Color(0x21FFFFFF)  // 13 %
    val Text = Color(0xFFF5F5F7)
    val Text2 = Color(0xFFA1A1A6)
    val Text3 = Color(0xFF69696F)
    val Icon = Color(0xFFE5E5E7)
    val Icon2 = Color(0xFFA6A6AB)
    val Track = Color(0xFF303034)
    val Active = Color(0xFFE7E7EA)
    /** Superficie de elemento seleccionado (sidebar, tiles activos). */
    val Selected = Color(0x1FFFFFFF)      // 12 %
    val SelectedBorder = Color(0x12FFFFFF) // 7 %
    val Pressed = Color(0x14FFFFFF)
    /** Textos secundarios que deben leerse con reflejos ("Velocidad", "km/h", artista). */
    val TextReadable = Color(0xFFC7C7CC)
    /** Placeholder de carátula: carbón casi negro. */
    val Placeholder = Color(0xFF141517)
    /** Solo para avisos críticos (conducción). No es color de sistema. */
    val Warn = Color(0xFFFF9F0A)
    /** Únicos colores funcionales: contestar / colgar una llamada. */
    val CallAccept = Color(0xFF30D158)
    val CallEnd = Color(0xFFFF453A)
}

/** Inter (SIL OFL), alternativa libre a SF Pro. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val base = TextStyle(fontFamily = Inter, color = Dash.Text)

object DashType {
    val Clock = base.copy(fontSize = 62.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2.6).sp, lineHeight = 62.sp)
    val Date = base.copy(fontSize = 23.sp, color = Dash.Text2, letterSpacing = (-0.2).sp)
    val Speed = base.copy(fontSize = 92.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp, lineHeight = 92.sp)
    val LargeTitle = base.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    val Title = base.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val Headline = base.copy(fontSize = 21.sp, fontWeight = FontWeight.Medium)
    val Body = base.copy(fontSize = 19.sp)
    val Label = base.copy(fontSize = 17.sp, color = Dash.Text2)
    val Footnote = base.copy(fontSize = 15.sp, color = Dash.Text3)
    val Caption = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Dash.Text3)
}

@Composable
fun CorollaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Dash.Active,
            onPrimary = Dash.Bg,
            background = Dash.Bg,
            surface = Dash.Card,
            onSurface = Dash.Text,
            onBackground = Dash.Text,
        ),
        typography = Typography(bodyLarge = DashType.Body),
        content = content,
    )
}
