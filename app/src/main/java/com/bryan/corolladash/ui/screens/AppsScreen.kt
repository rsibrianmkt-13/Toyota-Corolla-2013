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
 * Aplicaciones (1125×571):
 *  - fila fija "Sistema" con iconos blancos (abren la app configurada para cada función);
 *  - todas las apps instaladas en orden alfabético, con búsqueda.
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

        if (q.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Sistema")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                system.forEach { t -> SystemTileView(t) { onSystem(t) } }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(if (q.isEmpty()) "Todas las aplicaciones · ${list.size}" else "Resultados · ${list.size}")
        when {
            apps == null -> Text("Cargando aplicaciones…", style = DashType.Label)
            list.isEmpty() -> Text("No hay apps con ese nombre", style = DashType.Label)
            else -> LazyVerticalGrid(
                GridCells.Fixed(8),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(list, key = { it.packageName }) { app -> AppCell(app) { onLaunch(app.packageName) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = DashType.Caption, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
}

@Composable
private fun SystemTileView(t: SystemTile, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .size(140.dp, 118.dp)
            .clip(shape)
            .background(Dash.CardElevated)
            .border(1.dp, Dash.Border, shape)
            .clickable(onClickLabel = t.label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(78.dp), contentAlignment = Alignment.Center) {
            // Sin configurar: icono gris (al tocar se abre su selector).
            DashIcon(t.icon, t.iconSize, if (t.configured) Dash.Text else Dash.Icon2)
        }
        Text(
            t.label, style = DashType.Label.copy(fontSize = 16.sp, color = if (t.configured) Dash.TextReadable else Dash.Text3),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppCell(app: AppEntry, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = app.label, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(10.dp))
        Text(
            app.label, style = DashType.Label.copy(fontSize = 15.sp, color = Dash.TextReadable),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
