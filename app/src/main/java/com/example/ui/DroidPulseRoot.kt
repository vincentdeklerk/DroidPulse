package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PluginType
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.LogsAndExportsScreen
import com.example.ui.screens.PluginConfigSheet
import com.example.ui.screens.RecipeImporterSheet
import com.example.ui.theme.DroidPulseTheme

@Composable
fun DroidPulseRoot(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val state by viewModel.consoleState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val callSearch by viewModel.callSearchQuery.collectAsStateWithLifecycle()
    val callLogs by viewModel.filteredCallLogs.collectAsStateWithLifecycle()
    val networkLogs by viewModel.recentNetworkLogs.collectAsStateWithLifecycle()
    val networkReport by viewModel.networkUsageReport.collectAsStateWithLifecycle()
    val locationPings by viewModel.recentLocationPings.collectAsStateWithLifecycle()
    val watchedFiles by viewModel.recentWatchedFiles.collectAsStateWithLifecycle()
    val pushEvents by viewModel.recentPushEvents.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedPluginForConfig by remember { mutableStateOf<PluginType?>(null) }
    var showRecipeImporter by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.ShareFile -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, event.title))
                }
            }
        }
    }

    val requestBatteryExemption = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    DroidPulseTheme(darkTheme = state.darkMode, useSystemTheme = state.useSystemTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = { Icon(Icons.Filled.Tune, contentDescription = "Plugins Console") },
                                label = { Text("Console") },
                                modifier = Modifier.testTag("nav_console")
                            )
                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                icon = { Icon(Icons.Filled.Storage, contentDescription = "Logs & Exports") },
                                label = { Text("Logs & Export") },
                                modifier = Modifier.testTag("nav_logs")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationRailItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = { Icon(Icons.Filled.Tune, contentDescription = "Plugins Console") },
                                label = { Text("Console") },
                                modifier = Modifier.testTag("rail_console")
                            )
                            NavigationRailItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                icon = { Icon(Icons.Filled.Storage, contentDescription = "Logs & Exports") },
                                label = { Text("Logs") },
                                modifier = Modifier.testTag("rail_logs")
                            )
                        }
                    }

                    val contentMod = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)

                    if (currentTab == 0) {
                        ConsoleScreen(
                            state = state,
                            computeRuntimeState = { cfg -> viewModel.computeRuntimeState(cfg, state.health) },
                            onTogglePlugin = { type, enabled -> viewModel.togglePlugin(type, enabled) },
                            onOpenPluginConfig = { type -> selectedPluginForConfig = type },
                            onRequestBatteryExemption = requestBatteryExemption,
                            onToggleTheme = { viewModel.toggleDarkMode() },
                            onOpenRecipeImporter = { showRecipeImporter = true },
                            onToggleRecipe = { id, en -> viewModel.toggleRecipe(id, en) },
                            onDeleteRecipe = { id -> viewModel.deleteRecipe(id) },
                            modifier = contentMod
                        )
                    } else {
                        LogsAndExportsScreen(
                            callSearchQuery = callSearch,
                            callLogs = callLogs,
                            networkLogs = networkLogs,
                            networkReport = networkReport,
                            locationPings = locationPings,
                            watchedFiles = watchedFiles,
                            pushEvents = pushEvents,
                            onCallSearchChange = { viewModel.setCallSearchQuery(it) },
                            onExportCallsCsv = { viewModel.exportCallsCsv() },
                            onExportNetworkCsv = { viewModel.exportNetworkCsv() },
                            onExportGpsCsv = { viewModel.exportGpsCsv() },
                            onExportGpsGpx = { viewModel.exportGpsGpx() },
                            onExportFilesCsv = { viewModel.exportFilesCsv() },
                            onRetryPushQueue = { viewModel.retryAndFlushPushQueue() },
                            onClearLogs = { viewModel.clearLogs(it) },
                            modifier = contentMod
                        )
                    }
                }
            }

            selectedPluginForConfig?.let { pluginType ->
                PluginConfigSheet(
                    pluginType = pluginType,
                    state = state,
                    isBusy = isBusy,
                    onDismiss = { selectedPluginForConfig = null },
                    onRefreshHealth = { viewModel.refreshSystemHealth() },
                    onTogglePlugin = { en -> viewModel.togglePlugin(pluginType, en) },
                    onSelectMode = { mode -> viewModel.updateExecutionMode(pluginType, mode) },
                    onUpdateInterval = { sec -> viewModel.updateInterval(pluginType, sec) },
                    onUpdateParams = { mut -> viewModel.updatePluginParams(pluginType, mut) },
                    onSelectCalendar = { cal -> viewModel.selectCalendar(cal) },
                    onImportRecentCalls = { viewModel.importRecentCalls() },
                    onTestCalendarWrite = { viewModel.testCalendarWrite() },
                    onSampleNetworkNow = { viewModel.captureNetworkSampleNow() },
                    onCaptureGpsNow = { viewModel.captureGpsPingNow() },
                    onAddWatchedFolder = { uri -> viewModel.addWatchedFolder(uri) },
                    onRemoveWatchedFolder = { uri -> viewModel.removeWatchedFolder(uri) },
                    onSelectPushTarget = { target -> viewModel.setPushTarget(target) },
                    onSaveCredentials = { creds -> viewModel.saveCloudCredentials(creds) },
                    onTestCloudConnection = { target -> viewModel.testCloudConnection(target) },
                    onLaunchDriveOAuth = { cid -> viewModel.launchDriveOAuth(cid) },
                    onScanFoldersNow = { baseline -> viewModel.scanFoldersNow(baseline) }
                )
            }

            if (showRecipeImporter) {
                RecipeImporterSheet(
                    isBusy = isBusy,
                    onDismiss = { showRecipeImporter = false },
                    onImportUrl = { url -> viewModel.importRecipeFromUrl(url) },
                    onImportJson = { json -> viewModel.importRecipeFromJson(json) }
                )
            }
        }
    }
}
