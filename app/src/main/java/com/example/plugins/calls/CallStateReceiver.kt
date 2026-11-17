package com.example.plugins.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.DroidPulseApp
import com.example.data.model.CallDirection
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private var lastState: String? = null
        private var callStartedAt: Long = 0L
        private var wasIncoming: Boolean = false
        private var lastIncomingNumber: String = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val incomingNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!incomingNum.isNullOrBlank()) {
            lastIncomingNumber = incomingNum
        }

        val now = System.currentTimeMillis()
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasIncoming = true
                callStartedAt = now
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (lastState != TelephonyManager.EXTRA_STATE_RINGING) {
                    wasIncoming = false
                    callStartedAt = now
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING ||
                    lastState == TelephonyManager.EXTRA_STATE_OFFHOOK
                ) {
                    val pendingResult = goAsync()
                    val started = if (callStartedAt > 0L) callStartedAt else now
                    val durationSec = ((now - started) / 1000L).coerceAtLeast(0L)
                    val direction = when {
                        lastState == TelephonyManager.EXTRA_STATE_RINGING -> CallDirection.MISSED
                        wasIncoming -> CallDirection.INCOMING
                        else -> CallDirection.OUTGOING
                    }
                    val capturedNum = lastIncomingNumber
                    lastIncomingNumber = ""
                    callStartedAt = 0L

                    val app = context.applicationContext as? DroidPulseApp
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            if (app != null) {
                                val cfg = app.repository.getConfig(PluginType.CALL_LOGGER)
                                val params = TelemetryRepository.parseParams(cfg?.paramsJson ?: "{}")
                                if (cfg?.enabled == true && params.optBoolean("trackPhone", true)) {
                                    // Give Android CallLog provider 1.5s to flush the completed entry
                                    delay(1500L)
                                    val imported = app.callCaptureManager.importRecentDeviceCalls(limit = 1)
                                    if (imported == 0) {
                                        app.callCaptureManager.recordCall(
                                            sourceApp = "Phone",
                                            packageName = "com.android.dialer",
                                            contactOrLabel = capturedNum.ifBlank { "Cellular Call" },
                                            phoneNumber = capturedNum,
                                            direction = direction,
                                            startedAt = started,
                                            durationSeconds = durationSec,
                                            notes = "Captured via PhoneStateReceiver"
                                        )
                                    }
                                }
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
        lastState = state
    }
}
