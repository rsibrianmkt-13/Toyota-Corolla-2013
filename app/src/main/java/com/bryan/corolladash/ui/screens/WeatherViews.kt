package com.bryan.corolladash.ui.screens

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.ui.components.DashCard
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.TextAt
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.components.tight
import com.bryan.corolladash.ui.theme.DashType
import com.bryan.corolladash.weather.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Código WMO de Open-Meteo → icono monocromático. */
fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0 -> if (isDay) DashIcons.Sun else DashIcons.Moon
    1, 2 -> if (isDay) DashIcons.PartlyCloudy else DashIcons.Cloud
    45, 48 -> DashIcons.Fog
    in 51..67, in 80..82 -> DashIcons.Rain
    in 95..99 -> DashIcons.Storm
    else -> DashIcons.Cloud
}

/** Código WMO → texto corto en español. */
fun weatherText(code: Int, raining: Boolean): String = when {
    raining && code in 95..99 -> "Tormenta"
    raining -> "Lloviendo"
    code == 0 -> "Despejado"
    code in 1..2 -> "Parcialmente nublado"
    code == 3 -> "Nublado"
    code == 45 || code == 48 -> "Neblina"
    code in 51..57 -> "Llovizna"
    code in 61..67 || code in 80..82 -> "Lluvia"
    code in 95..99 -> "Tormenta"
    else -> "Nublado"
}

/** "Lluvia en ~20 min" / "Lluvia hasta ~19 h" / null. */
fun rainHint(w: Weather): String? = when {
    w.raining && w.rainUntilHour != null -> "Lluvia hasta ~${w.rainUntilHour} h"
    w.rainInMin != null -> "Lluvia en ~${w.rainInMin} min"
    else -> null
}

/** Ciudad o municipio actual por geocodificación inversa del sistema (cada ~1 km). */
@Composable
fun rememberPlaceName(lat: Double?, lon: Double?): String? {
    val context = LocalContext.current
    val kLat = lat?.let { Math.round(it * 100) / 100.0 }
    val kLon = lon?.let { Math.round(it * 100) / 100.0 }
    val place by produceState<String?>(null, kLat, kLon) {
        if (lat == null || lon == null) return@produceState
        value = withContext(Dispatchers.IO) { locality(context, lat, lon) } ?: value
    }
    return place
}

@Suppress("DEPRECATION")
private fun locality(context: Context, lat: Double, lon: Double): String? = runCatching {
    if (!Geocoder.isPresent()) return null
    val a = Geocoder(context, Locale("es")).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
    listOf(a.locality, a.subAdminArea, a.adminArea).firstOrNull { !it.isNullOrBlank() }
}.getOrNull()

private val Dim = Color(0xBF8E8E93)
private val Line = Color(0x14FFFFFF)

// ---------------------------------------------------------------- Tarjeta del Inicio (200×214)

/** Clima tenue para el lateral del Inicio: lugar, temperatura, estado y próximas 3 horas. Tocar abre la pantalla de clima. */
@Composable
fun WeatherCard(w: Weather?, place: String?, onOpen: () -> Unit, modifier: Modifier) {
    DashCard(modifier, onClick = onOpen) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0C0D0E)))
        Row(Modifier.offset(18.dp, 18.dp), verticalAlignment = Alignment.CenterVertically) {
            DashIcon(DashIcons.Pin, 14, Dim)
            Spacer(Modifier.width(6.dp))
            Text(place ?: "Clima", style = DashType.Footnote.copy(fontSize = 14.sp, color = Dim), maxLines = 1)
        }
        if (w == null) {
            TextAt("Sin datos", 18, 60, DashType.Label.copy(fontSize = 15.sp, color = Color(0x99C7C7CC)))
        } else {
            WeatherCardBody(w)
        }
    }
}

@Composable
private fun WeatherCardBody(w: Weather) {
    Box(Modifier.fillMaxSize()) {
        TextAt("${w.tempC}°", 16, 48, DashType.LargeTitle.copy(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp, color = Color(0xB8F0F0F2)))
        DashIcon(weatherIcon(w.code, w.isDay), 32, Color(0x8CC7C7CC), Modifier.offset(148.dp, 52.dp))
        TextAt(rainHint(w) ?: weatherText(w.code, w.raining), 18, 104, DashType.Label.copy(fontSize = 15.sp, color = Color(0x99C7C7CC)), maxWidth = 164.dp)
        Box(Modifier.at(18, 138, 164, 1).background(Line))
        Row(Modifier.offset(18.dp, 148.dp).width(164.dp)) {
            w.hours.drop(1).take(3).forEach { h ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(h.label, style = DashType.Footnote.copy(fontSize = 12.sp, color = Color(0x998E8E93)))
                    Spacer(Modifier.height(5.dp))
                    DashIcon(weatherIcon(h.code, h.isDay), 16, Color(0x8CA1A1A6))
                    Spacer(Modifier.height(5.dp))
                    Text("${h.tempC}°", style = DashType.Footnote.copy(fontSize = 13.sp, color = Color(0x99D1D1D6)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Pantalla de clima (1125×571)

@Composable
fun WeatherScreen(w: Weather?, place: String?) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.offset(30.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DashIcon(DashIcons.Pin, 17, Color(0xD98E8E93))
            Spacer(Modifier.width(8.dp))
            Text(place ?: "Ubicación actual", style = DashType.Label.copy(fontSize = 17.sp, color = Color(0xD98E8E93)))
        }
        if (w == null) {
            TextAt("Sin datos del clima (se necesita internet y ubicación)", 30, 80, DashType.Label.copy(fontSize = 20.sp))
        } else {
            WeatherScreenBody(w)
        }
    }
}

@Composable
private fun WeatherScreenBody(w: Weather) {
    Box(Modifier.fillMaxSize()) {
        // Actual
        Text(
            "${w.tempC}°",
            style = DashType.LargeTitle.copy(fontSize = 112.sp, fontWeight = FontWeight.Light, letterSpacing = (-6).sp, color = Color(0xD9F0F0F2)).tight(),
            modifier = Modifier.offset(26.dp, 44.dp),
        )
        DashIcon(weatherIcon(w.code, w.isDay), 70, Color(0xB3C7C7CC), Modifier.offset(280.dp, 68.dp))
        TextAt(listOfNotNull(weatherText(w.code, w.raining), rainHint(w)).joinToString(" · "), 30, 174, DashType.Body.copy(fontSize = 20.sp, color = Color(0xBFC7C7CC)), maxWidth = 390.dp)
        val stats = listOf(
            "Sensación" to (w.feelsC?.let { "$it°" } ?: "—"),
            "Humedad" to (w.humidity?.let { "$it %" } ?: "—"),
            "Viento" to (w.windKmh?.let { "$it km/h" } ?: "—"),
            "Lluvia hoy" to (w.rainTodayMm?.let { String.format(Locale.US, "%.0f mm", it) } ?: "—"),
        )
        stats.forEachIndexed { i, (l, v) ->
            val x = 30 + (i % 2) * 190
            val y = 224 + (i / 2) * 70
            TextAt(l, x, y, DashType.Footnote.copy(fontSize = 14.sp, color = Color(0xE66E6F74)))
            TextAt(v, x, y + 22, DashType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xCCE5E5E7)))
        }
        Box(Modifier.at(440, 10, 1, 540).background(Line))

        // Próximas horas
        TextAt("Próximas horas", 470, 10, DashType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xCC8E8E93)))
        Row(Modifier.offset(470.dp, 44.dp).width(644.dp).height(150.dp)) {
            w.hours.take(7).forEachIndexed { i, h ->
                if (i > 0) Box(Modifier.padding(vertical = 16.dp).width(1.dp).fillMaxHeight().background(Line))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(h.label, style = DashType.Footnote.copy(fontSize = 14.sp, color = if (i == 0) Color(0xE6F0F0F2) else Color(0xCC8E8E93)))
                    DashIcon(weatherIcon(h.code, h.isDay), 28, Color(0xBFC7C7CC))
                    Text("${h.tempC}°", style = DashType.Body.copy(fontSize = 19.sp, color = Color(0xD9E5E5E7)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DashIcon(DashIcons.Drop, 11, Color(0x998E8E93))
                        Spacer(Modifier.width(3.dp))
                        Text("${h.rainProb}%", style = DashType.Footnote.copy(fontSize = 12.sp, color = Color(0x998E8E93)))
                    }
                }
            }
        }
        Box(Modifier.at(470, 210, 640, 1).background(Line))

        // Próximos días
        TextAt("Próximos días", 470, 228, DashType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xCC8E8E93)))
        val lo = (w.days.minOfOrNull { it.minC } ?: 0) - 1
        val hi = (w.days.maxOfOrNull { it.maxC } ?: 1) + 1
        w.days.take(5).forEachIndexed { i, d ->
            val y = 260 + i * 56
            TextAt(d.label, 470, y + 14, DashType.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xCCE5E5E7)))
            DashIcon(weatherIcon(d.code, true), 26, Color(0xB3C7C7CC), Modifier.offset(550.dp, (y + 8).dp))
            Text(
                "${d.minC}°", style = DashType.Body.copy(fontSize = 17.sp, color = Color(0xD98E8E93), textAlign = TextAlign.End).tight(),
                modifier = Modifier.offset(600.dp, (y + 14).dp).width(50.dp),
            )
            val span = (hi - lo).coerceAtLeast(1).toFloat()
            val s0 = (d.minC - lo) / span
            val s1 = (d.maxC - lo) / span
            Box(Modifier.at(670, y + 22, 360, 6).clip(RoundedCornerShape(3.dp)).background(Color(0x12FFFFFF))) {
                Box(
                    Modifier.offset((360 * s0).dp).width((360 * (s1 - s0)).coerceAtLeast(6f).dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF6E6F74), Color(0xFFD8D8DC))))
                )
            }
            TextAt("${d.maxC}°", 1050, y + 14, DashType.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xD9E5E5E7)))
        }
    }
}

// ---------------------------------------------------------------- Combustible (200×258)

/**
 * Medidor de tanque (arco de E a F), autonomía estimada y km/galón. Liviano: tonos grises tenues.
 * [tankLeft] = fracción restante (null si aún no hay recargas suficientes). Tocar abre Combustible.
 */
@Composable
fun FuelCard(tankLeft: Float?, rangeKm: Int?, kmPerGallon: Double?, onOpen: () -> Unit, modifier: Modifier) {
    DashCard(modifier, onClick = onOpen) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0C0D0E)))
        Row(Modifier.offset(18.dp, 18.dp), verticalAlignment = Alignment.CenterVertically) {
            DashIcon(DashIcons.Fuel, 14, Dim)
            Spacer(Modifier.width(6.dp))
            Text("Combustible", style = DashType.Footnote.copy(fontSize = 14.sp, color = Dim), maxLines = 1)
        }
        FuelGauge(tankLeft, Modifier.at(54, 50, 92, 92))
        Text(
            tankLeft?.let { "${(it * 100).toInt()}%" } ?: "—",
            style = DashType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xB8F0F0F2), textAlign = TextAlign.Center).tight(),
            modifier = Modifier.offset(0.dp, 86.dp).width(200.dp),
        )
        Text(
            if (tankLeft != null) "tanque" else "registra recargas",
            style = DashType.Footnote.copy(fontSize = 12.sp, color = Color(0xCC6E6F74), textAlign = TextAlign.Center),
            modifier = Modifier.offset(0.dp, 116.dp).width(200.dp),
        )
        TextAt("E", 60, 138, DashType.Footnote.copy(fontSize = 11.sp, color = Color(0xB36E6F74)))
        TextAt("F", 133, 138, DashType.Footnote.copy(fontSize = 11.sp, color = Color(0xB36E6F74)))
        Box(Modifier.at(18, 176, 164, 1).background(Line))
        TextAt(rangeKm?.let { "~$it km" } ?: "—", 18, 194, DashType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xA6E5E5E7)))
        TextAt("Autonomía", 18, 216, DashType.Footnote.copy(fontSize = 12.sp, color = Color(0xCC6E6F74)))
        Box(Modifier.at(100, 192, 1, 36).background(Line))
        TextAt(kmPerGallon?.let { String.format(Locale.US, "%.1f", it) } ?: "—", 110, 194, DashType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xA6E5E5E7)))
        TextAt("km/gal", 110, 216, DashType.Footnote.copy(fontSize = 12.sp, color = Color(0xCC6E6F74)))
    }
}

@Composable
private fun FuelGauge(frac: Float?, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2
        val sz = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
        val st = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawArc(Color(0xFF1C1D20), 135f, 270f, false, tl, sz, style = st)
        val f = (frac ?: 0f).coerceIn(0f, 1f)
        if (f > 0f) drawArc(
            Brush.sweepGradient(listOf(Color(0xFF5E5E63), Color(0xFFB8B8BE), Color(0xFF5E5E63))),
            135f, 270f * f, false, tl, sz, style = st,
        )
    }
}
