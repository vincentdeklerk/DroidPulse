package com.example.plugins.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TelemetryExporter(
    private val context: Context,
    private val repository: TelemetryRepository
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun exportFile(filename: String, content: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, filename)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun exportCallsCsv(): Uri = withContext(Dispatchers.IO) {
        val rows = repository.getCallLogsForExport()
        val sb = StringBuilder()
        sb.appendLine("id,timestamp_iso,source_app,package_name,contact_or_label,phone_number,direction,duration_sec,calendar_event_id,synced_to_calendar,notes")
        for (r in rows) {
            sb.appendLine(
                listOf(
                    r.id.toString(),
                    csvEscape(isoFormat.format(Date(r.startedAt))),
                    csvEscape(r.sourceApp),
                    csvEscape(r.packageName),
                    csvEscape(r.contactOrLabel),
                    csvEscape(r.phoneNumber),
                    csvEscape(r.direction),
                    r.durationSeconds.toString(),
                    r.calendarEventId?.toString() ?: "",
                    r.syncedToCalendar.toString(),
                    csvEscape(r.notes)
                ).joinToString(",")
            )
        }
        exportFile("droidpulse_calls_${System.currentTimeMillis()}.csv", sb.toString())
    }

    suspend fun exportNetworkCsv(): Uri = withContext(Dispatchers.IO) {
        val rows = repository.getNetworkLogsForExport()
        val sb = StringBuilder()
        sb.appendLine("id,timestamp_iso,transport_type,network_name,is_metered,is_validated,link_down_kbps,link_up_kbps,event_kind")
        for (r in rows) {
            sb.appendLine(
                listOf(
                    r.id.toString(),
                    csvEscape(isoFormat.format(Date(r.timestamp))),
                    csvEscape(r.transportType),
                    csvEscape(r.networkName),
                    r.isMetered.toString(),
                    r.isValidated.toString(),
                    r.linkDownKbps.toString(),
                    r.linkUpKbps.toString(),
                    csvEscape(r.eventKind)
                ).joinToString(",")
            )
        }
        exportFile("droidpulse_network_${System.currentTimeMillis()}.csv", sb.toString())
    }

    suspend fun exportGpsCsv(): Uri = withContext(Dispatchers.IO) {
        val rows = repository.getLocationPingsForExport()
        val sb = StringBuilder()
        sb.appendLine("id,timestamp_iso,latitude,longitude,altitude_m,accuracy_m,speed_mps,provider_source")
        for (r in rows) {
            sb.appendLine(
                listOf(
                    r.id.toString(),
                    csvEscape(isoFormat.format(Date(r.timestamp))),
                    r.latitude.toString(),
                    r.longitude.toString(),
                    r.altitude.toString(),
                    r.accuracyMeters.toString(),
                    r.speedMps.toString(),
                    csvEscape(r.providerSource)
                ).joinToString(",")
            )
        }
        exportFile("droidpulse_gps_${System.currentTimeMillis()}.csv", sb.toString())
    }

    suspend fun exportGpsGpx(): Uri = withContext(Dispatchers.IO) {
        val rows = repository.getLocationPingsForExport()
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="DroidPulse" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>DroidPulse Location History</name>")
        sb.appendLine("    <time>${isoFormat.format(Date())}</time>")
        sb.appendLine("  </metadata>")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>DroidPulse Telemetry Track</name>")
        sb.appendLine("    <trkseg>")
        for (r in rows) {
            sb.appendLine("""      <trkpt lat="${r.latitude}" lon="${r.longitude}">""")
            sb.appendLine("        <ele>${r.altitude}</ele>")
            sb.appendLine("        <time>${isoFormat.format(Date(r.timestamp))}</time>")
            sb.appendLine("        <cmt>${xmlEscape(r.providerSource)} (acc=${r.accuracyMeters}m, spd=${r.speedMps}m/s)</cmt>")
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        exportFile("droidpulse_track_${System.currentTimeMillis()}.gpx", sb.toString())
    }

    suspend fun exportWatchedFilesCsv(): Uri = withContext(Dispatchers.IO) {
        val rows = repository.getWatchedFilesForExport()
        val sb = StringBuilder()
        sb.appendLine("id,detected_iso,file_name,mime_type,size_bytes,folder_label,file_uri")
        for (r in rows) {
            sb.appendLine(
                listOf(
                    r.id.toString(),
                    csvEscape(isoFormat.format(Date(r.detectedAt))),
                    csvEscape(r.fileName),
                    csvEscape(r.mimeType),
                    r.sizeBytes.toString(),
                    csvEscape(r.folderLabel),
                    csvEscape(r.fileUri)
                ).joinToString(",")
            )
        }
        exportFile("droidpulse_files_${System.currentTimeMillis()}.csv", sb.toString())
    }

    private fun csvEscape(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun xmlEscape(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
