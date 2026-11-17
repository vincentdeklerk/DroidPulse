package com.example.plugins.gps

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.db.LocationPingEntity
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class GpsTrackerEngine(
    private val context: Context,
    private val repository: TelemetryRepository
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var passiveListener: LocationListener? = null
    private var activeCallback: LocationCallback? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    @SuppressLint("MissingPermission")
    fun startTracking(scope: CoroutineScope, intervalSeconds: Int, passiveOnly: Boolean) {
        stopTracking()
        if (!hasLocationPermission()) return

        // Always hook PASSIVE_PROVIDER to pick up location fixes triggered by Play Services / Maps / Banking apps
        val passive = LocationListener { loc ->
            scope.launch(Dispatchers.IO) {
                saveLocation(loc, "PASSIVE_APP_FIX")
            }
        }
        passiveListener = passive
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                5_000L,
                0f,
                passive,
                Looper.getMainLooper()
            )
        }

        // Also register PendingIntent so passive updates wake receiver if running in passive mode
        registerPassivePendingIntent()

        if (!passiveOnly) {
            val intervalMs = (intervalSeconds.coerceAtLeast(15)) * 1000L
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(10_000L))
                .build()
            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    scope.launch(Dispatchers.IO) {
                        saveLocation(loc, "FUSED_ACTIVE")
                    }
                }
            }
            activeCallback = cb
            runCatching {
                fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun registerPassivePendingIntent() {
        if (!hasLocationPermission()) return
        val pi = createPassivePendingIntent()
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                10_000L,
                5f,
                pi
            )
        }
    }

    fun unregisterPassivePendingIntent() {
        runCatching { locationManager.removeUpdates(createPassivePendingIntent()) }
    }

    private fun createPassivePendingIntent(): PendingIntent {
        val intent = Intent(context, PassiveLocationReceiver::class.java).apply {
            action = PassiveLocationReceiver.ACTION_PASSIVE_LOCATION
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 4041, intent, flags)
    }

    fun stopTracking() {
        passiveListener?.let { runCatching { locationManager.removeUpdates(it) } }
        passiveListener = null
        activeCallback?.let { runCatching { fusedClient.removeLocationUpdates(it) } }
        activeCallback = null
    }

    @SuppressLint("MissingPermission")
    suspend fun captureSinglePing(sourceTag: String = "MANUAL_OR_WORK"): LocationPingEntity? {
        if (!hasLocationPermission()) return null
        val loc = suspendCancellableCoroutine<Location?> { cont ->
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { current ->
                    if (current != null) {
                        cont.resume(current)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    runCatching {
                        val fallback = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                        cont.resume(fallback)
                    }.onFailure { cont.resume(null) }
                }
        } ?: return null

        return saveLocation(loc, sourceTag)
    }

    suspend fun saveLocation(loc: Location, sourceTag: String): LocationPingEntity {
        val ping = LocationPingEntity(
            timestamp = if (loc.time > 0L) loc.time else System.currentTimeMillis(),
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = loc.altitude,
            accuracyMeters = loc.accuracy,
            speedMps = loc.speed,
            providerSource = "$sourceTag (${loc.provider ?: "fused"})"
        )
        repository.insertLocationPing(ping)
        val coordSummary = String.format(
            Locale.US,
            "%.4f, %.4f (±%.0fm)",
            ping.latitude,
            ping.longitude,
            ping.accuracyMeters
        )
        repository.updatePluginStatus(PluginType.GPS_LOGGER.id, coordSummary)
        return ping
    }
}
