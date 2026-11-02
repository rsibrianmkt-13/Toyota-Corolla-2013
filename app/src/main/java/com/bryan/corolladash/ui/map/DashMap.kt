package com.bryan.corolladash.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bryan.corolladash.nav.GeoPoint
import com.bryan.corolladash.nav.NavRepository
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs

/** Estilo oscuro gratuito de OpenFreeMap (datos © OpenStreetMap). Sin clave de API. */
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val FOLLOW_ZOOM = 16.0

/**
 * Mapa MapLibre monocromático compartido por la tarjeta del Inicio y la pantalla de mapa.
 *  - Posición: punto blanco con halo. Destino: punto blanco con centro oscuro. Ruta: línea blanca.
 *  - [follow]: la cámara sigue la posición (con el rumbo arriba si [headingUp]).
 *  - [fitKey]: al cambiar, encuadra posición + destino + ruta.
 *  - [interactive]: gestos activos; al moverlo con el dedo se llama [onUserMoved].
 */
@Composable
fun DashMap(
    position: GeoPoint?,
    bearing: Float,
    follow: Boolean,
    headingUp: Boolean,
    interactive: Boolean,
    route: List<GeoPoint>?,
    destination: GeoPoint?,
    modifier: Modifier = Modifier,
    fitKey: Any? = null,
    fitPadding: IntArray = intArrayOf(80, 120, 80, 220),
    onUserMoved: () -> Unit = {},
    onLongPress: ((GeoPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        // TextureView en lugar de SurfaceView: así el mapa se desenfoca con el resto bajo el centro de control.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            setMaximumFps(if (interactive) 30 else 20)
        }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val moved by rememberUpdatedState(onUserMoved)
    val longPress by rememberUpdatedState(onLongPress)

    // Ciclo de vida del MapView ligado al de la pantalla.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                setAllGesturesEnabled(interactive)
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true   // © OpenStreetMap: obligatorio
                setAttributionMargins(0, 0, 14, 14)
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) moved()
            }
            m.addOnMapLongClickListener { p ->
                val cb = longPress
                if (cb != null) { cb(GeoPoint(p.latitude, p.longitude)); true } else false
            }
            position?.let { m.moveCamera(CameraUpdateFactory.newCameraPosition(cam(it, if (headingUp) bearing else 0f))) }
            m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { s ->
                s.addSource(GeoJsonSource(SRC_ROUTE))
                s.addSource(GeoJsonSource(SRC_DEST))
                s.addSource(GeoJsonSource(SRC_POS))
                s.addLayer(
                    LineLayer(LYR_ROUTE, SRC_ROUTE).withProperties(
                        PropertyFactory.lineColor("#F5F5F7"),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineOpacity(0.9f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    )
                )
                s.addLayer(
                    CircleLayer(LYR_DEST, SRC_DEST).withProperties(
                        PropertyFactory.circleRadius(10f),
                        PropertyFactory.circleColor("#F5F5F7"),
                        PropertyFactory.circleStrokeWidth(4f),
                        PropertyFactory.circleStrokeColor("#0B0C0E"),
                    )
                )
                s.addLayer(
                    CircleLayer(LYR_POS_HALO, SRC_POS).withProperties(
                        PropertyFactory.circleRadius(22f),
                        PropertyFactory.circleColor("#FFFFFF"),
                        PropertyFactory.circleOpacity(0.12f),
                    )
                )
                s.addLayer(
                    CircleLayer(LYR_POS, SRC_POS).withProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleColor("#F5F5F7"),
                        PropertyFactory.circleStrokeWidth(4f),
                        PropertyFactory.circleStrokeColor("#0B0C0E"),
                    )
                )
                style = s
            }
            map = m
        }
    }

    // Datos dibujados: posición, destino y ruta.
    LaunchedEffect(style, position) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_POS)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(position?.let { Feature.fromGeometry(it.toPoint()) }))
        )
    }
    LaunchedEffect(style, destination) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_DEST)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(destination?.let { Feature.fromGeometry(it.toPoint()) }))
        )
    }
    LaunchedEffect(style, route) {
        val s = style ?: return@LaunchedEffect
        val r = route?.takeIf { it.size >= 2 }
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(r?.let { Feature.fromGeometry(LineString.fromLngLats(it.map { p -> p.toPoint() })) }))
        )
    }

    // Seguir la posición: solo si se movió > 4 m o el rumbo cambió > 4° (evita animar por el ruido del GPS).
    val lastCam = remember { doubleArrayOf(Double.NaN, Double.NaN, 0.0) }
    LaunchedEffect(map, position, bearing, follow, headingUp) {
        val m = map ?: return@LaunchedEffect
        val p = position ?: return@LaunchedEffect
        if (!follow) {
            lastCam[0] = Double.NaN
            return@LaunchedEffect
        }
        val b = if (headingUp) bearing.toDouble() else 0.0
        val changed = lastCam[0].isNaN() ||
            NavRepository.distanceM(GeoPoint(lastCam[0], lastCam[1]), p) > 4.0 ||
            abs(((b - lastCam[2] + 540.0) % 360.0) - 180.0) > 4.0
        if (!changed) return@LaunchedEffect
        lastCam[0] = p.lat; lastCam[1] = p.lon; lastCam[2] = b
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cam(p, b.toFloat())), 900)
    }

    // Encuadrar posición + destino + ruta cuando cambia [fitKey].
    LaunchedEffect(map, fitKey) {
        val m = map ?: return@LaunchedEffect
        if (fitKey == null) return@LaunchedEffect
        val pts = (listOfNotNull(position, destination) + route.orEmpty()).map { LatLng(it.lat, it.lon) }
        if (pts.size < 2) {
            destination?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 15.0), 700) }
            return@LaunchedEffect
        }
        val bounds = LatLngBounds.Builder().includes(pts).build()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, fitPadding[0], fitPadding[1], fitPadding[2], fitPadding[3]), 700)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun GeoPoint.toPoint(): Point = Point.fromLngLat(lon, lat)

private fun cam(p: GeoPoint, bearing: Float): CameraPosition =
    CameraPosition.Builder().target(LatLng(p.lat, p.lon)).zoom(FOLLOW_ZOOM).bearing(bearing.toDouble()).tilt(0.0).build()

private const val SRC_ROUTE = "cd-route"
private const val SRC_DEST = "cd-dest"
private const val SRC_POS = "cd-pos"
private const val LYR_ROUTE = "cd-route-line"
private const val LYR_DEST = "cd-dest-dot"
private const val LYR_POS_HALO = "cd-pos-halo"
private const val LYR_POS = "cd-pos-dot"
