package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ExecutionMode
import com.example.data.model.PluginType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PluginConfigEntity::class,
        CallLogEntity::class,
        NetworkLogEntity::class,
        LocationPingEntity::class,
        WatchedFileEntity::class,
        PushEventEntity::class,
        PluginRecipeEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pluginConfigDao(): PluginConfigDao
    abstract fun callLogDao(): CallLogDao
    abstract fun networkLogDao(): NetworkLogDao
    abstract fun locationPingDao(): LocationPingDao
    abstract fun watchedFileDao(): WatchedFileDao
    abstract fun pushEventDao(): PushEventDao
    abstract fun pluginRecipeDao(): PluginRecipeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "droidpulse_telemetry.db"
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaults(getInstance(appContext).pluginConfigDao())
                        }
                    }
                })
                .build()
        }

        suspend fun ensureSeeded(dao: PluginConfigDao) {
            seedDefaults(dao)
        }

        private suspend fun seedDefaults(dao: PluginConfigDao) {
            val defaults = listOf(
                PluginConfigEntity(
                    pluginId = PluginType.CALL_LOGGER.id,
                    enabled = false,
                    executionMode = ExecutionMode.PASSIVE_ONLY.id,
                    intervalSeconds = 0,
                    paramsJson = """{"calendarId":-1,"calendarName":"Not Selected","trackPhone":true,"trackWhatsApp":true,"trackSignal":true,"trackTelegram":true}""",
                    lastStatusNote = "Select calendar & grant permissions"
                ),
                PluginConfigEntity(
                    pluginId = PluginType.NETWORK_MONITOR.id,
                    enabled = false,
                    executionMode = ExecutionMode.FOREGROUND_SERVICE.id,
                    intervalSeconds = 300,
                    paramsJson = """{"logTransitions":true}""",
                    lastStatusNote = "Ready to arm"
                ),
                PluginConfigEntity(
                    pluginId = PluginType.GPS_LOGGER.id,
                    enabled = false,
                    executionMode = ExecutionMode.PASSIVE_ONLY.id,
                    intervalSeconds = 120,
                    paramsJson = """{"passiveOnly":true}""",
                    lastStatusNote = "Passive piggyback or active slider"
                ),
                PluginConfigEntity(
                    pluginId = PluginType.FOLDER_WATCHER.id,
                    enabled = false,
                    executionMode = ExecutionMode.WORK_MANAGER.id,
                    intervalSeconds = 180,
                    paramsJson = """{"folderUris":[],"pushTarget":"drive"}""",
                    lastStatusNote = "Pick folder & push target"
                )
            )
            dao.insertAllIgnore(defaults)
        }
    }
}
