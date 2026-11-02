package com.bryan.corolladash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.nav.GeoPoint
import com.bryan.corolladash.nav.Place
import com.bryan.corolladash.nav.RouteInfo
import com.bryan.corolladash.speed.SpeedState
import com.bryan.corolladash.ui.components.DashCard
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.PillButton
import com.bryan.corolladash.ui.map.DashMap
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import kotlinx.coroutines.launch

/** Acciones de la pantalla de mapa. */
class MapActions(
    val search: suspend (String) -> List<Place>,
    val reverse: suspend (GeoPoint) -> Place,
    val setDestination: (Place?) -> Unit,
    val saveHome: (Place) -> Unit,
    val saveWork: (Place) -> Unit,
    val addFavorite: (Place) -> Unit,
    val removeFavorite: (Place) -> Unit,
    val navigate: (Place) -> Unit,
    val toast: (String) -> Unit,
)

private val Panel = Color(0xF7121315)

/**
 * Mapa completo (1125×571): buscar destino, marcar un punto manteniendo presionado,
 * Casa / Trabajo / Guardados, ruta con distancia y hora de llegada, e "Iniciar" en la app de navegación.
 * Con el vehículo en movimiento la búsqueda se bloquea (los accesos rápidos siguen funcionando).
 */
@Composable
fun FullMapScreen(
    speed: SpeedState,
    destination: Place?,
    route: RouteInfo?,
    home: Place?,
    work: Place?,
    favorites: List<Place>,
    locked: Boolean,
    navAppLabel: String,
    a: MapActions,
) {
    val scope = rememberCoroutineScope()
    val position = speed.latitude?.let { la -> speed.longitude?.let { lo -> GeoPoint(la, lo) } }
    var bearing by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speed.bearing) { speed.bearing?.let { bearing = it } }

    var following by remember { mutableStateOf(destination == null) }
    var headingUp by remember { mutableStateOf(false) }
    var fitTick by remember { mutableIntStateOf(if (destination != null) 1 else 0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }

    fun choose(p: Place) {
        a.setDestination(p)
        results = null
        query = ""
        showSaved = false
        following = false
        fitTick++
    }

    fun runSearch() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = a.search(query)
            searching = false
        }
    }

    DashCard(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0C0C0C)))
        DashMap(
            position = position,
            bearing = bearing,
            follow = following,
            headingUp = headingUp,
            interactive = true,
            route = route?.points,
            destination = destination?.point,
            modifier = Modifier.fillMaxSize(),
            fitKey = fitTick.takeIf { it > 0 },
            fitPadding = intArrayOf(90, 160, 90, if (destination != null) 210 else 90),
            onUserMoved = { following = false },
            onLongPress = { p ->
                scope.launch { choose(a.reverse(p)) }
            },
        )

        // ---------------- Búsqueda y accesos rápidos
        Column(Modifier.padding(20.dp).width(520.dp)) {
            SearchBox(query, { query = it; if (it.isBlank()) results = null }, enabled = !locked, onSearch = { runSearch() })
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(DashIcons.Home, "Casa") { home?.let { choose(it) } ?: a.toast("Busca o marca tu casa y toca Guardar → Casa") }
                Chip(DashIcons.Briefcase, "Trabajo") { work?.let { choose(it) } ?: a.toast("Busca o marca tu trabajo y toca Guardar → Trabajo") }
                Chip(DashIcons.Star, "Guardados") { showSaved = !showSaved; results = null }
            }
            when {
                searching -> ListPanel { Text("Buscando…", style = DashType.Label, modifier = Modifier.padding(18.dp)) }
                results != null -> ListPanel {
                    val r = results.orEmpty()
                    if (r.isEmpty()) Text("Sin resultados", style = DashType.Label, modifier = Modifier.padding(18.dp))
                    else LazyColumn { items(r) { p -> PlaceRow(p) { choose(p) } } }
                }
                showSaved -> ListPanel {
                    val all = listOfNotNull(home?.let { "Casa" to it }, work?.let { "Trabajo" to it }) + favorites.map { it.name to it }
                    if (all.isEmpty()) Text("Aún no hay lugares guardados", style = DashType.Label, modifier = Modifier.padding(18.dp))
                    else LazyColumn {
                        items(all) { (label, p) ->
                            PlaceRow(p, title = label, onRemove = if (p in favorites) ({ a.removeFavorite(p) }) else null) { choose(p) }
                        }
                    }
                }
            }
        }
        if (destination == null && results == null && !showSaved) {
            Text(
                if (locked) "Detén el vehículo para buscar" else "Mantén presionado el mapa para marcar un punto",
                style = DashType.Footnote.copy(fontSize = 14.sp),
                modifier = Modifier.padding(start = 560.dp, top = 36.dp),
            )
        }

        // ---------------- Brújula / norte arriba y recentrar
        Column(Modifier.align(Alignment.TopEnd).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundButton(onClick = { headingUp = !headingUp; following = true }) {
                Text(if (headingUp) "↑" else "N", style = DashType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Dash.Text))
            }
            RoundButton(onClick = { following = true }) { DashIcon(DashIcons.Gps, 22, if (following) Dash.Text else Dash.Icon2) }
        }

        // ---------------- Destino
        if (destination != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth().height(150.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Dash.BorderStrong, RoundedCornerShape(24.dp))
                    .padding(horizontal = 26.dp, vertical = 22.dp),
            ) {
                Column(Modifier.width(560.dp)) {
                    Text(destination.name, style = DashType.Title.copy(fontSize = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    destination.address?.let { Text(it, style = DashType.Label.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(14.dp))
                    Text(route?.let { routeSummary(it) } ?: "Calculando ruta…", style = DashType.Body.copy(fontSize = 18.sp, color = Dash.Text))
                }
                Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        PillButton("Guardar", { showSaveMenu = !showSaveMenu })
                    }
                    PillButton("Quitar", { a.setDestination(null); following = true })
                    PillButton("Iniciar en $navAppLabel", { a.navigate(destination) }, strong = true)
                }
            }
            if (showSaveMenu) {
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 360.dp, bottom = 180.dp).width(220.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Dash.BorderStrong, RoundedCornerShape(18.dp)),
                ) {
                    listOf(
                        "Guardar como Casa" to { a.saveHome(destination) },
                        "Guardar como Trabajo" to { a.saveWork(destination) },
                        "Agregar a Guardados" to { a.addFavorite(destination) },
                    ).forEach { (label, action) ->
                        Text(
                            label, style = DashType.Body.copy(fontSize = 17.sp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                action()
                                showSaveMenu = false
                                a.toast("Guardado")
                            }.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, enabled: Boolean, onSearch: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(shape).background(Panel).border(1.dp, Dash.Border, shape).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashIcon(DashIcons.Search, 22, Dash.Text2)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(if (enabled) "Buscar destino o dirección" else "Detén el vehículo para buscar", style = DashType.Body.copy(fontSize = 18.sp, color = Dash.Text3))
            }
            BasicTextField(
                value = if (enabled) value else "",
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = DashType.Body.copy(fontSize = 18.sp, color = Dash.Text),
                cursorBrush = SolidColor(Dash.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty() && enabled) {
            Spacer(Modifier.width(8.dp))
            PillButton("Buscar", onSearch, strong = true)
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.height(44.dp).clip(shape).background(Panel).border(1.dp, Dash.Border, shape).clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashIcon(icon, 18, Dash.Icon)
        Spacer(Modifier.width(8.dp))
        Text(label, style = DashType.Body.copy(fontSize = 16.sp))
    }
}

@Composable
private fun ListPanel(content: @Composable () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Dash.BorderStrong, RoundedCornerShape(20.dp)),
    ) { content() }
}

@Composable
private fun PlaceRow(p: Place, title: String = p.name, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DashType.Body.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            (if (title != p.name) p.name else p.address)?.let {
                Text(it, style = DashType.Footnote.copy(fontSize = 14.sp, color = Dash.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        onRemove?.let { rm ->
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = rm), contentAlignment = Alignment.Center) { DashIcon(DashIcons.X, 18, Dash.Text3) }
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Panel).border(1.dp, Dash.Border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
