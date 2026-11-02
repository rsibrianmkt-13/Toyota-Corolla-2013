package com.bryan.corolladash.speed

import androidx.compose.runtime.Immutable

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

/** Estado real de la velocidad. speedKmh = null → no hay dato (la UI muestra "— km/h"). */
@Immutable
data class SpeedState(
    val speedKmh: Int? = null,
    val permissionGranted: Boolean = false,
    val gpsEnabled: Boolean = false,
    /** Última posición GPS real (null = sin fix). Para el mapa. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Rumbo en grados (0 = norte); null si el GPS no lo da (p. ej. detenido). */
    val bearing: Float? = null,
)

/**
 * Única fuente de datos del vehículo: velocidad por GPS.
 * Flujo frío: empieza a escuchar al suscribirse y libera el GPS al cancelarse.
 */
class GpsSpeedRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun observe(): Flow<SpeedState> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        if (!hasPermission() || lm == null) {
            trySend(SpeedState(permissionGranted = false))
            awaitClose { }
            return@callbackFlow
        }
        val enabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        trySend(SpeedState(permissionGranted = true, gpsEnabled = enabled))

        var last: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                last = location
                // Un fix de más de 5 s ya no representa la velocidad actual.
                val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                val kmh = if (location.hasSpeed() && ageMs < 5_000) (location.speed * 3.6f).roundToInt() else null
                trySend(
                    SpeedState(
                        // Bajo 3 km/h el GPS oscila; se muestra 0.
                        speedKmh = kmh?.let { if (it < 3) 0 else it },
                        permissionGranted = true,
                        gpsEnabled = true,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        // El rumbo solo es fiable en movimiento.
                        bearing = if (location.hasBearing() && (kmh ?: 0) >= 5) location.bearing else null,
                    )
                )
            }
            override fun onProviderEnabled(provider: String) {
                trySend(SpeedState(permissionGranted = true, gpsEnabled = true, latitude = last?.latitude, longitude = last?.longitude))
            }
            override fun onProviderDisabled(provider: String) {
                trySend(SpeedState(permissionGranted = true, gpsEnabled = false))
            }
            @Deprecated("API antigua")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        // Última posición conocida: el mapa puede mostrarse antes del primer fix nuevo.
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()?.let { l ->
            trySend(SpeedState(permissionGranted = true, gpsEnabled = enabled, latitude = l.latitude, longitude = l.longitude))
        }
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}
