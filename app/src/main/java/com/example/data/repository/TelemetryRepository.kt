package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.db.CallLogEntity
import com.example.data.db.LocationPingEntity
import com.example.data.db.NetworkLogEntity
import com.example.data.db.PluginConfigEntity
import com.example.data.db.PluginRecipeEntity
import com.example.data.db.PushEventEntity
import com.example.data.db.WatchedFileEntity
import com.example.data.model.NetworkUsageReport
import com.example.data.model.PluginType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class TelemetryRepository(private val db: AppDatabase) {

    val pluginConfigs: Flow<List<PluginConfigEntity>> = db.pluginConfigDao().observeAll()
    val callLogsCount: Flow<Int> = db.callLogDao().observeCount()
    val networkLogsCount: Flow<Int> = db.networkLogDao().observeCount()
    val locationPingsCount: Flow<Int> = db.locationPingDao().observeCount()
    val watchedFilesCount: Flow<Int> = db.watchedFileDao().observeCount()
    val pendingPushCount: Flow<Int> = db.pushEventDao().observePendingCount()

    val recentNetworkLogs: Flow<List<NetworkLogEntity>> = db.networkLogDao().observeRecent()
    val recentLocationPings: Flow<List<LocationPingEntity>> = db.locationPingDao().observeRecent()
    val recentWatchedFiles: Flow<List<WatchedFileEntity>> = db.watchedFileDao().observeRecent()
    val recentPushEvents: Flow<List<PushEventEntity>> = db.pushEventDao().observeRecent()
    val recipes: Flow<List<PluginRecipeEntity>> = db.pluginRecipeDao().observeAll()

    val networkUsageReport: Flow<NetworkUsageReport> = recentNetworkLogs.map { logs ->
        if (logs.isEmpty()) {
            NetworkUsageReport(0, 0, 0, 0, 0, 0, 0, emptyList())
        } else {
            val wifi = logs.count { it.transportType == "WIFI" }
            val cell = logs.count { it.transportType == "CELLULAR" }
            val offline = logs.count { it.transportType == "NONE" }
            val transitions = logs.count { it.eventKind == "TRANSITION" }
            val downSamples = logs.filter { it.linkDownKbps > 0 }
            val upSamples = logs.filter { it.linkUpKbps > 0 }
            val avgDown = if (downSamples.isEmpty()) 0 else downSamples.sumOf { it.linkDownKbps } / downSamples.size
            val avgUp = if (upSamples.isEmpty()) 0 else upSamples.sumOf { it.linkUpKbps } / upSamples.size
            val top = logs
                .filter { it.networkName.isNotBlank() && it.networkName != "Offline" }
                .groupingBy { "${it.transportType}: ${it.networkName}" }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(4)
                .map { it.key to it.value }

            NetworkUsageReport(
                totalSamples = logs.size,
                wifiSamples = wifi,
                cellularSamples = cell,
                offlineSamples = offline,
                transitionCount = transitions,
                avgDownKbps = avgDown,
                avgUpKbps = avgUp,
                topNetworks = top
            )
        }
    }

    fun searchCallLogs(query: String): Flow<List<CallLogEntity>> =
        db.callLogDao().searchLogs(query.trim())

    suspend fun ensureSeeded() {
        AppDatabase.ensureSeeded(db.pluginConfigDao())
    }

    suspend fun getAllConfigs(): List<PluginConfigEntity> = db.pluginConfigDao().getAll()

    suspend fun getConfig(pluginType: PluginType): PluginConfigEntity? =
        db.pluginConfigDao().getById(pluginType.id)

    suspend fun upsertConfig(config: PluginConfigEntity) {
        db.pluginConfigDao().upsert(config)
    }

    suspend fun updatePluginStatus(pluginId: String, note: String) {
        db.pluginConfigDao().updateStatus(pluginId, System.currentTimeMillis(), note)
    }

    suspend fun insertCallLog(log: CallLogEntity): Long = db.callLogDao().insert(log)

    suspend fun callExistsNear(sourceApp: String, label: String, timestamp: Long): Boolean =
        db.callLogDao().existsNearTimestamp(sourceApp, label, timestamp - 5000L, timestamp + 5000L)

    suspend fun insertNetworkLog(log: NetworkLogEntity): Long = db.networkLogDao().insert(log)

    suspend fun getLatestNetworkLog(): NetworkLogEntity? = db.networkLogDao().getLatest()

    suspend fun insertLocationPing(ping: LocationPingEntity): Long = db.locationPingDao().insert(ping)

    suspend fun hasWatchedFile(uri: String): Boolean = db.watchedFileDao().existsByUri(uri)

    suspend fun insertWatchedFile(file: WatchedFileEntity): Long = db.watchedFileDao().insertIgnore(file)

    suspend fun insertPushEvent(event: PushEventEntity): Long = db.pushEventDao().insert(event)

    suspend fun updatePushEvent(event: PushEventEntity) = db.pushEventDao().update(event)

    suspend fun getPendingPushEvents(): List<PushEventEntity> = db.pushEventDao().getPendingOrRetryable()

    suspend fun retryFailedPushes() = db.pushEventDao().resetFailedToPending()

    suspend fun getEnabledRecipes(type: String): List<PluginRecipeEntity> =
        db.pluginRecipeDao().getEnabledByType(type)

    suspend fun upsertRecipe(recipe: PluginRecipeEntity) = db.pluginRecipeDao().upsert(recipe)

    suspend fun setRecipeEnabled(recipeId: String, enabled: Boolean) =
        db.pluginRecipeDao().setEnabled(recipeId, enabled)

    suspend fun deleteRecipe(recipeId: String) = db.pluginRecipeDao().deleteById(recipeId)

    suspend fun getCallLogsForExport(): List<CallLogEntity> = db.callLogDao().getAllForExport()
    suspend fun getNetworkLogsForExport(): List<NetworkLogEntity> = db.networkLogDao().getAllForExport()
    suspend fun getLocationPingsForExport(): List<LocationPingEntity> = db.locationPingDao().getAllAscendingForExport()
    suspend fun getWatchedFilesForExport(): List<WatchedFileEntity> = db.watchedFileDao().getAllForExport()

    suspend fun clearLogsForPlugin(pluginType: PluginType) {
        when (pluginType) {
            PluginType.CALL_LOGGER -> db.callLogDao().clearAll()
            PluginType.NETWORK_MONITOR -> db.networkLogDao().clearAll()
            PluginType.GPS_LOGGER -> db.locationPingDao().clearAll()
            PluginType.FOLDER_WATCHER -> {
                db.pushEventDao().clearAll()
                db.watchedFileDao().clearAll()
            }
        }
    }

    companion object {
        fun parseParams(json: String): JSONObject =
            runCatching { JSONObject(json) }.getOrElse { JSONObject() }

        fun getFolderUris(paramsJson: String): List<Pair<String, String>> {
            val obj = parseParams(paramsJson)
            val arr = obj.optJSONArray("folderUris") ?: JSONArray()
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i)
                if (item != null) {
                    val uri = item.optString("uri")
                    val label = item.optString("label", "Watched Folder")
                    if (uri.isNotBlank()) list.add(uri to label)
                } else {
                    val raw = arr.optString(i)
                    if (raw.isNotBlank()) list.add(raw to "Watched Folder")
                }
            }
            return list
        }

        fun withUpdatedFolderUris(paramsJson: String, folders: List<Pair<String, String>>): String {
            val obj = parseParams(paramsJson)
            val arr = JSONArray()
            folders.forEach { (uri, label) ->
                arr.put(JSONObject().apply {
                    put("uri", uri)
                    put("label", label)
                })
            }
            obj.put("folderUris", arr)
            return obj.toString()
        }
    }
}
