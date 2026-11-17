package com.example.plugins.orchestrator

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.DroidPulseApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.ExecutionMode
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelemetryForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var networkLoopJob: Job? = null
    private var watcherLoopJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_SYNC_STATE = "com.example.action.SYNC_FGS_STATE"
        const val ACTION_STOP = "com.example.action.STOP_FGS"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val app = applicationContext as? DroidPulseApp
        val hasLocPerm = app?.gpsTrackerEngine?.hasLocationPermission() == true
        startForegroundSafely("Initializing telemetry plugins...", includeLocationType = hasLocPerm)

        serviceScope.launch {
            reconfigureLoops()
        }
        return START_STICKY
    }

    private fun startForegroundSafely(summary: String, includeLocationType: Boolean) {
        val notification = buildNotification(summary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (includeLocationType) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            runCatching {
                startForeground(NOTIFICATION_ID, notification, type)
            }.onFailure {
                // Fallback to dataSync only if location FGS permission isn't currently satisfied
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun reconfigureLoops() {
        val app = applicationContext as? DroidPulseApp ?: return
        val configs = app.repository.getAllConfigs()

        val fgsPlugins = configs.filter {
            it.enabled && it.executionMode == ExecutionMode.FOREGROUND_SERVICE.id
        }

        if (fgsPlugins.isEmpty()) {
            stopSelf()
            return
        }

        val activeLabels = mutableListOf<String>()

        // 1. Network Monitor in FGS mode
        val netCfg = fgsPlugins.firstOrNull { it.pluginId == PluginType.NETWORK_MONITOR.id }
        networkLoopJob?.cancel()
        if (netCfg != null) {
            activeLabels.add("Network")
            app.networkMonitorEngine.registerTransitionCallbacks(serviceScope)
            val intervalMs = (netCfg.intervalSeconds.coerceAtLeast(30)) * 1000L
            networkLoopJob = serviceScope.launch {
                while (isActive) {
                    app.networkMonitorEngine.captureSample("FGS_INTERVAL")
                    delay(intervalMs)
                }
            }
        } else {
            app.networkMonitorEngine.unregisterCallbacks()
        }

        // 2. GPS Logger in FGS mode
        val gpsCfg = fgsPlugins.firstOrNull { it.pluginId == PluginType.GPS_LOGGER.id }
        if (gpsCfg != null && app.gpsTrackerEngine.hasLocationPermission()) {
            val params = TelemetryRepository.parseParams(gpsCfg.paramsJson)
            val passiveOnly = params.optBoolean("passiveOnly", true)
            activeLabels.add(if (passiveOnly) "GPS(Passive)" else "GPS(${gpsCfg.intervalSeconds}s)")
            app.gpsTrackerEngine.startTracking(
                scope = serviceScope,
                intervalSeconds = gpsCfg.intervalSeconds,
                passiveOnly = passiveOnly
            )
        } else {
            app.gpsTrackerEngine.stopTracking()
        }

        // 3. Folder Watcher in FGS mode
        val watcherCfg = fgsPlugins.firstOrNull { it.pluginId == PluginType.FOLDER_WATCHER.id }
        watcherLoopJob?.cancel()
        if (watcherCfg != null) {
            activeLabels.add("Watcher")
            val intervalMs = (watcherCfg.intervalSeconds.coerceAtLeast(30)) * 1000L
            watcherLoopJob = serviceScope.launch {
                while (isActive) {
                    app.folderWatcherEngine.scanConfiguredFolders()
                    delay(intervalMs)
                }
            }
        }

        // 4. Call Logger in FGS mode (keeps process priority warm for CallStateReceiver + VoipListener)
        if (fgsPlugins.any { it.pluginId == PluginType.CALL_LOGGER.id }) {
            activeLabels.add("Calls")
        }

        val summary = "Armed: ${activeLabels.joinToString(" • ")}"
        val includeLoc = gpsCfg != null && app.gpsTrackerEngine.hasLocationPermission()
        startForegroundSafely(summary, includeLocationType = includeLoc)
    }

    private fun buildNotification(summary: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DroidPulseApp.CHANNEL_TELEMETRY_FGS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DroidPulse Background Engine")
            .setContentText(summary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        networkLoopJob?.cancel()
        watcherLoopJob?.cancel()
        val app = applicationContext as? DroidPulseApp
        app?.networkMonitorEngine?.unregisterCallbacks()
        app?.gpsTrackerEngine?.stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }
}
