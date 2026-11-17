package com.example.plugins.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.data.db.CallLogEntity
import com.example.data.model.CallDirection
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import com.example.plugins.calendar.CalendarSyncHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallCaptureManager(
    private val context: Context,
    private val repository: TelemetryRepository,
    private val calendarSyncHelper: CalendarSyncHelper
) {

    fun hasCallPermissions(): Boolean {
        val phoneState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val callLog = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        return phoneState && callLog
    }

    suspend fun recordCall(
        sourceApp: String,
        packageName: String,
        contactOrLabel: String,
        phoneNumber: String,
        direction: CallDirection,
        startedAt: Long,
        durationSeconds: Long,
        notes: String = "",
        forceCalendarWrite: Boolean = false
    ): Boolean {
        val config = repository.getConfig(PluginType.CALL_LOGGER)
        if (!forceCalendarWrite && config?.enabled != true) return false

        val cleanLabel = contactOrLabel.ifBlank { phoneNumber.ifBlank { "Unknown Caller" } }
        if (repository.callExistsNear(sourceApp, cleanLabel, startedAt)) {
            return false
        }

        val params = TelemetryRepository.parseParams(config?.paramsJson ?: "{}")
        val calendarId = params.optLong("calendarId", -1L)

        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(startedAt))
        val calTitle = "[$sourceApp] ${direction.name.lowercase().replaceFirstChar { it.uppercase() }}: $cleanLabel"
        val calDesc = buildString {
            appendLine("Source: $sourceApp ($packageName)")
            appendLine("Direction: ${direction.name}")
            if (phoneNumber.isNotBlank()) appendLine("Number: $phoneNumber")
            appendLine("Duration: ${durationSeconds}s")
            appendLine("Started: $timeFmt")
            if (notes.isNotBlank()) appendLine("Notes: $notes")
            append("Logged offline by DroidPulse")
        }

        val eventId = if (calendarId > 0L) {
            calendarSyncHelper.insertCallEvent(
                calendarId = calendarId,
                title = calTitle,
                description = calDesc,
                startedAtMs = startedAt,
                durationSeconds = durationSeconds
            )
        } else null

        repository.insertCallLog(
            CallLogEntity(
                sourceApp = sourceApp,
                packageName = packageName,
                contactOrLabel = cleanLabel,
                phoneNumber = phoneNumber,
                direction = direction.name,
                startedAt = startedAt,
                durationSeconds = durationSeconds,
                calendarEventId = eventId,
                syncedToCalendar = eventId != null,
                notes = notes
            )
        )
        val statusMsg = if (eventId != null) {
            "Last: $sourceApp ($cleanLabel) -> Cal #$eventId"
        } else {
            "Last: $sourceApp ($cleanLabel) -> SQLite only"
        }
        repository.updatePluginStatus(PluginType.CALL_LOGGER.id, statusMsg)
        return true
    }

    suspend fun importRecentDeviceCalls(limit: Int = 10): Int {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return 0
        }
        var imported = 0
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    count++
                    val number = cursor.getString(numIdx) ?: ""
                    val cachedName = cursor.getString(nameIdx) ?: ""
                    val type = cursor.getInt(typeIdx)
                    val dateMs = cursor.getLong(dateIdx)
                    val durationSec = cursor.getLong(durIdx)

                    val direction = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallDirection.MISSED
                        else -> CallDirection.UNKNOWN
                    }
                    val ok = recordCall(
                        sourceApp = "Phone",
                        packageName = "com.android.dialer",
                        contactOrLabel = cachedName.ifBlank { number },
                        phoneNumber = number,
                        direction = direction,
                        startedAt = dateMs,
                        durationSeconds = durationSec,
                        notes = "Device CallLog sync",
                        forceCalendarWrite = true
                    )
                    if (ok) imported++
                }
            }
        }
        return imported
    }
}
