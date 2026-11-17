package com.example.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CloudCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "droidpulse_secure_prefs")

class CredentialsStore(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val USE_SYSTEM_THEME = booleanPreferencesKey("use_system_theme")
        val DRIVE_CLIENT_ID = stringPreferencesKey("drive_client_id")
        val DRIVE_ACCESS_TOKEN = stringPreferencesKey("drive_access_token_enc")
        val DRIVE_REFRESH_TOKEN = stringPreferencesKey("drive_refresh_token_enc")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val DRIVE_UPLOAD_CONTENT = booleanPreferencesKey("drive_upload_content")
        val DROPBOX_TOKEN = stringPreferencesKey("dropbox_token_enc")
        val DROPBOX_FOLDER = stringPreferencesKey("dropbox_folder")
        val DROPBOX_UPLOAD_CONTENT = booleanPreferencesKey("dropbox_upload_content")
        val FTP_HOST = stringPreferencesKey("ftp_host")
        val FTP_PORT = intPreferencesKey("ftp_port")
        val FTP_USERNAME = stringPreferencesKey("ftp_username")
        val FTP_PASSWORD = stringPreferencesKey("ftp_password_enc")
        val FTP_REMOTE_DIR = stringPreferencesKey("ftp_remote_dir")
        val FTP_PASSIVE = booleanPreferencesKey("ftp_passive")
        val FTP_FTPS = booleanPreferencesKey("ftp_ftps")
        val FTP_UPLOAD_CONTENT = booleanPreferencesKey("ftp_upload_content")
        val PKCE_VERIFIER = stringPreferencesKey("pkce_verifier")
    }

    val darkThemeFlow: Flow<Pair<Boolean, Boolean>> = context.dataStore.data.map { prefs ->
        val dark = prefs[Keys.DARK_MODE] ?: true
        val system = prefs[Keys.USE_SYSTEM_THEME] ?: false
        dark to system
    }

    suspend fun setThemePreference(darkMode: Boolean, useSystem: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = darkMode
            prefs[Keys.USE_SYSTEM_THEME] = useSystem
        }
    }

    val credentialsFlow: Flow<CloudCredentials> = context.dataStore.data.map { prefs ->
        CloudCredentials(
            driveClientId = prefs[Keys.DRIVE_CLIENT_ID] ?: "",
            driveAccessToken = decrypt(prefs[Keys.DRIVE_ACCESS_TOKEN] ?: ""),
            driveRefreshToken = decrypt(prefs[Keys.DRIVE_REFRESH_TOKEN] ?: ""),
            driveFolderId = prefs[Keys.DRIVE_FOLDER_ID] ?: "",
            driveUploadFileContent = prefs[Keys.DRIVE_UPLOAD_CONTENT] ?: true,
            dropboxAccessToken = decrypt(prefs[Keys.DROPBOX_TOKEN] ?: ""),
            dropboxRemoteFolder = prefs[Keys.DROPBOX_FOLDER] ?: "/DroidPulse",
            dropboxUploadFileContent = prefs[Keys.DROPBOX_UPLOAD_CONTENT] ?: true,
            ftpHost = prefs[Keys.FTP_HOST] ?: "",
            ftpPort = prefs[Keys.FTP_PORT] ?: 21,
            ftpUsername = prefs[Keys.FTP_USERNAME] ?: "",
            ftpPassword = decrypt(prefs[Keys.FTP_PASSWORD] ?: ""),
            ftpRemoteDir = prefs[Keys.FTP_REMOTE_DIR] ?: "/DroidPulse",
            ftpUsePassive = prefs[Keys.FTP_PASSIVE] ?: true,
            ftpUseFtps = prefs[Keys.FTP_FTPS] ?: false,
            ftpUploadFileContent = prefs[Keys.FTP_UPLOAD_CONTENT] ?: true
        )
    }

    suspend fun getCredentials(): CloudCredentials = credentialsFlow.first()

    suspend fun saveCredentials(creds: CloudCredentials) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DRIVE_CLIENT_ID] = creds.driveClientId.trim()
            prefs[Keys.DRIVE_ACCESS_TOKEN] = encrypt(creds.driveAccessToken.trim())
            prefs[Keys.DRIVE_REFRESH_TOKEN] = encrypt(creds.driveRefreshToken.trim())
            prefs[Keys.DRIVE_FOLDER_ID] = creds.driveFolderId.trim()
            prefs[Keys.DRIVE_UPLOAD_CONTENT] = creds.driveUploadFileContent
            prefs[Keys.DROPBOX_TOKEN] = encrypt(creds.dropboxAccessToken.trim())
            prefs[Keys.DROPBOX_FOLDER] = creds.dropboxRemoteFolder.trim().ifEmpty { "/DroidPulse" }
            prefs[Keys.DROPBOX_UPLOAD_CONTENT] = creds.dropboxUploadFileContent
            prefs[Keys.FTP_HOST] = creds.ftpHost.trim()
            prefs[Keys.FTP_PORT] = creds.ftpPort
            prefs[Keys.FTP_USERNAME] = creds.ftpUsername.trim()
            prefs[Keys.FTP_PASSWORD] = encrypt(creds.ftpPassword)
            prefs[Keys.FTP_REMOTE_DIR] = creds.ftpRemoteDir.trim().ifEmpty { "/DroidPulse" }
            prefs[Keys.FTP_PASSIVE] = creds.ftpUsePassive
            prefs[Keys.FTP_FTPS] = creds.ftpUseFtps
            prefs[Keys.FTP_UPLOAD_CONTENT] = creds.ftpUploadFileContent
        }
    }

    suspend fun savePkceVerifier(verifier: String) {
        context.dataStore.edit { it[Keys.PKCE_VERIFIER] = verifier }
    }

    suspend fun getPkceVerifier(): String = context.dataStore.data.first()[Keys.PKCE_VERIFIER] ?: ""

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "droidpulse_creds_key"
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
