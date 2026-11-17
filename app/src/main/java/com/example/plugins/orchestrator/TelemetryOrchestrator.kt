package com.example.plugins.orchestrator

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.ExecutionMode
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import com.example.plugins.gps.GpsTrackerEngine
import com.example.plugins.network.NetworkMonitorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class TelemetryOrchestrator(
    private val context: Context,
    private val repository: TelemetryRepository,
    private val networkMonitorEngine: NetworkMonitorEngine,
    private val gpsTrackerEngine: GpsTrackerEngine
) {
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun syncAllPlugins() {
        val configs = repository.getAllConfigs()
        val workManager = runCatching {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(
                    context,
                    androidx.work.Configuration.Builder().build()
                )
            }
            WorkManager.getInstance(context)
        }.getOrNull()

        var needsForegroundService = false

        for (cfg in configs) {
            val type = PluginType.fromId(cfg.pluginId)
            val mode = ExecutionMode.fromId(cfg.executionMode)
            val workName = "droidpulse_work_${type.id}"

            if (!cfg.enabled) {
                workManager?.cancelUniqueWork(workName)
                if (type == PluginType.GPS_LOGGER) {
                    gpsTrackerEngine.unregisterPassivePendingIntent()
                }
                continue
            }

            when (mode) {
                ExecutionMode.FOREGROUND_SERVICE -> {
                    needsForegroundService = true
                    workManager?.cancelUniqueWork(workName)
                }
                ExecutionMode.WORK_MANAGER -> {
                    val minutes = (cfg.intervalSeconds / 60L).coerceAtLeast(15L)
                    val req = PeriodicWorkRequestBuilder<TelemetryIntervalWorker>(minutes, TimeUnit.MINUTES)
                        .setInputData(
                            Data.Builder()
                                .putString(TelemetryIntervalWorker.KEY_PLUGIN_ID, type.id)
                                .build()
                        )
                        .build()
                    workManager?.enqueueUniquePeriodicWork(
                        workName,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        req
                    )
                    if (type == PluginType.GPS_LOGGER) {
                        gpsTrackerEngine.registerPassivePendingIntent()
                    }
                    if (type == PluginType.NETWORK_MONITOR) {
                        networkMonitorEngine.registerTransitionCallbacks(callbackScope)
                    }
                }
                ExecutionMode.PASSIVE_ONLY -> {
                    workManager?.cancelUniqueWork(workName)
                    if (type == PluginType.GPS_LOGGER) {
                        gpsTrackerEngine.registerPassivePendingIntent()
                    }
                    if (type == PluginType.NETWORK_MONITOR) {
                        networkMonitorEngine.registerTransitionCallbacks(callbackScope)
                    }
                }
            }
        }

        if (needsForegroundService) {
            val intent = Intent(context, TelemetryForegroundService::class.java).apply {
                action = TelemetryForegroundService.ACTION_SYNC_STATE
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        } else if (TelemetryForegroundService.isRunning) {
            val stopIntent = Intent(context, TelemetryForegroundService::class.java).apply {
                action = TelemetryForegroundService.ACTION_STOP
            }
            runCatching { context.startService(stopIntent) }
        }
    }
}
