package com.bryan.corolladash.ui.screens

import android.content.Context
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bryan.corolladash.nav.GeoPoint
import com.bryan.corolladash.nav.Place
import com.bryan.corolladash.nav.RouteInfo
import com.bryan.corolladash.speed.SpeedState
import com.bryan.corolladash.ui.components.DashCard
import com.bryan.corolladash.ui.components.DashIcon
import com.bryan.corolladash.ui.components.DashIcons
import com.bryan.corolladash.ui.components.TextAt
import com.bryan.corolladash.ui.components.at
import com.bryan.corolladash.ui.map.DashMap
import com.bryan.corolladash.ui.theme.Dash
import com.bryan.corolladash.ui.theme.DashType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

private val MapBg = Color(0xFF0C0C0C)

/** "4.2 km · 12 min · llegada 21:23" */
internal fun routeSummary(r: RouteInfo): String {
    val km = r.distanceM / 1000.0
    val dist = if (km < 10) String.format(Locale.US, "%.1f km", km) else String.format(Locale.US, "%.0f km", km)
    val min = (r.durationS / 60).toInt().coerceAtLeast(1)
    val dur = if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
    val cal = Calendar.getInstance().apply { add(Calendar.SECOND, r.durationS.toInt()) }
    val eta = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    return "$dist · $dur · llegada $eta"
}

/**
 * Tarjeta de mapa del Inicio (363×469): sigue la posición GPS con el rumbo arriba, muestra el destino y la ruta.
 * Tocar la tarjeta abre el mapa completo; "Ir" abre la app de navegación con el destino.
 */
@Composable
fun MapCard(
    speed: SpeedState,
    destination: Place?,
    route: RouteInfo?,
    onOpenMap: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier,
    width: Int = 363,
    height: Int = 469,
) {
    val lat = speed.latitude
    val lon = speed.longitude
    var bearing by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speed.bearing) { speed.bearing?.let { bearing = it } }

    DashCard(modifier) {
        Box(Modifier.fillMaxSize().background(MapBg))
        if (lat != null && lon != null) {
            DashMap(
                position = GeoPoint(lat, lon),
                bearing = bearing,
                follow = true,
                headingUp = true,
                interactive = false,
                route = route?.points,
                destination = destination?.point,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            TextAt(
                when {
                    !speed.permissionGranted -> "Permite la ubicación"
                    !speed.gpsEnabled -> "GPS desactivado"
                    else -> "Buscando señal GPS…"
                },
                24, height / 2 - 10, DashType.Label.copy(color = Dash.Text2),
            )
        }

        // Degradados arriba/abajo para integrar con la tarjeta.
        Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(MapBg, Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (destination != null) 150.dp else 100.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to MapBg, 1f to MapBg))
        )

        // Capa transparente: el MapView no recibe toques; tocar abre el mapa completo.
        Box(Modifier.fillMaxSize().clickable(onClickLabel = "Abrir mapa", onClick = onOpenMap))

        Box(Modifier.at(24, 24, 40, 40).clip(CircleShape).background(Dash.Well), contentAlignment = Alignment.Center) {
            DashIcon(DashIcons.Gps, 22, Dash.Icon)
        }
        TextAt("Mapa", 76, 36, DashType.Label.copy(fontSize = 18.sp))
        Box(
            Modifier.at(width - 24 - 40, 24, 40, 40).clip(CircleShape).background(Color(0xE61A1B1E)).border(1.dp, Dash.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("N", style = DashType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dash.Text), modifier = Modifier.rotate(-bearing))
        }

        if (destination != null) {
            TextAt(destination.name, 24, height - 86, DashType.Title.copy(fontSize = 19.sp), maxWidth = (width - 24 - 100).dp)
            TextAt(
                route?.let { routeSummary(it) } ?: "Calculando ruta…",
                24, height - 58, DashType.Label.copy(fontSize = 15.sp, color = Dash.TextReadable), maxWidth = (width - 24 - 100).dp,
            )
            Box(
                Modifier.at(width - 24 - 64, height - 84, 64, 48).clip(RoundedCornerShape(24.dp)).background(Dash.Active)
                    .clickable(onClickLabel = "Iniciar navegación", onClick = onGo),
                contentAlignment = Alignment.Center,
            ) { Text("Ir", style = DashType.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Dash.Bg)) }
        } else if (lat != null && lon != null) {
            val street = rememberStreetName(lat, lon)
            val online = rememberOnline()
            val bottom = when {
                !online -> "Mapa sin conexión"
                street != null -> street
                else -> "Toca para elegir destino"
            }
            TextAt(bottom, 24, height - 46, DashType.Label.copy(fontSize = 16.sp, color = Dash.TextReadable), maxWidth = (width - 48).dp)
        }
    }
}

/** Calle actual por geocodificación inversa del sistema; null si no está disponible. Cada ~100 m. */
@Composable
internal fun rememberStreetName(lat: Double, lon: Double): String? {
    val context = LocalContext.current
    val kLat = Math.round(lat * 1000) / 1000.0
    val kLon = Math.round(lon * 1000) / 1000.0
    val street by produceState<String?>(null, kLat, kLon) {
        value = withContext(Dispatchers.IO) { reverseGeocode(context, lat, lon) } ?: value
    }
    return street
}

@Suppress("DEPRECATION")
private fun reverseGeocode(context: Context, lat: Double, lon: Double): String? = runCatching {
    if (!Geocoder.isPresent()) return null
    Geocoder(context, Locale("es")).getFromLocation(lat, lon, 1)?.firstOrNull()?.thoroughfare?.takeIf { it.isNotBlank() }
}.getOrNull()

@Composable
internal fun rememberOnline(): Boolean {
    val context = LocalContext.current
    val online by produceState(true) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        while (true) {
            value = runCatching {
                val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }.getOrDefault(false)
            delay(10_000)
        }
    }
    return online
}
