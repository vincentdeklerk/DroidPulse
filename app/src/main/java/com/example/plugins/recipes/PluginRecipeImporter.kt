package com.example.plugins.recipes

import com.example.data.db.PluginRecipeEntity
import com.example.data.model.RecipeType
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PluginRecipeImporter(private val repository: TelemetryRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun importFromUrl(url: String): Result<PluginRecipeEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = url.trim()
            require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
                "URL must start with https:// or http://"
            }
            val req = Request.Builder().url(trimmed).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} fetching recipe")
                val raw = resp.body?.string().orEmpty()
                parseAndSaveRecipe(raw, trimmed).getOrThrow()
            }
        }
    }

    suspend fun parseAndSaveRecipe(
        rawJson: String,
        sourceUrl: String = ""
    ): Result<PluginRecipeEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = JSONObject(rawJson.trim())
            val id = obj.optString("id").ifBlank {
                "recipe_${System.currentTimeMillis()}"
            }
            val name = obj.optString("name").ifBlank { error("Missing 'name' field in recipe JSON") }
            val typeStr = obj.optString("type").ifBlank { error("Missing 'type' field in recipe JSON") }
            val recipeType = RecipeType.fromId(typeStr)

            when (recipeType) {
                RecipeType.VOIP_MATCHER -> {
                    require(obj.optString("packageName").isNotBlank()) {
                        "VOIP_MATCHER recipe requires 'packageName'"
                    }
                }
                RecipeType.WEBHOOK_PUSH_RULE -> {
                    require(obj.optString("endpointUrl").startsWith("http")) {
                        "WEBHOOK_PUSH_RULE recipe requires valid 'endpointUrl'"
                    }
                }
            }

            val entity = PluginRecipeEntity(
                recipeId = id,
                name = name,
                recipeType = recipeType.id,
                sourceUrl = sourceUrl,
                enabled = true,
                configJson = obj.toString()
            )
            repository.upsertRecipe(entity)
            entity
        }
    }

    companion object {
        val SAMPLE_DISCORD_VOIP_RECIPE = """
            {
              "id": "voip_discord",
              "name": "Discord Voice & Calls",
              "type": "VOIP_MATCHER",
              "packageName": "com.discord",
              "appLabel": "Discord",
              "keywords": ["voice", "call", "incoming", "ongoing", "connected"]
            }
        """.trimIndent()

        val SAMPLE_WEBHOOK_RULE_RECIPE = """
            {
              "id": "webhook_pdf_notify",
              "name": "Webhook Alert on New PDF",
              "type": "WEBHOOK_PUSH_RULE",
              "fileExtension": ".pdf",
              "endpointUrl": "https://example.com/webhook/droidpulse"
            }
        """.trimIndent()
    }
}
