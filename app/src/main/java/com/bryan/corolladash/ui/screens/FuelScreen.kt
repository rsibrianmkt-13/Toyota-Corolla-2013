package com.bryan.corolladash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.fuel.FillUpRow
import com.bryan.corolladash.fuel.FuelStats
import com.bryan.corolladash.ui.components.DashCard
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.PillButton
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
private fun km(v: Double) = String.format(Locale.US, "%,.0f km", v)

/**
 * Combustible (1125×571): rendimiento en km/galón calculado con el kilometraje GPS entre dos tanques llenos.
 * Registrar recargas solo con el vehículo detenido.
 */
@Composable
fun FuelScreen(
    stats: FuelStats,
    odometerKm: Double,
    locked: Boolean,
    onAdd: (gallons: Double, pricePerGallon: Double?, full: Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Combustible", style = DashType.LargeTitle, modifier = Modifier.weight(1f))
            PillButton(if (locked) "Detén el vehículo" else "Registrar recarga", { adding = true }, strong = !locked, enabled = !locked)
        }

        // ---------------- Resumen
        DashCard(Modifier.at(0, 70, 470, 501)) {
            Column(Modifier.fillMaxSize().padding(28.dp)) {
                Text("RENDIMIENTO", style = DashType.Caption)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        stats.lastKmPerGallon?.let { f1(it) } ?: "—",
                        style = DashType.Speed.copy(fontSize = 76.sp, lineHeight = 80.sp, color = if (stats.lastKmPerGallon == null) Dash.Text3 else Dash.Text),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("km/gal", style = DashType.Label.copy(fontSize = 22.sp, color = Dash.TextReadable), modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    stats.averageKmPerGallon?.let { "Promedio últimas recargas: ${f1(it)} km/gal" }
                        ?: "Registra dos recargas con tanque lleno para ver el rendimiento.",
                    style = DashType.Label.copy(fontSize = 16.sp),
                )
                Spacer(Modifier.height(26.dp))
                Metric("Desde la última recarga", stats.kmSinceLastFill?.let { km(it) } ?: "—")
                Metric("Costo por km", stats.costPerKm?.let { "$" + f2(it) } ?: "—")
                Metric("Kilometraje GPS", km(odometerKm), last = true)
                Spacer(Modifier.weight(1f))
                Text(
                    "El kilometraje lo mide el GPS de la radio desde que se instaló CorollaDash.",
                    style = DashType.Footnote.copy(fontSize = 13.sp),
                )
            }
        }

        // ---------------- Historial
        DashCard(Modifier.at(490, 70, 635, 501)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text("RECARGAS", style = DashType.Caption)
                Spacer(Modifier.height(10.dp))
                if (stats.rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DashIcon(DashIcons.Fuel, 44, Dash.Text3)
                            Spacer(Modifier.height(12.dp))
                            Text("Aún no hay recargas", style = DashType.Headline.copy(color = Dash.Text2))
                            Spacer(Modifier.height(6.dp))
                            Text("Llena el tanque y regístralo para empezar.", style = DashType.Label.copy(fontSize = 15.sp))
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(stats.rows, key = { it.fill.id }) { r -> FillRow(r, canDelete = !locked) { onDelete(r.fill.id) } }
                    }
                }
            }
        }

        if (adding && !locked) {
            AddFillDialog(
                onCancel = { adding = false },
                onSave = { g, p, full ->
                    onAdd(g, p, full)
                    adding = false
                },
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DashType.Label.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
        Text(value, style = DashType.Body.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium))
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.Border))
}

@Composable
private fun FillRow(r: FillUpRow, canDelete: Boolean, onDelete: () -> Unit) {
    val f = r.fill
    val date = remember(f.timeMillis) { SimpleDateFormat("d MMM · HH:mm", Locale("es")).format(Date(f.timeMillis)) }
    Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(date + if (!f.full) " · parcial" else "", style = DashType.Body.copy(fontSize = 18.sp))
            Text(
                "${f2(f.gallons)} gal" + (f.pricePerGallon?.let { " · $" + f2(it) + "/gal · $" + f2(it * f.gallons) } ?: ""),
                style = DashType.Footnote.copy(fontSize = 14.sp, color = Dash.Text2),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.kmPerGallon?.let { f1(it) + " km/gal" } ?: "—", style = DashType.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            r.km?.let { Text(km(it), style = DashType.Footnote.copy(fontSize = 14.sp, color = Dash.Text2)) }
        }
        if (canDelete) {
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "Borrar recarga", onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { DashIcon(DashIcons.X, 18, Dash.Text3) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.Border))
}

/** Galones (obligatorio), precio por galón (opcional) y si se llenó el tanque. */
@Composable
private fun AddFillDialog(onCancel: () -> Unit, onSave: (Double, Double?, Boolean) -> Unit) {
    var gallons by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    fun num(s: String) = s.replace(',', '.').trim().toDoubleOrNull()
    val g = num(gallons)?.takeIf { it > 0 && it < 40 }
    val p = num(price)?.takeIf { it > 0 }

    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000)).pointerInput(Unit) { detectTapGestures { onCancel() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF151619))
                .border(1.dp, Dash.BorderStrong, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(28.dp),
        ) {
            Text("Registrar recarga", style = DashType.Title)
            Spacer(Modifier.height(20.dp))
            NumberField("Galones", gallons, { gallons = it }, "Ej. 10.5")
            Spacer(Modifier.height(12.dp))
            NumberField("Precio por galón (US$, opcional)", price, { price = it }, "Ej. 4.15")
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).clickable { full = !full },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tanque lleno", style = DashType.Body)
                    Text("Necesario para calcular el rendimiento", style = DashType.Footnote.copy(fontSize = 13.sp))
                }
                GraySwitch(full)
            }
            if (g != null && p != null) {
                Spacer(Modifier.height(8.dp))
                Text("Total: $" + f2(g * p), style = DashType.Label.copy(color = Dash.Text))
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
                PillButton("Cancelar", onCancel)
                PillButton("Guardar", { if (g != null) onSave(g, p, full) }, strong = true, enabled = g != null)
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    Text(label, style = DashType.Label.copy(fontSize = 15.sp))
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Dash.Well)
            .border(1.dp, Dash.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, style = DashType.Body.copy(color = Dash.Text3))
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(7)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = DashType.Body.copy(color = Dash.Text, textAlign = TextAlign.Start),
            cursorBrush = SolidColor(Dash.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
