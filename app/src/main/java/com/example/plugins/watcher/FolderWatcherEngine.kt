package com.example.plugins.watcher

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.db.PushEventEntity
import com.example.data.db.WatchedFileEntity
import com.example.data.model.PluginType
import com.example.data.model.PushStatus
import com.example.data.model.RecipeType
import com.example.data.repository.TelemetryRepository
import com.example.plugins.orchestrator.PushQueueWorker
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FolderWatcherEngine(
    private val context: Context,
    private val repository: TelemetryRepository,
    private val pushAdapters: CloudPushAdapters
) {

    suspend fun scanConfiguredFolders(seedExistingWithoutPush: Boolean = false): Int {
        val cfg = repository.getConfig(PluginType.FOLDER_WATCHER) ?: return 0
        val params = TelemetryRepository.parseParams(cfg.paramsJson)
        val folders = TelemetryRepository.getFolderUris(cfg.paramsJson)
        if (folders.isEmpty()) return 0

        val pushTarget = params.optString("pushTarget", "drive")
        val webhookRecipes = repository.getEnabledRecipes(RecipeType.WEBHOOK_PUSH_RULE.id)
        var newlyDiscovered = 0

        for ((folderUriStr, folderLabel) in folders) {
            val treeUri = runCatching { Uri.parse(folderUriStr) }.getOrNull() ?: continue
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            val children = rootDoc.listFiles()

            for (doc in children) {
                if (!doc.isFile) continue
                val fileUriStr = doc.uri.toString()
                if (repository.hasWatchedFile(fileUriStr)) continue

                val fileName = doc.name ?: "unnamed_file"
                val mimeType = doc.type ?: "application/octet-stream"
                val sizeBytes = doc.length()
                val now = System.currentTimeMillis()

                val watchedId = repository.insertWatchedFile(
                    WatchedFileEntity(
                        fileUri = fileUriStr,
                        fileName = fileName,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        folderUri = folderUriStr,
                        folderLabel = folderLabel,
                        detectedAt = now
                    )
                )
                if (watchedId <= 0L) continue
                newlyDiscovered++

                if (!seedExistingWithoutPush) {
                    repository.insertPushEvent(
                        PushEventEntity(
                            watchedFileId = watchedId,
                            fileUri = fileUriStr,
                            fileName = fileName,
                            targetType = pushTarget,
                            status = PushStatus.PENDING.id,
                            createdAt = now
                        )
                    )

                    // Trigger matching declarative webhook recipes
                    for (recipe in webhookRecipes) {
                        val obj = runCatching { JSONObject(recipe.configJson) }.getOrNull() ?: continue
                        val ext = obj.optString("fileExtension", "")
                        val url = obj.optString("endpointUrl", "")
                        if (url.isNotBlank() && (ext.isBlank() || fileName.endsWith(ext, ignoreCase = true))) {
                            pushAdapters.fireWebhookRule(url, fileName, sizeBytes, fileUriStr)
                        }
                    }
                }
            }
        }

        if (newlyDiscovered > 0) {
            val note = if (seedExistingWithoutPush) {
                "Indexed $newlyDiscovered baseline files"
            } else {
                "Queued $newlyDiscovered new file(s) -> $pushTarget"
            }
            repository.updatePluginStatus(PluginType.FOLDER_WATCHER.id, note)
            if (!seedExistingWithoutPush) {
                enqueuePushWorker(context)
            }
        }
        return newlyDiscovered
    }

    suspend fun processPendingQueue(): Pair<Int, Int> {
        val pending = repository.getPendingPushEvents()
        var succeeded = 0
        var failed = 0
        for (event in pending) {
            when (val res = pushAdapters.pushEvent(event)) {
                is PushResult.Success -> {
                    succeeded++
                    repository.updatePushEvent(
                        event.copy(
                            status = PushStatus.SUCCESS.id,
                            remotePath = res.remotePath,
                            lastError = "",
                            completedAt = System.currentTimeMillis()
                        )
                    )
                }
                is PushResult.Failure -> {
                    failed++
                    repository.updatePushEvent(
                        event.copy(
                            status = PushStatus.FAILED.id,
                            retryCount = event.retryCount + 1,
                            lastError = res.reason
                        )
                    )
                }
            }
        }
        if (succeeded > 0 || failed > 0) {
            repository.updatePluginStatus(
                PluginType.FOLDER_WATCHER.id,
                "Pushed $succeeded OK, $failed failed"
            )
        }
        return succeeded to failed
    }

    companion object {
        fun enqueuePushWorker(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<PushQueueWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            runCatching {
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(
                        context,
                        androidx.work.Configuration.Builder().build()
                    )
                }
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "droidpulse_push_queue",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    req
                )
            }
        }
    }
}
