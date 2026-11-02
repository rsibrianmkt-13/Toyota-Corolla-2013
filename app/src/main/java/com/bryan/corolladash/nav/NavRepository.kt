package com.bryan.corolladash.nav

import androidx.compose.runtime.Immutable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

@Immutable
data class GeoPoint(val lat: Double, val lon: Double)

/** Un lugar: destino, resultado de búsqueda o lugar guardado. */
@Immutable
data class Place(val name: String, val address: String?, val lat: Double, val lon: Double) {
    val point get() = GeoPoint(lat, lon)
}

/** Ruta calculada desde [from] hasta el destino. */
@Immutable
data class RouteInfo(val distanceM: Double, val durationS: Double, val points: List<GeoPoint>, val from: GeoPoint)

/**
 * Destino actual, lugares guardados (Casa, Trabajo y favoritos), búsqueda y ruta.
 * Servicios gratuitos sin clave basados en OpenStreetMap:
 *  - búsqueda y dirección: Photon (photon.komoot.io)
 *  - ruta y tiempo: OSRM (router.project-osrm.org)
 * La navegación paso a paso la hace la app de navegación configurada (Waze, Maps…).
 */
class NavRepository(context: Context) {
    private val prefs = context.getSharedPreferences("corolla_nav", Context.MODE_PRIVATE)

    private val _destination = MutableStateFlow(loadPlace(KEY_DEST))
    val destination: StateFlow<Place?> = _destination.asStateFlow()

    private val _route = MutableStateFlow<RouteInfo?>(null)
    val route: StateFlow<RouteInfo?> = _route.asStateFlow()

    private val _home = MutableStateFlow(loadPlace(KEY_HOME))
    val home: StateFlow<Place?> = _home.asStateFlow()

    private val _work = MutableStateFlow(loadPlace(KEY_WORK))
    val work: StateFlow<Place?> = _work.asStateFlow()

    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<Place>> = _favorites.asStateFlow()

    @Volatile var lastError: String? = null
        private set

    fun setDestination(p: Place?) {
        _destination.value = p
        _route.value = null
        savePlace(KEY_DEST, p)
    }

    fun saveHome(p: Place) { _home.value = p; savePlace(KEY_HOME, p) }
    fun saveWork(p: Place) { _work.value = p; savePlace(KEY_WORK, p) }

    fun addFavorite(p: Place) {
        val list = (_favorites.value.filterNot { it.lat == p.lat && it.lon == p.lon } + p).takeLast(20)
        _favorites.value = list
        prefs.edit().putString(KEY_FAVS, JSONArray(list.map { toJson(it) }).toString()).apply()
    }

    fun removeFavorite(p: Place) {
        val list = _favorites.value.filterNot { it == p }
        _favorites.value = list
        prefs.edit().putString(KEY_FAVS, JSONArray(list.map { toJson(it) }).toString()).apply()
    }

    /**
     * Calcula (o recalcula) la ruta desde [from]. Solo consulta si no hay ruta,
     * si el destino cambió o si el carro se movió más de 400 m desde el último cálculo.
     */
    suspend fun updateRoute(from: GeoPoint) {
        val dest = _destination.value ?: return
        val current = _route.value
        if (current != null && distanceM(current.from, from) < 400) return
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val url = String.format(
                    Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                    from.lon, from.lat, dest.lon, dest.lat,
                )
                val o = JSONObject(get(url) ?: error("sin respuesta"))
                val route = o.getJSONArray("routes").getJSONObject(0)
                val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                val pts = (0 until coords.length()).map { i ->
                    val c = coords.getJSONArray(i)
                    GeoPoint(c.getDouble(1), c.getDouble(0))
                }
                RouteInfo(route.getDouble("distance"), route.getDouble("duration"), pts, from)
            }.onFailure { lastError = "Ruta: ${it.javaClass.simpleName}: ${it.message}"; Log.d(TAG, lastError.orEmpty()) }
                .getOrNull()
        }
        // Solo aplicar si el destino no cambió mientras se calculaba.
        if (r != null && _destination.value == dest) _route.value = r
    }

    /** Búsqueda por nombre o dirección, priorizando lugares cercanos. */
    suspend fun search(query: String, near: GeoPoint?): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val bias = near?.let { String.format(Locale.US, "&lat=%.5f&lon=%.5f", it.lat, it.lon) }.orEmpty()
            parsePhoton(get("https://photon.komoot.io/api/?q=$q&limit=6$bias") ?: return@runCatching emptyList())
        }.onFailure { lastError = "Búsqueda: ${it.javaClass.simpleName}" }.getOrDefault(emptyList())
    }

    /** Nombre del punto marcado en el mapa (o sus coordenadas si no hay datos). */
    suspend fun reverse(p: GeoPoint): Place = withContext(Dispatchers.IO) {
        runCatching {
            val url = String.format(Locale.US, "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f", p.lat, p.lon)
            parsePhoton(get(url) ?: "").firstOrNull()?.copy(lat = p.lat, lon = p.lon)
        }.getOrNull() ?: Place("Punto marcado", String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon), p.lat, p.lon)
    }

    /** Intent para navegar al destino con la app configurada (Waze, Google Maps u otra). */
    fun navigationIntent(p: Place, navPackage: String?): Intent {
        val pkg = navPackage?.takeIf { it.isNotBlank() }
        val uri = when {
            pkg?.contains("waze") == true -> String.format(Locale.US, "https://waze.com/ul?ll=%.6f,%.6f&navigate=yes", p.lat, p.lon)
            pkg?.contains("google.android.apps.maps") == true -> String.format(Locale.US, "google.navigation:q=%.6f,%.6f", p.lat, p.lon)
            else -> String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", p.lat, p.lon, p.lat, p.lon, Uri.encode(p.name))
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            pkg?.let { setPackage(it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun parsePhoton(body: String): List<Place> {
        if (body.isBlank()) return emptyList()
        val feats = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until feats.length()).mapNotNull { i ->
            val f = feats.getJSONObject(i)
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val pr = f.optJSONObject("properties") ?: JSONObject()
            val street = listOf(pr.optString("street"), pr.optString("housenumber")).filter { it.isNotBlank() }.joinToString(" ")
            val name = pr.optString("name").ifBlank { street.ifBlank { "Lugar" } }
            val address = listOf(street.takeIf { it != name }, pr.optString("city"), pr.optString("state"))
                .filter { !it.isNullOrBlank() }.distinct().joinToString(", ").ifBlank { null }
            Place(name, address, c.getDouble(1), c.getDouble(0))
        }
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 7000
            conn.readTimeout = 9000
            conn.setRequestProperty("User-Agent", "CorollaDash/0.9 (Android head unit; personal use)")
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun toJson(p: Place) = JSONObject().put("n", p.name).put("a", p.address ?: "").put("lat", p.lat).put("lon", p.lon)

    private fun fromJson(o: JSONObject) = Place(o.getString("n"), o.optString("a").ifBlank { null }, o.getDouble("lat"), o.getDouble("lon"))

    private fun savePlace(key: String, p: Place?) {
        prefs.edit().apply { if (p == null) remove(key) else putString(key, toJson(p).toString()) }.apply()
    }

    private fun loadPlace(key: String): Place? = runCatching { prefs.getString(key, null)?.let { fromJson(JSONObject(it)) } }.getOrNull()

    private fun loadFavorites(): List<Place> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_FAVS, "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    companion object {
        private const val TAG = "CorollaNav"
        private const val KEY_DEST = "destination"
        private const val KEY_HOME = "home"
        private const val KEY_WORK = "work"
        private const val KEY_FAVS = "favorites"

        fun distanceM(a: GeoPoint, b: GeoPoint): Double {
            val x = Math.toRadians(b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
            val y = Math.toRadians(b.lat - a.lat)
            return sqrt(x * x + y * y) * 6_371_000.0
        }
    }
}
