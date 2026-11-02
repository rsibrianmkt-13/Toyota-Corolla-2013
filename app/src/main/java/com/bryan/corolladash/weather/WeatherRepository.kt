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

/** Clima actual. [rainInMin]: minutos hasta que empiece a llover (próximas 2 h), null si no se espera. */
@Immutable
data class Weather(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
    val raining: Boolean,
    val rainInMin: Int?,
    val fetchedAt: Long,
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
                    "&current=temperature_2m,weather_code,is_day,precipitation" +
                    "&minutely_15=precipitation&forecast_minutely_15=8&timezone=auto",
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
        return Weather(
            tempC = cur.optDouble("temperature_2m", 0.0).roundToInt(),
            code = code,
            isDay = cur.optInt("is_day", 1) == 1,
            raining = raining,
            rainInMin = rainIn,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        val RAIN_CODES = (51..67).toSet() + (80..82) + (95..99)
    }
}
