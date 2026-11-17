package com.example.plugins.gps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.example.DroidPulseApp
import com.example.data.model.PluginType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PassiveLocationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PASSIVE_LOCATION = "com.example.action.PASSIVE_LOCATION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PASSIVE_LOCATION) return
        val location: Location? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED)
        }
        location ?: return

        val app = context.applicationContext as? DroidPulseApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cfg = app.repository.getConfig(PluginType.GPS_LOGGER)
                if (cfg?.enabled == true) {
                    app.gpsTrackerEngine.saveLocation(location, "PASSIVE_INTENT")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
