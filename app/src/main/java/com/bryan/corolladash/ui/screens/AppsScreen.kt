package com.bryan.corolladash.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.launcher.AppEntry
import com.bryan.corolladash.lyrics.SongIdentity
import com.bryan.corolladash.settings.ExternalApp
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType

/** Acceso fijo de la fila "Sistema". [app] = null → Ajustes de CorollaDash. */
class SystemTile(
    val label: String,
    val icon: ImageVector,
    val iconSize: Int,
    val app: ExternalApp?,
    val configured: Boolean,
    /** Acción propia (p. ej. abrir Combustible); si es null se usa [app] o Ajustes. */
    val action: (() -> Unit)? = null,
)

/**
 * Aplicaciones (1125×571), pensada para tocar sin precisión de teléfono:
 *  - "Sistema": accesos grandes, 4 por fila (dos filas si hace falta);
 *  - todas las apps en 6 columnas, iconos de 72 dp y nombres de 17 sp, con desplazamiento vertical.
 * Todo desplaza junto en una sola cuadrícula de 12 columnas (Sistema ocupa 3, cada app 2).
 */
@Composable
fun AppsScreen(
    system: List<SystemTile>,
    apps: List<AppEntry>?,
    canSearch: Boolean,
    onSystem: (SystemTile) -> Unit,
    onLaunch: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = SongIdentity.normalize(query)
    val list = remember(apps, q) {
        apps.orEmpty()
            .sortedBy { SongIdentity.normalize(it.label) }
            .filter { q.isEmpty() || SongIdentity.normalize(it.label).contains(q) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aplicaciones", style = DashType.LargeTitle, modifier = Modifier.weight(1f))
            SearchField(query, { query = it }, enabled = canSearch)
        }
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            GridCells.Fixed(12),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (q.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "sys-label") { SectionLabel("Sistema") }
                items(system, key = { "sys-" + it.label }, span = { GridItemSpan(3) }) { t -> SystemTileView(t) { onSystem(t) } }
                item(span = { GridItemSpan(maxLineSpan) }, key = "sp") { Spacer(Modifier.height(6.dp)) }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "apps-label") {
                SectionLabel(if (q.isEmpty()) "Todas las aplicaciones · ${list.size}" else "Resultados · ${list.size}")
            }
            when {
                apps == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") { Text("Cargando aplicaciones…", style = DashType.Label) }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { Text("No hay apps con ese nombre", style = DashType.Label) }
                else -> items(list, key = { it.packageName }, span = { GridItemSpan(2) }) { app -> AppCell(app) { onLaunch(app.packageName) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = DashType.Caption, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
}

/** Acceso de sistema: icono + nombre en horizontal, 76 de alto. */
@Composable
private fun SystemTileView(t: SystemTile, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(Dash.CardElevated)
            .border(1.dp, Dash.Border, shape)
            .clickable(onClickLabel = t.label, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sin configurar: icono gris (al tocar se abre su selector).
        DashIcon(t.icon, 32, if (t.configured) Dash.Text else Dash.Icon2)
        Spacer(Modifier.width(16.dp))
        Text(
            t.label, style = DashType.Headline.copy(fontSize = 18.sp, color = if (t.configured) Dash.Text else Dash.Text3),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppCell(app: AppEntry, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = app.label, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        Spacer(Modifier.height(10.dp))
        Text(
            app.label, style = DashType.Label.copy(fontSize = 17.sp, color = Dash.TextReadable),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
    }
}

/** Búsqueda: solo con el vehículo detenido (escribir mientras se conduce distrae). */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .size(260.dp, 48.dp)
            .clip(shape)
            .background(Dash.CardElevated)
            .border(1.dp, Dash.Border, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashIcon(DashIcons.Search, 22, Dash.Text2)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(if (enabled) "Buscar app" else "Detén el vehículo", style = DashType.Label.copy(color = Dash.Text3), maxLines = 1)
            }
            BasicTextField(
                value = if (enabled) value else "",
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = DashType.Label.copy(color = Dash.Text),
                cursorBrush = SolidColor(Dash.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            DashIcon(DashIcons.X, 20, Dash.Text2, Modifier.clip(RoundedCornerShape(10.dp)).clickable { onChange("") })
        }
    }
}
