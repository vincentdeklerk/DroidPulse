package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DroidPulseApp
import com.example.data.db.CallLogEntity
import com.example.data.db.LocationPingEntity
import com.example.data.db.NetworkLogEntity
import com.example.data.db.PluginConfigEntity
import com.example.data.db.PluginRecipeEntity
import com.example.data.db.PushEventEntity
import com.example.data.db.WatchedFileEntity
import com.example.data.model.CalendarInfo
import com.example.data.model.CallDirection
import com.example.data.model.CloudCredentials
import com.example.data.model.CloudPushTarget
import com.example.data.model.ExecutionMode
import com.example.data.model.NetworkUsageReport
import com.example.data.model.PluginRuntimeState
import com.example.data.model.PluginType
import com.example.data.model.SystemHealthSnapshot
import com.example.data.repository.TelemetryRepository
import com.example.plugins.calls.VoipCallNotificationListener
import com.example.plugins.orchestrator.TelemetryForegroundService
import com.example.plugins.watcher.FolderWatcherEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class UiEvent {
    data class Snackbar(val message: String) : UiEvent()
    data class ShareFile(val uri: Uri, val mimeType: String, val title: String) : UiEvent()
}

data class ConsoleState(
    val configs: List<PluginConfigEntity> = emptyList(),
    val callCount: Int = 0,
    val networkCount: Int = 0,
    val locationCount: Int = 0,
    val watchedFilesCount: Int = 0,
    val pendingPushCount: Int = 0,
    val recipes: List<PluginRecipeEntity> = emptyList(),
    val health: SystemHealthSnapshot = SystemHealthSnapshot(),
    val calendars: List<CalendarInfo> = emptyList(),
    val credentials: CloudCredentials = CloudCredentials(),
    val darkMode: Boolean = true,
    val useSystemTheme: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DroidPulseApp
    private val repo = app.repository

    private val _health = MutableStateFlow(SystemHealthSnapshot())
    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val _callSearchQuery = MutableStateFlow("")
    val callSearchQuery: StateFlow<String> = _callSearchQuery.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val consoleState: StateFlow<ConsoleState> = combine(
        repo.pluginConfigs,
        repo.callLogsCount,
        repo.networkLogsCount,
        repo.locationPingsCount,
        repo.watchedFilesCount,
        repo.pendingPushCount,
        repo.recipes,
        _health,
        _calendars,
        app.credentialsStore.credentialsFlow,
        app.credentialsStore.darkThemeFlow
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val configs = arr[0] as List<PluginConfigEntity>
        val callCnt = arr[1] as Int
        val netCnt = arr[2] as Int
        val locCnt = arr[3] as Int
        val fileCnt = arr[4] as Int
        val pushPending = arr[5] as Int
        @Suppress("UNCHECKED_CAST")
        val recipes = arr[6] as List<PluginRecipeEntity>
        val health = arr[7] as SystemHealthSnapshot
        @Suppress("UNCHECKED_CAST")
        val cals = arr[8] as List<CalendarInfo>
        val creds = arr[9] as CloudCredentials
        @Suppress("UNCHECKED_CAST")
        val themePair = arr[10] as Pair<Boolean, Boolean>

        ConsoleState(
            configs = configs,
            callCount = callCnt,
            networkCount = netCnt,
            locationCount = locCnt,
            watchedFilesCount = fileCnt,
            pendingPushCount = pushPending,
            recipes = recipes,
            health = health,
            calendars = cals,
            credentials = creds,
            darkMode = themePair.first,
            useSystemTheme = themePair.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConsoleState())

    val filteredCallLogs: StateFlow<List<CallLogEntity>> = _callSearchQuery
        .flatMapLatest { repo.searchCallLogs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentNetworkLogs: StateFlow<List<NetworkLogEntity>> = repo.recentNetworkLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkUsageReport: StateFlow<NetworkUsageReport> = repo.networkUsageReport
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            NetworkUsageReport(0, 0, 0, 0, 0, 0, 0, emptyList())
        )

    val recentLocationPings: StateFlow<List<LocationPingEntity>> = repo.recentLocationPings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentWatchedFiles: StateFlow<List<WatchedFileEntity>> = repo.recentWatchedFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPushEvents: StateFlow<List<PushEventEntity>> = repo.recentPushEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo.ensureSeeded()
            refreshSystemHealth()
            refreshCalendars()
        }
    }

    fun refreshSystemHealth() {
        val pm = app.getSystemService(PowerManager::class.java)
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
        } else true

        val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                app, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        _health.value = SystemHealthSnapshot(
            isBatteryOptimizationIgnored = batteryIgnored,
            isNotificationListenerEnabled = VoipCallNotificationListener.isNotificationServiceEnabled(app),
            hasCalendarPermission = app.calendarSyncHelper.hasCalendarPermissions(),
            hasCallPermissions = app.callCaptureManager.hasCallPermissions(),
            hasLocationPermission = app.gpsTrackerEngine.hasLocationPermission(),
            hasBackgroundLocationPermission = app.gpsTrackerEngine.hasBackgroundLocationPermission(),
            hasNotificationPermission = notifPermission,
            activeServiceRunning = TelemetryForegroundService.isRunning
        )
        refreshCalendars()
    }

    fun refreshCalendars() {
        viewModelScope.launch(Dispatchers.IO) {
            _calendars.value = app.calendarSyncHelper.queryAvailableCalendars()
        }
    }

    fun computeRuntimeState(config: PluginConfigEntity, health: SystemHealthSnapshot): PluginRuntimeState {
        if (!config.enabled) return PluginRuntimeState.PAUSED
        val type = PluginType.fromId(config.pluginId)
        val needsSetup = when (type) {
            PluginType.CALL_LOGGER -> {
                val calId = TelemetryRepository.parseParams(config.paramsJson).optLong("calendarId", -1L)
                !health.hasCalendarPermission || calId <= 0L
            }
            PluginType.NETWORK_MONITOR -> false
            PluginType.GPS_LOGGER -> !health.hasLocationPermission
            PluginType.FOLDER_WATCHER -> TelemetryRepository.getFolderUris(config.paramsJson).isEmpty()
        }
        if (needsSetup) return PluginRuntimeState.NEEDS_SETUP
        return if (config.executionMode == ExecutionMode.FOREGROUND_SERVICE.id && health.activeServiceRunning) {
            PluginRuntimeState.RUNNING
        } else {
            PluginRuntimeState.ARMED
        }
    }

    fun toggleDarkMode() {
        val next = !consoleState.value.darkMode
        viewModelScope.launch {
            app.credentialsStore.setThemePreference(darkMode = next, useSystem = false)
        }
    }

    fun setCallSearchQuery(query: String) {
        _callSearchQuery.value = query
    }

    fun togglePlugin(pluginType: PluginType, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repo.getConfig(pluginType) ?: return@launch
            val updated = current.copy(
                enabled = enabled,
                lastStatusNote = if (enabled) "Armed (${ExecutionMode.fromId(current.executionMode).shortLabel})" else "Paused"
            )
            repo.upsertConfig(updated)
            app.orchestrator.syncAllPlugins()
            refreshSystemHealth()
        }
    }

    fun updateExecutionMode(pluginType: PluginType, mode: ExecutionMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repo.getConfig(pluginType) ?: return@launch
            repo.upsertConfig(
                current.copy(
                    executionMode = mode.id,
                    lastStatusNote = if (current.enabled) "Armed (${mode.shortLabel})" else current.lastStatusNote
                )
            )
            app.orchestrator.syncAllPlugins()
            refreshSystemHealth()
        }
    }

    fun updateInterval(pluginType: PluginType, seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repo.getConfig(pluginType) ?: return@launch
            repo.upsertConfig(current.copy(intervalSeconds = seconds))
            app.orchestrator.syncAllPlugins()
        }
    }

    fun updatePluginParams(pluginType: PluginType, mutator: (JSONObject) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repo.getConfig(pluginType) ?: return@launch
            val obj = TelemetryRepository.parseParams(current.paramsJson)
            mutator(obj)
            repo.upsertConfig(current.copy(paramsJson = obj.toString()))
            app.orchestrator.syncAllPlugins()
        }
    }

    fun selectCalendar(calendar: CalendarInfo) {
        updatePluginParams(PluginType.CALL_LOGGER) { obj ->
            obj.put("calendarId", calendar.id)
            obj.put("calendarName", "${calendar.displayName} (${calendar.accountName})")
        }
        emitMessage("Selected calendar: ${calendar.displayName}")
    }

    fun importRecentCalls() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = app.callCaptureManager.importRecentDeviceCalls(limit = 10)
            emitMessage(
                if (count > 0) "Logged $count recent phone call(s) to Calendar & SQLite"
                else "No new calls imported (grant Call Log permission or already synced)"
            )
        }
    }

    fun testCalendarWrite() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = app.callCaptureManager.recordCall(
                sourceApp = "DroidPulse",
                packageName = app.packageName,
                contactOrLabel = "Diagnostic Test Call",
                phoneNumber = "+0000000000",
                direction = CallDirection.OUTGOING,
                startedAt = System.currentTimeMillis(),
                durationSeconds = 45L,
                notes = "CalendarContract verification entry",
                forceCalendarWrite = true
            )
            emitMessage(
                if (ok) "Test call event logged to Calendar & SQLite"
                else "Select a calendar and grant Calendar permissions first"
            )
        }
    }

    fun captureNetworkSampleNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val sample = app.networkMonitorEngine.captureSample("MANUAL_SAMPLE")
            emitMessage("Captured: ${sample.transportType} • ${sample.networkName}")
        }
    }

    fun captureGpsPingNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            try {
                val ping = app.gpsTrackerEngine.captureSinglePing("MANUAL_PING")
                emitMessage(
                    if (ping != null) "GPS Ping: ${"%.4f".format(ping.latitude)}, ${"%.4f".format(ping.longitude)}"
                    else "Location unavailable (enable GPS & grant location permission)"
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun addWatchedFolder(uri: Uri) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            app.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val label = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { "Watched Folder" } ?: "Watched Folder"
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = repo.getConfig(PluginType.FOLDER_WATCHER) ?: return@launch
            val existing = TelemetryRepository.getFolderUris(cfg.paramsJson).toMutableList()
            val uriStr = uri.toString()
            if (existing.none { it.first == uriStr }) {
                existing.add(uriStr to label)
                val updatedParams = TelemetryRepository.withUpdatedFolderUris(cfg.paramsJson, existing)
                repo.upsertConfig(cfg.copy(paramsJson = updatedParams))
                // Seed baseline files silently so existing files aren't mass-uploaded on add
                val baseline = app.folderWatcherEngine.scanConfiguredFolders(seedExistingWithoutPush = true)
                emitMessage("Watching '$label' ($baseline existing file(s) baselined)")
            }
        }
    }

    fun removeWatchedFolder(uriStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = repo.getConfig(PluginType.FOLDER_WATCHER) ?: return@launch
            val updated = TelemetryRepository.getFolderUris(cfg.paramsJson).filterNot { it.first == uriStr }
            repo.upsertConfig(cfg.copy(paramsJson = TelemetryRepository.withUpdatedFolderUris(cfg.paramsJson, updated)))
        }
    }

    fun setPushTarget(target: CloudPushTarget) {
        updatePluginParams(PluginType.FOLDER_WATCHER) { it.put("pushTarget", target.id) }
    }

    fun saveCloudCredentials(creds: CloudCredentials) {
        viewModelScope.launch(Dispatchers.IO) {
            app.credentialsStore.saveCredentials(creds)
            emitMessage("Encrypted credentials saved")
        }
    }

    fun testCloudConnection(target: CloudPushTarget) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            try {
                val res = app.cloudPushAdapters.testConnection(target)
                emitMessage(res.getOrElse { "Failed: ${it.message}" })
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun launchDriveOAuth(clientId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (clientId.isBlank()) {
                emitMessage("Enter your Google OAuth Client ID first or paste an Access Token")
                return@launch
            }
            val current = app.credentialsStore.getCredentials()
            app.credentialsStore.saveCredentials(current.copy(driveClientId = clientId.trim()))
            app.cloudPushAdapters.launchDriveOAuthPkce(clientId)
        }
    }

    fun handleOAuthRedirect(uri: Uri?) {
        uri ?: return
        if (uri.scheme != "com.aistudio.droidpulse.xk7mqt" || uri.host != "oauth2redirect") return
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            emitMessage("Drive OAuth cancelled: $error")
            return
        }
        if (code.isNullOrBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            try {
                val res = app.cloudPushAdapters.exchangeDriveAuthCode(code)
                emitMessage(res.getOrElse { "OAuth failed: ${it.message}" })
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun scanFoldersNow(seedBaselineOnly: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            try {
                val count = app.folderWatcherEngine.scanConfiguredFolders(seedBaselineOnly)
                emitMessage(
                    if (seedBaselineOnly) "Baselined $count file(s) without pushing"
                    else if (count > 0) "Detected $count new file(s) & queued push"
                    else "No new files found"
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun retryAndFlushPushQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            try {
                repo.retryFailedPushes()
                val (ok, fail) = app.folderWatcherEngine.processPendingQueue()
                if (ok == 0 && fail == 0) {
                    FolderWatcherEngine.enqueuePushWorker(app)
                    emitMessage("Queue checked — no pending events")
                } else {
                    emitMessage("Push flush: $ok succeeded, $fail failed")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importRecipeFromUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            try {
                val res = app.recipeImporter.importFromUrl(url)
                emitMessage(
                    res.fold(
                        onSuccess = { "Installed recipe: ${it.name}" },
                        onFailure = { "Import error: ${it.message}" }
                    )
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importRecipeFromJson(rawJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = app.recipeImporter.parseAndSaveRecipe(rawJson)
            emitMessage(
                res.fold(
                    onSuccess = { "Installed recipe: ${it.name}" },
                    onFailure = { "Invalid recipe JSON: ${it.message}" }
                )
            )
        }
    }

    fun toggleRecipe(recipeId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setRecipeEnabled(recipeId, enabled)
        }
    }

    fun deleteRecipe(recipeId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteRecipe(recipeId)
            emitMessage("Recipe removed")
        }
    }

    fun exportCallsCsv() = launchExport("Export Calls CSV", "text/csv") { app.exporter.exportCallsCsv() }
    fun exportNetworkCsv() = launchExport("Export Network CSV", "text/csv") { app.exporter.exportNetworkCsv() }
    fun exportGpsCsv() = launchExport("Export GPS CSV", "text/csv") { app.exporter.exportGpsCsv() }
    fun exportGpsGpx() = launchExport("Export GPS GPX", "application/gpx+xml") { app.exporter.exportGpsGpx() }
    fun exportFilesCsv() = launchExport("Export Watched Files CSV", "text/csv") { app.exporter.exportWatchedFilesCsv() }

    private fun launchExport(title: String, mime: String, generator: suspend () -> Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { generator() }
                .onSuccess { uri -> _events.tryEmit(UiEvent.ShareFile(uri, mime, title)) }
                .onFailure { emitMessage("Export failed: ${it.message}") }
        }
    }

    fun clearLogs(pluginType: PluginType) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.clearLogsForPlugin(pluginType)
            emitMessage("Cleared ${pluginType.title} logs")
        }
    }

    private fun emitMessage(msg: String) {
        _events.tryEmit(UiEvent.Snackbar(msg))
    }
}
