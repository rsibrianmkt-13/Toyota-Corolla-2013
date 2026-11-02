package com.bryan.corolladash.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bryan.corolladash.CorollaDashApp

/**
 * No muestra ni guarda notificaciones. Existe porque Android exige un NotificationListener
 * habilitado para leer las MediaSession activas. Además:
 *  - busca la carátula en la notificación de la app Música BT (solo de ese package);
 *  - detecta llamadas (notificaciones de llamada) para el aviso de llamada entrante.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        container()?.media?.refresh()
        try {
            container()?.calls?.rescan(activeNotifications)
        } catch (_: Exception) {
            // Algunas ROM lanzan SecurityException justo al conectar; se recupera con el siguiente aviso.
        }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val c = container() ?: return
        c.media.onNotificationChanged(sbn.packageName)
        c.calls.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        container()?.calls?.onRemoved(sbn)
    }

    private fun container() = (application as? CorollaDashApp)?.container

    companion object {
        @Volatile
        var instance: MediaListenerService? = null
            private set
    }
}
