package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_configs")
data class PluginConfigEntity(
    @PrimaryKey val pluginId: String,
    val enabled: Boolean = false,
    val executionMode: String = "passive",
    val intervalSeconds: Int = 120,
    val paramsJson: String = "{}",
    val lastRunAt: Long = 0L,
    val lastStatusNote: String = "Idle"
)

@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["contactOrLabel"]),
        Index(value = ["sourceApp"])
    ]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceApp: String,
    val packageName: String,
    val contactOrLabel: String,
    val phoneNumber: String = "",
    val direction: String,
    val startedAt: Long,
    val durationSeconds: Long,
    val calendarEventId: Long? = null,
    val syncedToCalendar: Boolean = false,
    val notes: String = ""
)

@Entity(
    tableName = "network_logs",
    indices = [Index(value = ["timestamp"]), Index(value = ["transportType"])]
)
data class NetworkLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val transportType: String,
    val networkName: String,
    val isMetered: Boolean,
    val isValidated: Boolean,
    val linkDownKbps: Int,
    val linkUpKbps: Int,
    val eventKind: String
)

@Entity(
    tableName = "location_pings",
    indices = [Index(value = ["timestamp"]), Index(value = ["providerSource"])]
)
data class LocationPingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val providerSource: String
)

@Entity(
    tableName = "watched_files",
    indices = [Index(value = ["fileUri"], unique = true), Index(value = ["detectedAt"])]
)
data class WatchedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileUri: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val folderUri: String,
    val folderLabel: String,
    val detectedAt: Long
)

@Entity(
    tableName = "push_events",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])]
)
data class PushEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val watchedFileId: Long,
    val fileUri: String,
    val fileName: String,
    val targetType: String,
    val status: String,
    val retryCount: Int = 0,
    val remotePath: String = "",
    val lastError: String = "",
    val createdAt: Long,
    val completedAt: Long = 0L
)

@Entity(tableName = "plugin_recipes")
data class PluginRecipeEntity(
    @PrimaryKey val recipeId: String,
    val name: String,
    val recipeType: String,
    val sourceUrl: String = "",
    val enabled: Boolean = true,
    val configJson: String,
    val installedAt: Long = System.currentTimeMillis()
)
