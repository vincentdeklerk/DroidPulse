package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.CallLogEntity
import com.example.data.db.LocationPingEntity
import com.example.data.db.NetworkLogEntity
import com.example.data.db.PushEventEntity
import com.example.data.db.WatchedFileEntity
import com.example.data.model.NetworkUsageReport
import com.example.data.model.PluginType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsAndExportsScreen(
    callSearchQuery: String,
    callLogs: List<CallLogEntity>,
    networkLogs: List<NetworkLogEntity>,
    networkReport: NetworkUsageReport,
    locationPings: List<LocationPingEntity>,
    watchedFiles: List<WatchedFileEntity>,
    pushEvents: List<PushEventEntity>,
    onCallSearchChange: (String) -> Unit,
    onExportCallsCsv: () -> Unit,
    onExportNetworkCsv: () -> Unit,
    onExportGpsCsv: () -> Unit,
    onExportGpsGpx: () -> Unit,
    onExportFilesCsv: () -> Unit,
    onRetryPushQueue: () -> Unit,
    onClearLogs: (PluginType) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Calls", "Network", "GPS", "Files & Push")
    val fmt = SimpleDateFormat("MMM dd HH:mm:ss", Locale.getDefault())

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Logs & Exports",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Local SQLite search, usage reports, CSV & GPX exports",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("logs_tab_$idx")
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // Calls Tab
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = callSearchQuery,
                        onValueChange = onCallSearchChange,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Search SQLite calls (name, number, WhatsApp/Signal)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_search_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onExportCallsCsv,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_calls_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export CSV (${callLogs.size})")
                        }
                        IconButton(
                            onClick = { onClearLogs(PluginType.CALL_LOGGER) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear call logs")
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (callLogs.isEmpty()) {
                        item {
                            EmptyLogCard(
                                icon = Icons.Filled.Call,
                                message = "No call records match your filter. Enable Call & VoIP Calendar or use Sync Phone Log."
                            )
                        }
                    } else {
                        items(callLogs, key = { it.id }) { call ->
                            LogRowCard(
                                title = "${call.contactOrLabel} (${call.sourceApp})",
                                subtitle = "${call.direction} • ${call.durationSeconds}s • ${fmt.format(Date(call.startedAt))}",
                                badge = if (call.syncedToCalendar) "Cal #${call.calendarEventId}" else "SQLite Only"
                            )
                        }
                    }
                }
            }

            1 -> {
                // Network Tab
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "NETWORK USAGE REPORT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                val total = networkReport.totalSamples.coerceAtLeast(1)
                                val wifiPct = (networkReport.wifiSamples * 100) / total
                                val cellPct = (networkReport.cellularSamples * 100) / total
                                val offPct = (networkReport.offlineSamples * 100) / total
                                Text(
                                    text = "Wi-Fi: $wifiPct% (${networkReport.wifiSamples})  •  Mobile: $cellPct% (${networkReport.cellularSamples})  •  Offline: $offPct%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Transitions: ${networkReport.transitionCount}  •  Avg Down: ${networkReport.avgDownKbps / 1000} Mbps  •  Avg Up: ${networkReport.avgUpKbps / 1000} Mbps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (networkReport.topNetworks.isNotEmpty()) {
                                    Text(
                                        text = "Top: " + networkReport.topNetworks.joinToString(", ") { "${it.first} (${it.second})" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onExportNetworkCsv,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_network_csv_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export Network CSV (${networkLogs.size})")
                            }
                            IconButton(
                                onClick = { onClearLogs(PluginType.NETWORK_MONITOR) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear network logs")
                            }
                        }
                    }
                    if (networkLogs.isEmpty()) {
                        item {
                            EmptyLogCard(
                                icon = Icons.Filled.Wifi,
                                message = "No network samples logged yet. Arm Network & Usage Monitor or tap Sample Now."
                            )
                        }
                    } else {
                        items(networkLogs, key = { it.id }) { net ->
                            LogRowCard(
                                title = "${net.transportType} • ${net.networkName}",
                                subtitle = "${net.eventKind} • ${net.linkDownKbps / 1000}↓/${net.linkUpKbps / 1000}↑ Mbps • ${fmt.format(Date(net.timestamp))}",
                                badge = if (net.isMetered) "Metered" else "Unmetered"
                            )
                        }
                    }
                }
            }

            2 -> {
                // GPS Tab
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onExportGpsGpx,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_gps_gpx_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export GPX")
                        }
                        OutlinedButton(
                            onClick = onExportGpsCsv,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_gps_csv_button")
                        ) {
                            Text("CSV (${locationPings.size})")
                        }
                        IconButton(
                            onClick = { onClearLogs(PluginType.GPS_LOGGER) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear GPS pings")
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (locationPings.isEmpty()) {
                        item {
                            EmptyLogCard(
                                icon = Icons.Filled.GpsFixed,
                                message = "No GPS pings captured yet. Passive mode records fixes whenever Maps/Play Services/banking apps ping GPS."
                            )
                        }
                    } else {
                        items(locationPings, key = { it.id }) { ping ->
                            LogRowCard(
                                title = String.format(Locale.US, "%.5f, %.5f", ping.latitude, ping.longitude),
                                subtitle = "±${ping.accuracyMeters.toInt()}m • alt ${ping.altitude.toInt()}m • ${fmt.format(Date(ping.timestamp))}",
                                badge = ping.providerSource
                            )
                        }
                    }
                }
            }

            else -> {
                // Files & Push Tab
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onRetryPushQueue,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("flush_push_queue_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Flush Push Queue")
                        }
                        OutlinedButton(
                            onClick = onExportFilesCsv,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("CSV (${watchedFiles.size})")
                        }
                        IconButton(
                            onClick = { onClearLogs(PluginType.FOLDER_WATCHER) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear watched file logs")
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pushEvents.isEmpty() && watchedFiles.isEmpty()) {
                        item {
                            EmptyLogCard(
                                icon = Icons.Filled.CloudUpload,
                                message = "No watched files or push events yet. Add a folder in Folder Watcher and tap Scan & Push."
                            )
                        }
                    } else {
                        items(pushEvents, key = { "push_${it.id}" }) { evt ->
                            val detail = if (evt.lastError.isNotBlank()) evt.lastError else evt.remotePath.ifBlank { fmt.format(Date(evt.createdAt)) }
                            LogRowCard(
                                title = "${evt.fileName} -> ${evt.targetType.uppercase()}",
                                subtitle = detail,
                                badge = evt.status
                            )
                        }
                        items(watchedFiles, key = { "file_${it.id}" }) { file ->
                            LogRowCard(
                                title = file.fileName,
                                subtitle = "${file.folderLabel} • ${file.sizeBytes / 1024} KB • ${fmt.format(Date(file.detectedAt))}",
                                badge = "WATCHED"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRowCard(
    title: String,
    subtitle: String,
    badge: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLogCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
