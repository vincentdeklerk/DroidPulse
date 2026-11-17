package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.LocationPingEntity
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import com.example.plugins.export.TelemetryExporter
import com.example.plugins.recipes.PluginRecipeImporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DroidPulse", appName)
    }

    @Test
    fun `database seeds all four micro plugins and imports recipe`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TelemetryRepository(db)
            repo.ensureSeeded()
            val configs = repo.getAllConfigs()
            assertEquals(4, configs.size)
            assertTrue(configs.any { it.pluginId == PluginType.CALL_LOGGER.id })
            assertTrue(configs.any { it.pluginId == PluginType.FOLDER_WATCHER.id })

            val importer = PluginRecipeImporter(repo)
            val result = importer.parseAndSaveRecipe(PluginRecipeImporter.SAMPLE_DISCORD_VOIP_RECIPE)
            assertTrue(result.isSuccess)
            val voipRecipes = repo.getEnabledRecipes("VOIP_MATCHER")
            assertEquals(1, voipRecipes.size)
            assertEquals("Discord Voice & Calls", voipRecipes.first().name)
        } finally {
            db.close()
        }
    }

    @Test
    fun `exporter generates valid GPX 1_1 XML for location pings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TelemetryRepository(db)
            repo.insertLocationPing(
                LocationPingEntity(
                    timestamp = 1700000000000L,
                    latitude = -33.9249,
                    longitude = 18.4241,
                    altitude = 25.0,
                    accuracyMeters = 8.5f,
                    speedMps = 0.0f,
                    providerSource = "PASSIVE_APP_FIX"
                )
            )
            val exporter = TelemetryExporter(context, repo)
            val gpxUri = exporter.exportGpsGpx()
            val gpxContent = context.contentResolver.openInputStream(gpxUri)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
            assertTrue(gpxContent.contains("""<gpx version="1.1""""))
            assertTrue(gpxContent.contains("""lat="-33.9249" lon="18.4241""""))
        } finally {
            db.close()
        }
    }
}
