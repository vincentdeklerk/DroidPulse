package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.PluginConfigEntity
import com.example.data.model.CalendarInfo
import com.example.data.model.CloudCredentials
import com.example.data.model.CloudPushTarget
import com.example.data.model.ExecutionMode
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import com.example.ui.ConsoleState
import com.example.ui.components.CloudTargetSettings
import com.example.ui.components.PermissionCheckRow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginConfigSheet(
    pluginType: PluginType,
    state: ConsoleState,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRefreshHealth: () -> Unit,
    onTogglePlugin: (Boolean) -> Unit,
    onSelectMode: (ExecutionMode) -> Unit,
    onUpdateInterval: (Int) -> Unit,
    onUpdateParams: ((org.json.JSONObject) -> Unit) -> Unit,
    onSelectCalendar: (CalendarInfo) -> Unit,
    onImportRecentCalls: () -> Unit,
    onTestCalendarWrite: () -> Unit,
    onSampleNetworkNow: () -> Unit,
    onCaptureGpsNow: () -> Unit,
    onAddWatchedFolder: (Uri) -> Unit,
    onRemoveWatchedFolder: (String) -> Unit,
    onSelectPushTarget: (CloudPushTarget) -> Unit,
    onSaveCredentials: (CloudCredentials) -> Unit,
    onTestCloudConnection: (CloudPushTarget) -> Unit,
    onLaunchDriveOAuth: (String) -> Unit,
    onScanFoldersNow: (Boolean) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = state.configs.firstOrNull { it.pluginId == pluginType.id }
        ?: PluginConfigEntity(pluginId = pluginType.id)
    val params = TelemetryRepository.parseParams(config.paramsJson)
    val currentMode = ExecutionMode.fromId(config.executionMode)

    val multiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onRefreshHealth() }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshHealth() }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onAddWatchedFolder(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluginType.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = pluginType.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onTogglePlugin,
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close configuration")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Execution Mode Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "BACKGROUND EXECUTION MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExecutionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentMode == mode,
                            onClick = { onSelectMode(mode) },
                            label = { Text(mode.shortLabel, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mode_chip_${mode.id}")
                        )
                    }
                }
                Text(
                    text = currentMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Notification permission for FGS on Android 13+
            if (currentMode == ExecutionMode.FOREGROUND_SERVICE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !state.health.hasNotificationPermission
            ) {
                PermissionCheckRow(
                    title = "Foreground Service Notification",
                    detail = "Required on Android 13+ to display active telemetry indicator",
                    granted = false,
                    onGrantClick = {
                        multiPermLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Plugin-Specific Permissions & Configuration
            when (pluginType) {
                PluginType.CALL_LOGGER -> {
                    Text(
                        text = "PERMISSIONS GATE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PermissionCheckRow(
                        title = "Calendar Read & Write",
                        detail = "Writes offline call events to your chosen device calendar",
                        granted = state.health.hasCalendarPermission,
                        onGrantClick = {
                            multiPermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        }
                    )
                    PermissionCheckRow(
                        title = "Phone State & Call Log",
                        detail = "Detects cellular calls and imports duration/number",
                        granted = state.health.hasCallPermissions,
                        onGrantClick = {
                            multiPermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CALL_LOG
                                )
                            )
                        }
                    )
                    PermissionCheckRow(
                        title = "VoIP Notification Listener",
                        detail = "Captures WhatsApp, Signal & Telegram call notifications offline",
                        granted = state.health.isNotificationListenerEnabled,
                        onGrantClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "TARGET OFFLINE / LOCAL CALENDAR",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onRefreshHealth) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh calendars")
                        }
                    }

                    val selectedCalId = params.optLong("calendarId", -1L)
                    if (state.calendars.isEmpty()) {
                        Text(
                            text = if (state.health.hasCalendarPermission)
                                "No writable calendars found on device. Add a local or Google calendar in your Calendar app."
                            else
                                "Grant Calendar permission above to list local & Google-synced calendars.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.calendars.forEach { cal ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (cal.id == selectedCalId) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectCalendar(cal) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        RadioButton(
                                            selected = cal.id == selectedCalId,
                                            onClick = { onSelectCalendar(cal) }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cal.displayName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "${cal.accountName} (${cal.accountType})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "CALL SOURCES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ToggleParamRow("Cellular Phone Calls", params.optBoolean("trackPhone", true)) { v ->
                        onUpdateParams { it.put("trackPhone", v) }
                    }
                    ToggleParamRow("WhatsApp / WA Business", params.optBoolean("trackWhatsApp", true)) { v ->
                        onUpdateParams { it.put("trackWhatsApp", v) }
                    }
                    ToggleParamRow("Signal Private Messenger", params.optBoolean("trackSignal", true)) { v ->
                        onUpdateParams { it.put("trackSignal", v) }
                    }
                    ToggleParamRow("Telegram Calls", params.optBoolean("trackTelegram", true)) { v ->
                        onUpdateParams { it.put("trackTelegram", v) }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onImportRecentCalls,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("import_recent_calls_button")
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Phone Log")
                        }
                        Button(
                            onClick = onTestCalendarWrite,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("test_calendar_write_button")
                        ) {
                            Text("Test Cal Write")
                        }
                    }
                }

                PluginType.NETWORK_MONITOR -> {
                    val intervalMin = (config.intervalSeconds / 60f).coerceIn(1f, 60f)
                    Text(
                        text = "SAMPLING INTERVAL: ${intervalMin.roundToInt()} MIN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Slider(
                        value = intervalMin,
                        onValueChange = { onUpdateInterval((it.roundToInt().coerceAtLeast(1)) * 60) },
                        valueRange = 1f..60f,
                        steps = 58
                    )
                    ToggleParamRow(
                        label = "Log instant Wi-Fi/Mobile transitions",
                        checked = params.optBoolean("logTransitions", true)
                    ) { v -> onUpdateParams { it.put("logTransitions", v) } }

                    PermissionCheckRow(
                        title = "Wi-Fi SSID Name Visibility (Optional)",
                        detail = "Android requires Location permission to read exact Wi-Fi SSID names",
                        granted = state.health.hasLocationPermission,
                        onGrantClick = {
                            multiPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                    )

                    Button(
                        onClick = onSampleNetworkNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("sample_network_now_button")
                    ) {
                        Text("Sample Network Connection Now")
                    }
                }

                PluginType.GPS_LOGGER -> {
                    Text(
                        text = "LOCATION PERMISSIONS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PermissionCheckRow(
                        title = "Foreground & Passive Location",
                        detail = "Captures Play Services / Maps / banking GPS fixes & active pings",
                        granted = state.health.hasLocationPermission,
                        onGrantClick = {
                            multiPermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && state.health.hasLocationPermission) {
                        PermissionCheckRow(
                            title = "Background Location (Always Allow)",
                            detail = "Required for continuous background GPS history when screen is off",
                            granted = state.health.hasBackgroundLocationPermission,
                            onGrantClick = {
                                bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    val passiveOnly = params.optBoolean("passiveOnly", true)
                    ToggleParamRow(
                        label = "Passive Piggyback Only (Near-Zero Battery)",
                        checked = passiveOnly
                    ) { v -> onUpdateParams { it.put("passiveOnly", v) } }
                    Text(
                        text = if (passiveOnly)
                            "Listens passively for GPS fixes triggered by Google Play Services, Maps, or banking apps."
                        else
                            "Combines passive app fixes with active GPS pings at your slider interval.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val sliderSec = config.intervalSeconds.coerceIn(15, 1800).toFloat()
                    val labelTxt = if (sliderSec < 60f) "${sliderSec.roundToInt()} sec" else "${(sliderSec / 60f).roundToInt()} min"
                    Text(
                        text = "ACTIVE PING SLIDER: $labelTxt",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Slider(
                        value = sliderSec,
                        onValueChange = { onUpdateInterval(it.roundToInt().coerceIn(15, 1800)) },
                        valueRange = 15f..1800f,
                        enabled = !passiveOnly
                    )

                    Button(
                        onClick = onCaptureGpsNow,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("capture_gps_now_button")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Capture GPS Ping Now")
                    }
                }

                PluginType.FOLDER_WATCHER -> {
                    val folders = TelemetryRepository.getFolderUris(config.paramsJson)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "WATCHED FOLDERS (SAF)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_watched_folder_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Folder")
                        }
                    }

                    if (folders.isEmpty()) {
                        Text(
                            text = "No folders selected yet. Pick a folder via Android Storage Access Framework (no broad storage permission needed).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        folders.forEach { (uriStr, label) ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            uriStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(onClick = { onRemoveWatchedFolder(uriStr) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Remove folder $label",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val intervalMin = (config.intervalSeconds / 60f).coerceIn(1f, 60f)
                    Text(
                        text = "SCAN INTERVAL: ${intervalMin.roundToInt()} MIN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Slider(
                        value = intervalMin,
                        onValueChange = { onUpdateInterval((it.roundToInt().coerceAtLeast(1)) * 60) },
                        valueRange = 1f..60f,
                        steps = 58
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onScanFoldersNow(true) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Baseline Only")
                        }
                        Button(
                            onClick = { onScanFoldersNow(false) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("scan_folders_now_button")
                        ) {
                            Text("Scan & Push")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    val selectedTarget = CloudPushTarget.fromId(params.optString("pushTarget", "drive"))
                    CloudTargetSettings(
                        selectedTarget = selectedTarget,
                        credentials = state.credentials,
                        isBusy = isBusy,
                        onSelectTarget = onSelectPushTarget,
                        onSaveCredentials = onSaveCredentials,
                        onTestConnection = onTestCloudConnection,
                        onLaunchDriveOAuth = onLaunchDriveOAuth
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleParamRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.minimumInteractiveComponentSize()
        )
    }
}
