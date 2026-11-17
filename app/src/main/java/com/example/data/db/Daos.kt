package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginConfigDao {
    @Query("SELECT * FROM plugin_configs ORDER BY pluginId ASC")
    fun observeAll(): Flow<List<PluginConfigEntity>>

    @Query("SELECT * FROM plugin_configs")
    suspend fun getAll(): List<PluginConfigEntity>

    @Query("SELECT * FROM plugin_configs WHERE pluginId = :pluginId LIMIT 1")
    suspend fun getById(pluginId: String): PluginConfigEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(configs: List<PluginConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: PluginConfigEntity)

    @Query("UPDATE plugin_configs SET lastRunAt = :timestamp, lastStatusNote = :note WHERE pluginId = :pluginId")
    suspend fun updateStatus(pluginId: String, timestamp: Long, note: String)
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY startedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<CallLogEntity>>

    @Query(
        """
        SELECT * FROM call_logs
        WHERE (:query = '' OR contactOrLabel LIKE '%' || :query || '%'
               OR phoneNumber LIKE '%' || :query || '%'
               OR sourceApp LIKE '%' || :query || '%'
               OR notes LIKE '%' || :query || '%')
        ORDER BY startedAt DESC
        LIMIT 500
        """
    )
    fun searchLogs(query: String): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM call_logs ORDER BY startedAt DESC")
    suspend fun getAllForExport(): List<CallLogEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM call_logs WHERE sourceApp = :sourceApp AND startedAt BETWEEN :fromTs AND :toTs AND contactOrLabel = :label)")
    suspend fun existsNearTimestamp(sourceApp: String, label: String, fromTs: Long, toTs: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CallLogEntity): Long

    @Query("DELETE FROM call_logs")
    suspend fun clearAll()
}

@Dao
interface NetworkLogDao {
    @Query("SELECT * FROM network_logs ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<NetworkLogEntity>>

    @Query("SELECT COUNT(*) FROM network_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM network_logs ORDER BY timestamp DESC")
    suspend fun getAllForExport(): List<NetworkLogEntity>

    @Query("SELECT * FROM network_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): NetworkLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NetworkLogEntity): Long

    @Query("DELETE FROM network_logs")
    suspend fun clearAll()
}

@Dao
interface LocationPingDao {
    @Query("SELECT * FROM location_pings ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<LocationPingEntity>>

    @Query("SELECT COUNT(*) FROM location_pings")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM location_pings ORDER BY timestamp ASC")
    suspend fun getAllAscendingForExport(): List<LocationPingEntity>

    @Query("SELECT * FROM location_pings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): LocationPingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ping: LocationPingEntity): Long

    @Query("DELETE FROM location_pings")
    suspend fun clearAll()
}

@Dao
interface WatchedFileDao {
    @Query("SELECT * FROM watched_files ORDER BY detectedAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<WatchedFileEntity>>

    @Query("SELECT COUNT(*) FROM watched_files")
    fun observeCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM watched_files WHERE fileUri = :fileUri)")
    suspend fun existsByUri(fileUri: String): Boolean

    @Query("SELECT * FROM watched_files ORDER BY detectedAt DESC")
    suspend fun getAllForExport(): List<WatchedFileEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(file: WatchedFileEntity): Long

    @Query("DELETE FROM watched_files")
    suspend fun clearAll()
}

@Dao
interface PushEventDao {
    @Query("SELECT * FROM push_events ORDER BY createdAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<PushEventEntity>>

    @Query("SELECT COUNT(*) FROM push_events WHERE status != 'SUCCESS'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM push_events WHERE status IN ('PENDING', 'FAILED') AND retryCount < 5 ORDER BY createdAt ASC")
    suspend fun getPendingOrRetryable(): List<PushEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PushEventEntity): Long

    @Update
    suspend fun update(event: PushEventEntity)

    @Query("UPDATE push_events SET status = 'PENDING', lastError = '' WHERE status = 'FAILED'")
    suspend fun resetFailedToPending()

    @Query("DELETE FROM push_events")
    suspend fun clearAll()
}

@Dao
interface PluginRecipeDao {
    @Query("SELECT * FROM plugin_recipes ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<PluginRecipeEntity>>

    @Query("SELECT * FROM plugin_recipes WHERE enabled = 1 AND recipeType = :type")
    suspend fun getEnabledByType(type: String): List<PluginRecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: PluginRecipeEntity)

    @Query("UPDATE plugin_recipes SET enabled = :enabled WHERE recipeId = :recipeId")
    suspend fun setEnabled(recipeId: String, enabled: Boolean)

    @Query("DELETE FROM plugin_recipes WHERE recipeId = :recipeId")
    suspend fun deleteById(recipeId: String)
}
