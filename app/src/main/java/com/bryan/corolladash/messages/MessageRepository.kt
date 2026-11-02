package com.bryan.corolladash.messages

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Mensaje nuevo para el aviso en pantalla. */
@Immutable
data class MessageAlert(
    val key: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val text: String?,
    val postedAt: Long,
    val icon: ImageBitmap?,
    val contentIntent: PendingIntent?,
)

/**
 * Avisos de mensajes (WhatsApp, Telegram, SMS…) de las apps instaladas EN LA RADIO.
 * Las notificaciones del teléfono no llegan por Bluetooth: WhatsApp debe estar vinculado en la radio
 * como dispositivo acompañante. No se guarda nada: solo el último aviso, que se oculta solo.
 */
class MessageRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _current = MutableStateFlow<MessageAlert?>(null)
    val current: StateFlow<MessageAlert?> = _current.asStateFlow()

    private val lastText = HashMap<String, String>()
    private val icons = HashMap<String, ImageBitmap?>()
    private var hideJob: Job? = null

    /** Últimos avisos detectados (Diagnóstico). */
    val recent = ArrayDeque<String>()

    fun onPosted(sbn: StatusBarNotification) {
        val alert = parse(sbn) ?: return
        val sig = "${alert.sender}|${alert.text}"
        if (lastText[sbn.key] == sig) return            // misma notificación actualizada: no repetir
        lastText[sbn.key] = sig
        if (lastText.size > 200) lastText.clear()
        log("${alert.appName}: ${alert.sender}")
        _current.value = alert
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(SHOW_MS)
            if (_current.value?.key == alert.key) _current.value = null
        }
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (_current.value?.key == sbn.key) _current.value = null
        lastText.remove(sbn.key)
    }

    fun dismiss() {
        hideJob?.cancel()
        _current.value = null
    }

    /** Abre la conversación en la app (o la app) y quita el aviso. */
    fun open(): Boolean {
        val a = _current.value ?: return false
        dismiss()
        a.contentIntent?.let { pi ->
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(context, 0, null, null, null, null, opts.toBundle())
                } else {
                    pi.send()
                }
            }.isSuccess
            if (ok) return true
        }
        val launch = context.packageManager.getLaunchIntentForPackage(a.packageName) ?: return false
        return runCatching { context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun parse(sbn: StatusBarNotification): MessageAlert? {
        if (sbn.packageName == context.packageName) return null
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (sbn.isOngoing) return null
        if (n.category == Notification.CATEGORY_CALL || n.category == "missed_call") return null
        val ex = n.extras ?: Bundle.EMPTY
        val messaging = ex.containsKey(Notification.EXTRA_MESSAGES)
        val isMessage = n.category == Notification.CATEGORY_MESSAGE || messaging || sbn.packageName in MESSAGING_APPS
        if (!isMessage) return null

        // MessagingStyle: último mensaje y su remitente.
        var sender: String? = null
        var text: String? = null
        if (messaging) {
            @Suppress("DEPRECATION")
            val msgs = runCatching { ex.getParcelableArray(Notification.EXTRA_MESSAGES) }.getOrNull()
            (msgs?.lastOrNull() as? Bundle)?.let { last ->
                text = last.getCharSequence("text")?.toString()
                sender = last.getCharSequence("sender")?.toString()
                    ?: if (Build.VERSION.SDK_INT >= 28) personName(last) else null
            }
        }
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val conversation = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        sender = sender ?: title
        text = text ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (sender.isNullOrBlank() && text.isNullOrBlank()) return null
        // Resúmenes tipo "3 mensajes nuevos" sin remitente: ignorar.
        if (sender.isNullOrBlank()) return null
        val who = if (!conversation.isNullOrBlank() && conversation != sender) "$sender · $conversation" else sender!!

        val pm = context.packageManager
        val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val icon = icons.getOrPut(sbn.packageName) {
            runCatching { pm.getApplicationIcon(sbn.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        return MessageAlert(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            sender = who,
            text = text?.takeIf { it.isNotBlank() },
            postedAt = sbn.postTime,
            icon = icon,
            contentIntent = n.contentIntent,
        )
    }

    @Suppress("DEPRECATION")
    private fun personName(b: Bundle): String? = runCatching {
        (b.getParcelable<Parcelable>("sender_person") as? android.app.Person)?.name?.toString()
    }.getOrNull()

    private fun log(line: String) {
        synchronized(recent) {
            recent.addFirst(line)
            while (recent.size > 6) recent.removeLast()
        }
    }

    private companion object {
        const val SHOW_MS = 8_000L
        val MESSAGING_APPS = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
            "com.facebook.orca", "com.facebook.mlite",
        )
    }
}
