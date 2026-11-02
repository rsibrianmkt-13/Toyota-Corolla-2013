package com.bryan.corolladash.weather

import androidx.compose.runtime.Immutable

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

/** Una hora del pronóstico. */
@Immutable
data class HourForecast(val label: String, val code: Int, val isDay: Boolean, val tempC: Int, val rainProb: Int)

/** Un día del pronóstico. */
@Immutable
data class DayForecast(val label: String, val code: Int, val minC: Int, val maxC: Int)

/** Clima actual. [rainInMin]: minutos hasta que empiece a llover (próximas 2 h), null si no se espera. */
@Immutable
data class Weather(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
    val raining: Boolean,
    val rainInMin: Int?,
    val fetchedAt: Long,
    val feelsC: Int? = null,
    val humidity: Int? = null,
    val windKmh: Int? = null,
    val rainTodayMm: Double? = null,
    /** "Lluvia hasta ~19 h" si llueve ahora y el pronóstico dice cuándo para. */
    val rainUntilHour: Int? = null,
    val hours: List<HourForecast> = emptyList(),
    val days: List<DayForecast> = emptyList(),
)

/**
 * Open-Meteo (open-meteo.com): gratis, sin registro ni clave. Temperatura, condición y lluvia
 * en intervalos de 15 min para las próximas 2 horas, según la posición GPS.
 */
class WeatherRepository {
    private val _state = MutableStateFlow<Weather?>(null)
    val state: StateFlow<Weather?> = _state.asStateFlow()

    @Volatile var lastError: String? = null
        private set

    suspend fun refresh(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        runCatching {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                    "&current=temperature_2m,weather_code,is_day,precipitation,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
                    "&minutely_15=precipitation&forecast_minutely_15=8" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability,is_day,precipitation&forecast_hours=12" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&forecast_days=5" +
                    "&timezone=auto",
                lat, lon,
            )
            val conn = URL(url).openConnection() as HttpURLConnection
            val body = try {
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "CorollaDash/0.8 (Android head unit)")
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            parse(JSONObject(body))
        }.onSuccess {
            _state.value = it
            lastError = null
        }.onFailure {
            lastError = "${it.javaClass.simpleName}: ${it.message}"
            Log.d("CorollaWeather", lastError.orEmpty())
        }
        Unit
    }

    private fun parse(o: JSONObject): Weather {
        val cur = o.getJSONObject("current")
        val offset = ZoneOffset.ofTotalSeconds(o.optInt("utc_offset_seconds", 0))
        val code = cur.optInt("weather_code", 0)
        val raining = cur.optDouble("precipitation", 0.0) >= 0.1 || code in RAIN_CODES

        var rainIn: Int? = null
        if (!raining) {
            o.optJSONObject("minutely_15")?.let { m ->
                val times = m.optJSONArray("time")
                val prec = m.optJSONArray("precipitation")
                val now = System.currentTimeMillis() / 1000
                if (times != null && prec != null) {
                    for (i in 0 until minOf(times.length(), prec.length())) {
                        val t = LocalDateTime.parse(times.getString(i)).toEpochSecond(offset)
                        if (t > now - 15 * 60 && prec.optDouble(i, 0.0) >= 0.1) {
                            rainIn = (((t - now) / 60).coerceAtLeast(0) / 15 * 15).toInt().coerceAtLeast(5)
                            break
                        }
                    }
                }
            }
        }
        // Próximas horas (la primera es "Ahora").
        val hours = mutableListOf<HourForecast>()
        var rainUntil: Int? = null
        o.optJSONObject("hourly")?.let { h ->
            val times = h.optJSONArray("time")
            val temps = h.optJSONArray("temperature_2m")
            val codes = h.optJSONArray("weather_code")
            val probs = h.optJSONArray("precipitation_probability")
            val days = h.optJSONArray("is_day")
            val prec = h.optJSONArray("precipitation")
            val nowSec = System.currentTimeMillis() / 1000
            if (times != null && temps != null) {
                for (i in 0 until times.length()) {
                    val dt = LocalDateTime.parse(times.getString(i))
                    if (dt.toEpochSecond(offset) < nowSec - 3600) continue
                    if (raining && rainUntil == null && (prec?.optDouble(i, 0.0) ?: 0.0) < 0.1 && hours.isNotEmpty()) rainUntil = dt.hour
                    if (hours.size < 7) {
                        hours += HourForecast(
                            label = if (hours.isEmpty()) "Ahora" else "${dt.hour}h",
                            code = codes?.optInt(i, 0) ?: 0,
                            isDay = (days?.optInt(i, 1) ?: 1) == 1,
                            tempC = temps.optDouble(i, 0.0).roundToInt(),
                            rainProb = probs?.optInt(i, 0) ?: 0,
                        )
                    }
                }
            }
        }
        // Próximos días.
        val dayList = mutableListOf<DayForecast>()
        var rainToday: Double? = null
        o.optJSONObject("daily")?.let { d ->
            val times = d.optJSONArray("time")
            val codes = d.optJSONArray("weather_code")
            val maxs = d.optJSONArray("temperature_2m_max")
            val mins = d.optJSONArray("temperature_2m_min")
            rainToday = d.optJSONArray("precipitation_sum")?.optDouble(0)
            val names = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
            if (times != null && maxs != null && mins != null) {
                for (i in 0 until times.length()) {
                    val date = java.time.LocalDate.parse(times.getString(i))
                    dayList += DayForecast(
                        label = if (i == 0) "Hoy" else names[date.dayOfWeek.value - 1],
                        code = codes?.optInt(i, 0) ?: 0,
                        minC = mins.optDouble(i, 0.0).roundToInt(),
                        maxC = maxs.optDouble(i, 0.0).roundToInt(),
                    )
                }
            }
        }
        return Weather(
            tempC = cur.optDouble("temperature_2m", 0.0).roundToInt(),
            code = code,
            isDay = cur.optInt("is_day", 1) == 1,
            raining = raining,
            rainInMin = rainIn,
            fetchedAt = System.currentTimeMillis(),
            feelsC = cur.optDouble("apparent_temperature", Double.NaN).takeIf { !it.isNaN() }?.roundToInt(),
            humidity = cur.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
            windKmh = cur.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() }?.roundToInt(),
            rainTodayMm = rainToday?.takeIf { !it.isNaN() },
            rainUntilHour = rainUntil,
            hours = hours,
            days = dayList,
        )
    }

    private companion object {
        val RAIN_CODES = (51..67).toSet() + (80..82) + (95..99)
    }
}
