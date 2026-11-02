package com.bryan.corolladash.fuel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    @Synchronized
    fun add(m: Double) {
        if (m <= 0) return
        meters += m
        _km.value = meters / 1000.0
        if (meters - lastSaved >= 100) flush()
    }

    @Synchronized
    fun flush() {
        prefs.edit().putLong(KEY_METERS, meters.toLong()).apply()
        lastSaved = meters
    }

    private companion object {
        const val KEY_METERS = "gps_meters"
    }
}
