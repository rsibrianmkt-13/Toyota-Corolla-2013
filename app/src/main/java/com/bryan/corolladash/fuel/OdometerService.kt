package com.bryan.corolladash.fuel

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bryan.corolladash.CorollaDashApp
import com.bryan.corolladash.R

/**
 * Servicio en primer plano que suma los kilómetros recorridos con el GPS aunque otra app
 * (Waze, Android Auto…) esté en pantalla. Necesario para calcular el rendimiento de combustible.
 * Filtra el ruido: ignora fixes imprecisos (> 25 m), el "baile" del GPS detenido y saltos imposibles.
 */
class OdometerService : Service() {
    private var lm: LocationManager? = null
    private var last: Location? = null
    private val listener = LocationListener { onLocation(it) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        startUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { lm?.removeUpdates(listener) }
        odometer()?.flush()
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Kilometraje", NotificationManager.IMPORTANCE_MIN))
        }
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("CorollaDash")
            .setContentText("Registrando kilometraje por GPS")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        val m = getSystemService(LocationManager::class.java)
        lm = m
        val ok = runCatching {
            m?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener, Looper.getMainLooper())
        }.isSuccess
        if (!ok || m == null) stopSelf()
    }

    private fun onLocation(l: Location) {
        if (l.hasAccuracy() && l.accuracy > 25f) return
        val p = last
        if (p == null) {
            last = l
            return
        }
        val d = p.distanceTo(l)
        val seconds = ((l.time - p.time).coerceAtLeast(1L)) / 1000f
        when {
            d / seconds > 70f -> last = l                                   // > 250 km/h: salto del GPS
            d < 10f && (!l.hasSpeed() || l.speed < 1.5f) -> Unit            // detenido: ruido, no mover el ancla
            else -> {
                odometer()?.add(d.toDouble())
                last = l
            }
        }
    }

    private fun odometer(): Odometer? = (application as? CorollaDashApp)?.container?.odometer

    companion object {
        private const val CHANNEL = "odometer"
        private const val NOTIF_ID = 42

        /** Iniciar desde la Activity en primer plano (requisito de Android 14 para servicios de ubicación). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, OdometerService::class.java)) }
        }
    }
}
