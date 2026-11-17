package com.example.plugins.orchestrator

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.DroidPulseApp
import com.example.data.model.ExecutionMode
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository

class TelemetryIntervalWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DroidPulseApp ?: return Result.failure()
        val pluginId = inputData.getString(KEY_PLUGIN_ID) ?: return Result.failure()
        val pluginType = PluginType.fromId(pluginId)
        val cfg = app.repository.getConfig(pluginType) ?: return Result.success()
        if (!cfg.enabled) return Result.success()

        return when (pluginType) {
            PluginType.NETWORK_MONITOR -> {
                app.networkMonitorEngine.captureSample("WORK_INTERVAL")
                Result.success()
            }
            PluginType.GPS_LOGGER -> {
                val p = TelemetryRepository.parseParams(cfg.paramsJson)
                val passiveOnly = p.optBoolean("passiveOnly", true)
                if (!passiveOnly) {
                    app.gpsTrackerEngine.captureSinglePing("WORK_PING")
                }
                Result.success()
            }
            PluginType.FOLDER_WATCHER -> {
                app.folderWatcherEngine.scanConfiguredFolders()
                Result.success()
            }
            PluginType.CALL_LOGGER -> {
                if (cfg.executionMode == ExecutionMode.WORK_MANAGER.id) {
                    app.callCaptureManager.importRecentDeviceCalls(limit = 5)
                }
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_PLUGIN_ID = "plugin_id"
    }
}

class PushQueueWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DroidPulseApp ?: return Result.failure()
        val (_, failed) = app.folderWatcherEngine.processPendingQueue()
        return if (failed > 0 && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
