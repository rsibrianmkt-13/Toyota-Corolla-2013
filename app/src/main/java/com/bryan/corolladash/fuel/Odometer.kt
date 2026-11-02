package com.bryan.corolladash.fuel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Viaje actual: km y tiempo en marcha. Empieza de nuevo tras 30 min sin moverse. */
@androidx.compose.runtime.Immutable
data class Trip(val km: Double, val movingMs: Long)

/**
 * Kilometraje acumulado por GPS (no es el odómetro del carro: empieza en 0 al instalar).
 * Se guarda cada ~100 m para no escribir en cada fix.
 */
class Odometer(context: Context) {
    private val prefs = context.getSharedPreferences("corolla_odometer", Context.MODE_PRIVATE)
    private var meters = prefs.getLong(KEY_METERS, 0L).toDouble()
    private var lastSaved = meters

    private val _km = MutableStateFlow(meters / 1000.0)
    val km: StateFlow<Double> = _km.asStateFlow()

    // Viaje: se guarda junto al kilometraje (sobrevive a que Android cierre la app).
    private var tripMeters = prefs.getLong(KEY_TRIP_M, 0L).toDouble()
    private var tripMs = prefs.getLong(KEY_TRIP_MS, 0L)
    private var lastMoveAt = prefs.getLong(KEY_LAST_MOVE, 0L)
    private val _trip = MutableStateFlow(currentTrip())
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    private fun currentTrip(): Trip =
        if (System.currentTimeMillis() - lastMoveAt > TRIP_GAP_MS) Trip(0.0, 0L) else Trip(tripMeters / 1000.0, tripMs)

    @Synchronized
    fun add(m: Double, movingMs: Long = 0L) {
        if (m <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastMoveAt > TRIP_GAP_MS) { tripMeters = 0.0; tripMs = 0L }
        lastMoveAt = now
        meters += m
        tripMeters += m
        tripMs += movingMs
        _km.value = meters / 1000.0
        _trip.value = Trip(tripMeters / 1000.0, tripMs)
        if (meters - lastSaved >= 100) flush()
    }

    /** Re-evalúa si el viaje terminó (p. ej. al volver a la pantalla tras estar estacionado). */
    fun refreshTrip() { _trip.value = currentTrip() }

    @Synchronized
    fun flush() {
        prefs.edit()
            .putLong(KEY_METERS, meters.toLong())
            .putLong(KEY_TRIP_M, tripMeters.toLong())
            .putLong(KEY_TRIP_MS, tripMs)
            .putLong(KEY_LAST_MOVE, lastMoveAt)
            .apply()
        lastSaved = meters
    }

    private companion object {
        const val KEY_METERS = "gps_meters"
        const val KEY_TRIP_M = "trip_meters"
        const val KEY_TRIP_MS = "trip_moving_ms"
        const val KEY_LAST_MOVE = "trip_last_move"
        const val TRIP_GAP_MS = 30 * 60 * 1000L
    }
}
