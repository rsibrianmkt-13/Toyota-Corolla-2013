package com.bryan.corolladash

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bryan.corolladash.fuel.OdometerService
import com.bryan.corolladash.ui.DashboardApp
import com.bryan.corolladash.ui.Section
import com.bryan.corolladash.ui.components.FixedDesignCanvas
import com.bryan.corolladash.ui.theme.CorollaTheme

class MainActivity : ComponentActivity() {

    private val section = mutableStateOf(Section.HOME)
    /** Cambia cada vez que la app vuelve a primer plano (hora, apps instaladas, etc.). */
    private val resumeTick = mutableIntStateOf(0)
    private val container get() = (application as CorollaDashApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersive()

        setContent {
            CorollaTheme {
                FixedDesignCanvas { DashboardApp(container, section, resumeTick.intValue) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
        container.onForeground()
        resumeTick.intValue++
        // Kilometraje GPS continuo (solo si ya hay permiso de ubicación).
        if (container.gps.hasPermission()) OdometerService.start(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    /**
     * Pantalla completa: barras de Android ocultas; aparecen temporalmente deslizando desde el borde.
     * No bloquea nada: el launcher original se puede volver a elegir en Ajustes.
     */
    private fun applyImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Como launcher: pulsar "Inicio" estando aquí regresa al dashboard principal. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) section.value = Section.HOME
    }
}
