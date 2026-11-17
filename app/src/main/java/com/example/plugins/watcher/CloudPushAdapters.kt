package com.example.plugins.watcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.example.data.db.PushEventEntity
import com.example.data.model.CloudCredentials
import com.example.data.model.CloudPushTarget
import com.example.data.prefs.CredentialsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed class PushResult {
    data class Success(val remotePath: String) : PushResult()
    data class Failure(val reason: String) : PushResult()
}

class CloudPushAdapters(
    private val context: Context,
    private val credentialsStore: CredentialsStore
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val OAUTH_REDIRECT_URI = "com.aistudio.droidpulse.xk7mqt:/oauth2redirect"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }

    suspend fun launchDriveOAuthPkce(clientId: String) {
        val verifierBytes = ByteArray(32)
        SecureRandom().nextBytes(verifierBytes)
        val verifier = Base64.encodeToString(
            verifierBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        credentialsStore.savePkceVerifier(verifier)

        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
            .buildUpon()
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("redirect_uri", OAUTH_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", DRIVE_SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        withContext(Dispatchers.Main) {
            val customTabs = CustomTabsIntent.Builder().build()
            customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { customTabs.launchUrl(context, authUri) }
                .onFailure {
                    val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                }
        }
    }

    suspend fun exchangeDriveAuthCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val creds = credentialsStore.getCredentials()
            val verifier = credentialsStore.getPkceVerifier()
            require(creds.driveClientId.isNotBlank()) { "Google Drive OAuth Client ID is empty" }
            require(verifier.isNotBlank()) { "PKCE verifier missing; restart OAuth flow" }

            val form = FormBody.Builder()
                .add("client_id", creds.driveClientId)
                .add("code", code)
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", OAUTH_REDIRECT_URI)
                .build()

            val req = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("OAuth exchange failed (${resp.code}): $bodyStr")
                }
                val json = JSONObject(bodyStr)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", creds.driveRefreshToken)
                credentialsStore.saveCredentials(
                    creds.copy(driveAccessToken = accessToken, driveRefreshToken = refreshToken)
                )
                "Google Drive connected via OAuth"
            }
        }
    }

    private suspend fun refreshDriveTokenIfPossible(creds: CloudCredentials): String? {
        if (creds.driveRefreshToken.isBlank() || creds.driveClientId.isBlank()) return null
        return runCatching {
            val form = FormBody.Builder()
                .add("client_id", creds.driveClientId)
                .add("refresh_token", creds.driveRefreshToken)
                .add("grant_type", "refresh_token")
                .build()
            val req = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string().orEmpty())
                val newAccess = json.optString("access_token")
                if (newAccess.isNotBlank()) {
                    credentialsStore.saveCredentials(creds.copy(driveAccessToken = newAccess))
                    newAccess
                } else null
            }
        }.getOrNull()
    }

    suspend fun testConnection(target: CloudPushTarget): Result<String> = withContext(Dispatchers.IO) {
        val creds = credentialsStore.getCredentials()
        when (target) {
            CloudPushTarget.GOOGLE_DRIVE -> testDrive(creds)
            CloudPushTarget.DROPBOX -> testDropbox(creds)
            CloudPushTarget.FTP -> testFtp(creds)
        }
    }

    private suspend fun testDrive(creds: CloudCredentials): Result<String> = runCatching {
        require(creds.driveAccessToken.isNotBlank()) { "Drive access token is missing" }
        var token = creds.driveAccessToken
        var respPair = executeDriveAbout(token)
        if (respPair.first == 401) {
            val refreshed = refreshDriveTokenIfPossible(creds)
            if (refreshed != null) {
                token = refreshed
                respPair = executeDriveAbout(token)
            }
        }
        if (respPair.first !in 200..299) {
            error("Drive API ${respPair.first}: ${respPair.second}")
        }
        val user = JSONObject(respPair.second).optJSONObject("user")
        val email = user?.optString("emailAddress", "Connected") ?: "Connected"
        "Drive OK ($email)"
    }

    private fun executeDriveAbout(token: String): Pair<Int, String> {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/about?fields=user")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            resp.code to resp.body?.string().orEmpty()
        }
    }

    private fun testDropbox(creds: CloudCredentials): Result<String> = runCatching {
        require(creds.dropboxAccessToken.isNotBlank()) { "Dropbox access token is missing" }
        val req = Request.Builder()
            .url("https://api.dropboxapi.com/2/users/get_current_account")
            .header("Authorization", "Bearer ${creds.dropboxAccessToken}")
            .post("null".toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Dropbox API ${resp.code}: $body")
            val email = JSONObject(body).optString("email", "Connected")
            "Dropbox OK ($email)"
        }
    }

    private fun testFtp(creds: CloudCredentials): Result<String> = runCatching {
        require(creds.ftpHost.isNotBlank()) { "FTP host is required" }
        val client = if (creds.ftpUseFtps) FTPSClient() else FTPClient()
        client.connectTimeout = 12_000
        try {
            client.connect(creds.ftpHost, creds.ftpPort)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                error("FTP refused connection: ${client.replyString.trim()}")
            }
            val user = creds.ftpUsername.ifBlank { "anonymous" }
            if (!client.login(user, creds.ftpPassword)) {
                error("FTP login failed: ${client.replyString.trim()}")
            }
            if (creds.ftpUsePassive) client.enterLocalPassiveMode()
            ensureFtpDirectory(client, creds.ftpRemoteDir)
            "FTP OK (${creds.ftpHost}:${creds.ftpPort}${creds.ftpRemoteDir})"
        } finally {
            runCatching {
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            }
        }
    }

    suspend fun pushEvent(event: PushEventEntity): PushResult = withContext(Dispatchers.IO) {
        val creds = credentialsStore.getCredentials()
        val target = CloudPushTarget.fromId(event.targetType)
        when (target) {
            CloudPushTarget.GOOGLE_DRIVE -> pushToDrive(event, creds)
            CloudPushTarget.DROPBOX -> pushToDropbox(event, creds)
            CloudPushTarget.FTP -> pushToFtp(event, creds)
        }
    }

    private suspend fun pushToDrive(event: PushEventEntity, creds: CloudCredentials): PushResult {
        if (creds.driveAccessToken.isBlank()) {
            return PushResult.Failure("Missing Google Drive access token")
        }
        var token = creds.driveAccessToken
        val result = executeDriveUpload(event, creds, token)
        if (result is PushResult.Failure && result.reason.contains("401")) {
            val refreshed = refreshDriveTokenIfPossible(creds)
            if (refreshed != null) {
                token = refreshed
                return executeDriveUpload(event, creds, token)
            }
        }
        return result
    }

    private fun executeDriveUpload(
        event: PushEventEntity,
        creds: CloudCredentials,
        token: String
    ): PushResult = runCatching {
        val metaObj = JSONObject().apply {
            val uploadName = if (creds.driveUploadFileContent) event.fileName else "${event.fileName}.event.json"
            put("name", uploadName)
            if (creds.driveFolderId.isNotBlank()) {
                put("parents", JSONArray().put(creds.driveFolderId))
            }
        }

        val payloadBytes = readFileOrManifestBytes(event, creds.driveUploadFileContent)
        val mime = if (creds.driveUploadFileContent) "application/octet-stream" else "application/json"

        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metaObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(payloadBytes.toRequestBody(mime.toMediaType()))
            .build()

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val respText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@runCatching PushResult.Failure("Drive HTTP ${resp.code}: ${respText.take(180)}")
            }
            val fileId = JSONObject(respText).optString("id", "uploaded")
            PushResult.Success("gdrive://$fileId/${event.fileName}")
        }
    }.getOrElse { PushResult.Failure(it.message ?: "Drive upload error") }

    private fun pushToDropbox(event: PushEventEntity, creds: CloudCredentials): PushResult = runCatching {
        if (creds.dropboxAccessToken.isBlank()) {
            return@runCatching PushResult.Failure("Missing Dropbox access token")
        }
        val cleanFolder = creds.dropboxRemoteFolder.trimEnd('/').let {
            if (it.startsWith("/")) it else "/$it"
        }
        val uploadName = if (creds.dropboxUploadFileContent) event.fileName else "${event.fileName}.event.json"
        val remotePath = "$cleanFolder/$uploadName"
        val apiArg = JSONObject().apply {
            put("path", remotePath)
            put("mode", "add")
            put("autorename", true)
            put("mute", false)
        }.toString()

        val payloadBytes = readFileOrManifestBytes(event, creds.dropboxUploadFileContent)
        val reqBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = payloadBytes.size.toLong()
            override fun writeTo(sink: BufferedSink) {
                sink.write(payloadBytes)
            }
        }

        val req = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload")
            .header("Authorization", "Bearer ${creds.dropboxAccessToken}")
            .header("Dropbox-API-Arg", apiArg)
            .post(reqBody)
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val respText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@runCatching PushResult.Failure("Dropbox HTTP ${resp.code}: ${respText.take(180)}")
            }
            PushResult.Success("dropbox:$remotePath")
        }
    }.getOrElse { PushResult.Failure(it.message ?: "Dropbox upload error") }

    private fun pushToFtp(event: PushEventEntity, creds: CloudCredentials): PushResult = runCatching {
        if (creds.ftpHost.isBlank()) {
            return@runCatching PushResult.Failure("Missing FTP host")
        }
        val client = if (creds.ftpUseFtps) FTPSClient() else FTPClient()
        client.connectTimeout = 15_000
        try {
            client.connect(creds.ftpHost, creds.ftpPort)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                return@runCatching PushResult.Failure("FTP connect error: ${client.replyString.trim()}")
            }
            val user = creds.ftpUsername.ifBlank { "anonymous" }
            if (!client.login(user, creds.ftpPassword)) {
                return@runCatching PushResult.Failure("FTP auth failed: ${client.replyString.trim()}")
            }
            if (creds.ftpUsePassive) client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            ensureFtpDirectory(client, creds.ftpRemoteDir)

            val uploadName = if (creds.ftpUploadFileContent) event.fileName else "${event.fileName}.event.json"
            val payloadBytes = readFileOrManifestBytes(event, creds.ftpUploadFileContent)
            val stored = ByteArrayInputStream(payloadBytes).use { stream ->
                client.storeFile(uploadName, stream)
            }
            if (!stored) {
                return@runCatching PushResult.Failure("FTP store failed: ${client.replyString.trim()}")
            }
            PushResult.Success("ftp://${creds.ftpHost}${creds.ftpRemoteDir.trimEnd('/')}/$uploadName")
        } finally {
            runCatching {
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            }
        }
    }.getOrElse { PushResult.Failure(it.message ?: "FTP upload error") }

    suspend fun fireWebhookRule(endpointUrl: String, fileName: String, sizeBytes: Long, uri: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("event", "new_file_detected")
                    put("fileName", fileName)
                    put("sizeBytes", sizeBytes)
                    put("uri", uri)
                    put("detectedAt", System.currentTimeMillis())
                    put("source", "DroidPulse")
                }.toString()
                val req = Request.Builder()
                    .url(endpointUrl)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Webhook HTTP ${resp.code}")
                    "Webhook HTTP ${resp.code}"
                }
            }
        }

    private fun readFileOrManifestBytes(event: PushEventEntity, includeContent: Boolean): ByteArray {
        if (includeContent) {
            val contentBytes = runCatching {
                val uri = Uri.parse(event.fileUri)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (contentBytes != null) return contentBytes
        }
        return JSONObject().apply {
            put("event", "file_detected")
            put("fileName", event.fileName)
            put("fileUri", event.fileUri)
            put("detectedAt", event.createdAt)
            put("source", "DroidPulse FolderWatcher")
        }.toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun ensureFtpDirectory(client: FTPClient, remoteDir: String) {
        val segments = remoteDir.split("/").filter { it.isNotBlank() }
        if (remoteDir.startsWith("/")) {
            client.changeWorkingDirectory("/")
        }
        for (seg in segments) {
            if (!client.changeWorkingDirectory(seg)) {
                client.makeDirectory(seg)
                client.changeWorkingDirectory(seg)
            }
        }
    }
}
