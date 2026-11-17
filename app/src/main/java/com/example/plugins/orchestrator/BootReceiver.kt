package com.example.plugins.orchestrator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.DroidPulseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val app = context.applicationContext as? DroidPulseApp ?: return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.ensureSeeded()
                    app.orchestrator.syncAllPlugins()
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
