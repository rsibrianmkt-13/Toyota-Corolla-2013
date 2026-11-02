package com.bryan.corolladash.calls

import androidx.compose.runtime.Immutable

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bryan.corolladash.settings.AppConfig
import com.bryan.corolladash.settings.ExternalApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Botón real de la notificación de llamada (contestar, rechazar, colgar). */
@Immutable
data class CallAction(val label: String, val kind: Kind, val intent: PendingIntent) {
    enum class Kind { ANSWER, DECLINE, HANG_UP }
}

/** Llamada en curso leída de la notificación de la app de teléfono. Nunca se inventa. */
@Immutable
data class CallState(
    val key: String,
    val packageName: String,
    val caller: String,
    val detail: String?,
    val incoming: Boolean,
    /** Inicio de la llamada (System.currentTimeMillis) si la app lo publica con cronómetro. */
    val startedAt: Long?,
    val actions: List<CallAction>,
    val contentIntent: PendingIntent?,
)

/**
 * Detecta llamadas a partir de las notificaciones (Android no da otra API pública para
 * las llamadas Bluetooth HFP de la radio). Se considera llamada:
 *  - categoría CALL o plantilla CallStyle (Android 12+), de cualquier app, o
 *  - notificación de la app Teléfono BT configurada con pantalla completa o botones de llamada.
 */
class CallRepository(private val context: Context, private val config: AppConfig) {

    private val _state = MutableStateFlow<CallState?>(null)
    val state: StateFlow<CallState?> = _state.asStateFlow()

    /** Últimas notificaciones relevantes, para el diagnóstico (debug). */
    val recent = ArrayDeque<String>()

    fun onPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == context.packageName) return
        val call = parse(sbn)
        when {
            call != null -> _state.value = call
            _state.value?.key == sbn.key -> _state.value = null
        }
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (_state.value?.key == sbn.key) _state.value = null
    }

    fun rescan(active: Array<StatusBarNotification>?) {
        _state.value = active.orEmpty()
            .filter { it.packageName != context.packageName }
            .firstNotNullOfOrNull { parse(it) }
    }

    /** Ejecuta el botón real de la notificación. */
    fun send(a: CallAction): Boolean = sendIntent(a.intent)

    /** Abre la pantalla de llamada de la app de teléfono (si la notificación la ofrece). */
    fun open(): Boolean = _state.value?.contentIntent?.let { sendIntent(it) } ?: false

    private fun sendIntent(pi: PendingIntent): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(context, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "PendingIntent falló: $e")
        false
    }

    private fun parse(sbn: StatusBarNotification): CallState? {
        val n = sbn.notification ?: return null
        val ex = n.extras ?: Bundle.EMPTY
        val phonePkg = config.packageOf(ExternalApp.PHONE_BT)
        val fromPhone = phonePkg.isNotBlank() && sbn.packageName == phonePkg
        val callType = ex.getInt(EXTRA_CALL_TYPE, 0)

        val actions = buildActions(n, ex)
        val isCall = n.category != CATEGORY_MISSED_CALL && (
            n.category == Notification.CATEGORY_CALL ||
                callType != 0 ||
                (fromPhone && (n.fullScreenIntent != null || actions.isNotEmpty()))
            )

        if (fromPhone || n.category == Notification.CATEGORY_CALL || callType != 0) {
            log(
                "${TIME.format(Date())} ${sbn.packageName} cat=${n.category} type=$callType " +
                    "fs=${n.fullScreenIntent != null} ongoing=${sbn.isOngoing} " +
                    "botones=[${n.actions.orEmpty().joinToString { it.title?.toString().orEmpty() }}] → ${if (isCall) "llamada" else "no"}"
            )
        }
        if (!isCall) return null

        val incoming = callType == CALL_TYPE_INCOMING || (callType == 0 && actions.any { it.kind == CallAction.Kind.ANSWER })
        val personName = if (Build.VERSION.SDK_INT >= 28) parcelable<Person>(ex, EXTRA_CALL_PERSON)?.name?.toString() else null
        val caller = personName?.takeIf { it.isNotBlank() }
            ?: ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() }
            ?: "Llamada"
        val detail = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() && it != caller }
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)

        return CallState(
            key = sbn.key,
            packageName = sbn.packageName,
            caller = caller,
            detail = detail,
            incoming = incoming,
            startedAt = if (!incoming && chrono && n.`when` > 0) n.`when` else null,
            actions = actions,
            contentIntent = n.contentIntent,
        )
    }

    /** Primero los intents de CallStyle; si no existen, los botones clasificados por su texto. */
    private fun buildActions(n: Notification, ex: Bundle): List<CallAction> {
        val out = mutableListOf<CallAction>()
        parcelable<PendingIntent>(ex, EXTRA_ANSWER_INTENT)?.let { out += CallAction("Contestar", CallAction.Kind.ANSWER, it) }
        parcelable<PendingIntent>(ex, EXTRA_DECLINE_INTENT)?.let { out += CallAction("Rechazar", CallAction.Kind.DECLINE, it) }
        parcelable<PendingIntent>(ex, EXTRA_HANG_UP_INTENT)?.let { out += CallAction("Colgar", CallAction.Kind.HANG_UP, it) }
        if (out.isNotEmpty()) return out

        for (a in n.actions.orEmpty()) {
            val pi = a.actionIntent ?: continue
            val t = a.title?.toString()?.lowercase(Locale.ROOT)?.trim().orEmpty()
            val kind = when {
                ANSWER_WORDS.any { t.contains(it) } -> CallAction.Kind.ANSWER
                DECLINE_WORDS.any { t.contains(it) } -> CallAction.Kind.DECLINE
                HANG_UP_WORDS.any { t.contains(it) } -> CallAction.Kind.HANG_UP
                else -> null
            } ?: continue
            if (out.none { it.kind == kind }) out += CallAction(a.title.toString(), kind, pi)
        }
        return out
    }

    private fun log(line: String) {
        Log.d(TAG, line)
        synchronized(recent) {
            recent.addFirst(line)
            while (recent.size > 8) recent.removeLast()
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> parcelable(b: Bundle, key: String): T? =
        try { b.getParcelable(key) as? T } catch (e: Exception) { null }

    private companion object {
        const val TAG = "CorollaCalls"
        // Constantes de Notification (API 31) escritas como texto para compilar con minSdk 26.
        const val EXTRA_CALL_TYPE = "android.callType"
        const val EXTRA_CALL_PERSON = "android.callPerson"
        const val EXTRA_ANSWER_INTENT = "android.answerIntent"
        const val EXTRA_DECLINE_INTENT = "android.declineIntent"
        const val EXTRA_HANG_UP_INTENT = "android.hangUpIntent"
        const val CATEGORY_MISSED_CALL = "missed_call"
        const val CALL_TYPE_INCOMING = 1

        val ANSWER_WORDS = listOf("contestar", "responder", "aceptar", "answer", "accept", "接听", "接聽")
        val DECLINE_WORDS = listOf("rechazar", "declinar", "decline", "reject", "拒绝", "拒接", "拒絕")
        val HANG_UP_WORDS = listOf("colgar", "finalizar", "terminar", "hang up", "end call", "挂断", "掛斷")
        val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}
