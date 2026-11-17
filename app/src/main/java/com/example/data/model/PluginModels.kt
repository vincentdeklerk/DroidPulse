package com.example.data.model

enum class PluginType(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultIntervalSeconds: Int
) {
    CALL_LOGGER(
        id = "call_logger",
        title = "Call & VoIP Calendar",
        subtitle = "Logs cellular, WhatsApp, Signal & Telegram calls offline to Calendar + SQLite",
        defaultIntervalSeconds = 0
    ),
    NETWORK_MONITOR(
        id = "network_monitor",
        title = "Network & Usage Monitor",
        subtitle = "Samples Wi-Fi & mobile data links at intervals for detailed usage reports",
        defaultIntervalSeconds = 300
    ),
    GPS_LOGGER(
        id = "gps_logger",
        title = "GPS Ping Tracker",
        subtitle = "Captures passive Play Services/Maps fixes & slider-paced pings to CSV/GPX",
        defaultIntervalSeconds = 120
    ),
    FOLDER_WATCHER(
        id = "folder_watcher",
        title = "Folder Watcher & Push",
        subtitle = "Detects new files in SAF folders & queues pushes to Drive, Dropbox, or FTP",
        defaultIntervalSeconds = 180
    );

    companion object {
        fun fromId(id: String): PluginType = entries.firstOrNull { it.id == id } ?: CALL_LOGGER
    }
}

enum class ExecutionMode(val id: String, val shortLabel: String, val description: String) {
    FOREGROUND_SERVICE(
        id = "fgs",
        shortLabel = "Persistent",
        description = "Runs continuously in a persistent foreground service for instant capture"
    ),
    WORK_MANAGER(
        id = "work",
        shortLabel = "Scheduled",
        description = "Runs periodic battery-friendly background intervals via WorkManager"
    ),
    PASSIVE_ONLY(
        id = "passive",
        shortLabel = "Passive",
        description = "Relies purely on OS callbacks, receivers, and piggybacked app events"
    );

    companion object {
        fun fromId(id: String): ExecutionMode = entries.firstOrNull { it.id == id } ?: PASSIVE_ONLY
    }
}

enum class CloudPushTarget(val id: String, val label: String) {
    GOOGLE_DRIVE("drive", "Google Drive"),
    DROPBOX("dropbox", "Dropbox"),
    FTP("ftp", "FTP / FTPS");

    companion object {
        fun fromId(id: String): CloudPushTarget = entries.firstOrNull { it.id == id } ?: GOOGLE_DRIVE
    }
}

enum class PushStatus(val id: String) {
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED")
}

enum class CallDirection(val id: String) {
    INCOMING("INCOMING"),
    OUTGOING("OUTGOING"),
    MISSED("MISSED"),
    UNKNOWN("UNKNOWN")
}

enum class RecipeType(val id: String, val label: String) {
    VOIP_MATCHER("VOIP_MATCHER", "VoIP Call Matcher"),
    WEBHOOK_PUSH_RULE("WEBHOOK_PUSH_RULE", "Folder Webhook Rule");

    companion object {
        fun fromId(id: String): RecipeType = entries.firstOrNull { it.id == id } ?: VOIP_MATCHER
    }
}

enum class PluginRuntimeState {
    RUNNING,
    ARMED,
    NEEDS_SETUP,
    PAUSED
}

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val isPrimary: Boolean,
    val color: Int
)

data class NetworkUsageReport(
    val totalSamples: Int,
    val wifiSamples: Int,
    val cellularSamples: Int,
    val offlineSamples: Int,
    val transitionCount: Int,
    val avgDownKbps: Int,
    val avgUpKbps: Int,
    val topNetworks: List<Pair<String, Int>>
)

data class CloudCredentials(
    val driveClientId: String = "",
    val driveAccessToken: String = "",
    val driveRefreshToken: String = "",
    val driveFolderId: String = "",
    val driveUploadFileContent: Boolean = true,
    val dropboxAccessToken: String = "",
    val dropboxRemoteFolder: String = "/DroidPulse",
    val dropboxUploadFileContent: Boolean = true,
    val ftpHost: String = "",
    val ftpPort: Int = 21,
    val ftpUsername: String = "",
    val ftpPassword: String = "",
    val ftpRemoteDir: String = "/DroidPulse",
    val ftpUsePassive: Boolean = true,
    val ftpUseFtps: Boolean = false,
    val ftpUploadFileContent: Boolean = true
)

data class SystemHealthSnapshot(
    val isBatteryOptimizationIgnored: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val hasCallPermissions: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val activeServiceRunning: Boolean = false
)
