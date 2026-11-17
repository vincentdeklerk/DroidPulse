package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.example.data.db.AppDatabase
import com.example.data.prefs.CredentialsStore
import com.example.data.repository.TelemetryRepository
import com.example.plugins.calendar.CalendarSyncHelper
import com.example.plugins.calls.CallCaptureManager
import com.example.plugins.export.TelemetryExporter
import com.example.plugins.gps.GpsTrackerEngine
import com.example.plugins.network.NetworkMonitorEngine
import com.example.plugins.orchestrator.TelemetryOrchestrator
import com.example.plugins.recipes.PluginRecipeImporter
import com.example.plugins.watcher.CloudPushAdapters
import com.example.plugins.watcher.FolderWatcherEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DroidPulseApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set
    lateinit var credentialsStore: CredentialsStore
        private set
    lateinit var repository: TelemetryRepository
        private set
    lateinit var calendarSyncHelper: CalendarSyncHelper
        private set
    lateinit var callCaptureManager: CallCaptureManager
        private set
    lateinit var networkMonitorEngine: NetworkMonitorEngine
        private set
    lateinit var gpsTrackerEngine: GpsTrackerEngine
        private set
    lateinit var cloudPushAdapters: CloudPushAdapters
        private set
    lateinit var folderWatcherEngine: FolderWatcherEngine
        private set
    lateinit var recipeImporter: PluginRecipeImporter
        private set
    lateinit var exporter: TelemetryExporter
        private set
    lateinit var orchestrator: TelemetryOrchestrator
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        database = AppDatabase.getInstance(this)
        credentialsStore = CredentialsStore(this)
        repository = TelemetryRepository(database)
        calendarSyncHelper = CalendarSyncHelper(this)
        callCaptureManager = CallCaptureManager(this, repository, calendarSyncHelper)
        networkMonitorEngine = NetworkMonitorEngine(this, repository)
        gpsTrackerEngine = GpsTrackerEngine(this, repository)
        cloudPushAdapters = CloudPushAdapters(this, credentialsStore)
        folderWatcherEngine = FolderWatcherEngine(this, repository, cloudPushAdapters)
        recipeImporter = PluginRecipeImporter(repository)
        exporter = TelemetryExporter(this, repository)
        orchestrator = TelemetryOrchestrator(this, repository, networkMonitorEngine, gpsTrackerEngine)

        appScope.launch {
            repository.ensureSeeded()
            orchestrator.syncAllPlugins()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val fgsChannel = NotificationChannel(
                CHANNEL_TELEMETRY_FGS,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_channel_description)
                setShowBadge(false)
            }
            nm.createNotificationChannel(fgsChannel)
        }
    }

    companion object {
        const val CHANNEL_TELEMETRY_FGS = "droidpulse_telemetry_fgs"
    }
}
