package com.example.plugins.calls

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.DroidPulseApp
import com.example.data.model.CallDirection
import com.example.data.model.PluginType
import com.example.data.model.RecipeType
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class VoipCallNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ActiveVoipSession(
        val key: String,
        val sourceApp: String,
        val packageName: String,
        val callerLabel: String,
        val direction: CallDirection,
        val startedAt: Long
    )

    private val activeSessions = ConcurrentHashMap<String, ActiveVoipSession>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val app = applicationContext as? DroidPulseApp ?: return
        val pkg = sbn.packageName ?: return

        scope.launch {
            val cfg = app.repository.getConfig(PluginType.CALL_LOGGER) ?: return@launch
            if (!cfg.enabled) return@launch
            val params = TelemetryRepository.parseParams(cfg.paramsJson)

            val matcher = resolveVoipMatcher(pkg, params, app) ?: return@launch
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
            val category = sbn.notification.category
            val combined = "$title $text".lowercase()

            val isMissed = combined.contains("missed")
            val isCallNotification = category == Notification.CATEGORY_CALL ||
                sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                matcher.keywords.any { combined.contains(it) } ||
                matcher.keywords.any { combined.contains(it) }

            if (!isCallNotification && !isMissed) return@launch

            val caller = title.ifBlank { matcher.appLabel }
            if (isMissed) {
                app.callCaptureManager.recordCall(
                    sourceApp = matcher.appLabel,
                    packageName = pkg,
                    contactOrLabel = caller,
                    phoneNumber = "",
                    direction = CallDirection.MISSED,
                    startedAt = sbn.postTime,
                    durationSeconds = 0L,
                    notes = text.ifBlank { "Missed VoIP call" }
                )
                return@launch
            }

            val sessionKey = "${pkg}:${sbn.key}"
            if (!activeSessions.containsKey(sessionKey)) {
                val direction = when {
                    combined.contains("incoming") || combined.contains("answering") -> CallDirection.INCOMING
                    combined.contains("outgoing") || combined.contains("calling") || combined.contains("dialing") -> CallDirection.OUTGOING
                    else -> CallDirection.INCOMING
                }
                activeSessions[sessionKey] = ActiveVoipSession(
                    key = sessionKey,
                    sourceApp = matcher.appLabel,
                    packageName = pkg,
                    callerLabel = caller,
                    direction = direction,
                    startedAt = sbn.postTime
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        val app = applicationContext as? DroidPulseApp ?: return
        val pkg = sbn.packageName ?: return
        val sessionKey = "${pkg}:${sbn.key}"
        val session = activeSessions.remove(sessionKey) ?: return

        val endedAt = System.currentTimeMillis()
        val durationSec = ((endedAt - session.startedAt) / 1000L).coerceAtLeast(1L)

        scope.launch {
            app.callCaptureManager.recordCall(
                sourceApp = session.sourceApp,
                packageName = session.packageName,
                contactOrLabel = session.callerLabel,
                phoneNumber = "",
                direction = session.direction,
                startedAt = session.startedAt,
                durationSeconds = durationSec,
                notes = "Captured via VoIP NotificationListener"
            )
        }
    }

    private data class VoipMatcher(val appLabel: String, val keywords: List<String>)

    private suspend fun resolveVoipMatcher(
        pkg: String,
        params: JSONObject,
        app: DroidPulseApp
    ): VoipMatcher? {
        val defaultKeywords = listOf("call", "calling", "ongoing", "incoming", "voice", "video", "ringing")
        when (pkg) {
            "com.whatsapp", "com.whatsapp.w4b" -> {
                if (params.optBoolean("trackWhatsApp", true)) {
                    return VoipMatcher("WhatsApp", defaultKeywords)
                }
            }
            "org.thoughtcrime.securesms" -> {
                if (params.optBoolean("trackSignal", true)) {
                    return VoipMatcher("Signal", defaultKeywords)
                }
            }
            "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram" -> {
                if (params.optBoolean("trackTelegram", true)) {
                    return VoipMatcher("Telegram", defaultKeywords)
                }
            }
        }

        // Check hot-loaded declarative VOIP_MATCHER recipes
        val recipes = app.repository.getEnabledRecipes(RecipeType.VOIP_MATCHER.id)
        for (recipe in recipes) {
            val obj = runCatching { JSONObject(recipe.configJson) }.getOrNull() ?: continue
            val targetPkg = obj.optString("packageName")
            if (targetPkg == pkg) {
                val kwArray = obj.optJSONArray("keywords")
                val kws = mutableListOf<String>()
                if (kwArray != null) {
                    for (i in 0 until kwArray.length()) {
                        val kw = kwArray.optString(i).lowercase()
                        if (kw.isNotBlank()) kws.add(kw)
                    }
                }
                return VoipMatcher(
                    appLabel = obj.optString("appLabel", recipe.name),
                    keywords = kws.ifEmpty { defaultKeywords }
                )
            }
        }
        return null
    }

    companion object {
        fun isNotificationServiceEnabled(context: Context): Boolean {
            val cn = ComponentName(context, VoipCallNotificationListener::class.java)
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == cn
            }
        }
    }
}
