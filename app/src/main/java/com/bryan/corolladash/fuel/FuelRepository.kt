package com.bryan.corolladash.fuel

import androidx.compose.runtime.Immutable

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Una recarga de combustible. [odometerKm] = kilometraje GPS en el momento de la recarga. */
@Immutable
data class FillUp(
    val id: Long,
    val timeMillis: Long,
    val odometerKm: Double,
    val gallons: Double,
    val pricePerGallon: Double?,
    val full: Boolean,
)

/** Recarga con su rendimiento calculado (null en la primera o si no fue tanque lleno). */
@Immutable
data class FillUpRow(val fill: FillUp, val kmPerGallon: Double?, val km: Double?)

@Immutable
data class FuelStats(
    val rows: List<FillUpRow>,
    val lastKmPerGallon: Double?,
    val averageKmPerGallon: Double?,
    val costPerKm: Double?,
    val kmSinceLastFill: Double?,
)

/**
 * Registro de recargas (JSON en SharedPreferences, máx. 200).
 * Rendimiento entre dos tanques llenos: km recorridos ÷ galones cargados después del primero
 * (incluye recargas parciales intermedias).
 */
class FuelRepository(context: Context) {
    private val prefs = context.getSharedPreferences("corolla_fuel", Context.MODE_PRIVATE)
    private val _fills = MutableStateFlow(load())
    val fills: StateFlow<List<FillUp>> = _fills.asStateFlow()

    fun add(gallons: Double, pricePerGallon: Double?, full: Boolean, odometerKm: Double) {
        val now = System.currentTimeMillis()
        val list = (_fills.value + FillUp(now, now, odometerKm, gallons, pricePerGallon, full)).takeLast(200)
        save(list)
    }

    fun delete(id: Long) = save(_fills.value.filterNot { it.id == id })

    fun stats(currentKm: Double): FuelStats {
        val list = _fills.value.sortedBy { it.timeMillis }
        val rows = mutableListOf<FillUpRow>()
        var lastFull: FillUp? = null
        var gallonsSince = 0.0
        for (f in list) {
            gallonsSince += f.gallons
            val base = lastFull
            if (f.full) {
                val km = base?.let { f.odometerKm - it.odometerKm }
                val kpg = if (km != null && km > 1 && gallonsSince > 0) km / gallonsSince else null
                rows += FillUpRow(f, kpg, km)
                lastFull = f
                gallonsSince = 0.0
            } else {
                rows += FillUpRow(f, null, null)
            }
        }
        val withKpg = rows.filter { it.kmPerGallon != null }
        val recent = withKpg.takeLast(5)
        val recentGallons = recent.sumOf { gallonsFor(list, it.fill) }
        val avg = if (recent.isEmpty() || recentGallons <= 0) null else recent.sumOf { it.km ?: 0.0 } / recentGallons
        val priced = list.filter { it.pricePerGallon != null }
        val costPerKm = avg?.let { a ->
            priced.takeLast(5).map { it.pricePerGallon!! }.takeIf { it.isNotEmpty() }?.average()?.div(a)
        }
        return FuelStats(
            rows = rows.reversed(),
            lastKmPerGallon = withKpg.lastOrNull()?.kmPerGallon,
            averageKmPerGallon = avg,
            costPerKm = costPerKm,
            kmSinceLastFill = list.lastOrNull()?.let { (currentKm - it.odometerKm).coerceAtLeast(0.0) },
        )
    }

    /** Galones que corresponden al tramo que termina en [end] (tanque lleno anterior → end). */
    private fun gallonsFor(list: List<FillUp>, end: FillUp): Double {
        val i = list.indexOf(end)
        var sum = 0.0
        var j = i
        while (j >= 0) {
            val f = list[j]
            if (j != i && f.full) break
            sum += f.gallons
            j--
        }
        return sum
    }

    private fun load(): List<FillUp> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FillUp(
                id = o.getLong("id"),
                timeMillis = o.getLong("t"),
                odometerKm = o.getDouble("km"),
                gallons = o.getDouble("gal"),
                pricePerGallon = if (o.has("ppg")) o.getDouble("ppg") else null,
                full = o.optBoolean("full", true),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<FillUp>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id); put("t", f.timeMillis); put("km", f.odometerKm); put("gal", f.gallons)
                f.pricePerGallon?.let { put("ppg", it) }
                put("full", f.full)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _fills.value = list
    }

    private companion object {
        const val KEY = "fillups"
    }
}
