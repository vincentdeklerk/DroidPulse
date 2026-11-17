package com.example.plugins.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.db.NetworkLogEntity
import com.example.data.model.PluginType
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkMonitorEngine(
    private val context: Context,
    private val repository: TelemetryRepository
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastTransportSnapshot: String? = null

    fun registerTransitionCallbacks(scope: CoroutineScope) {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch(Dispatchers.IO) {
                    val cfg = repository.getConfig(PluginType.NETWORK_MONITOR)
                    val params = TelemetryRepository.parseParams(cfg?.paramsJson ?: "{}")
                    if (cfg?.enabled == true && params.optBoolean("logTransitions", true)) {
                        captureSample(eventKind = "TRANSITION", forceIfChanged = true)
                    }
                }
            }

            override fun onLost(network: Network) {
                scope.launch(Dispatchers.IO) {
                    val cfg = repository.getConfig(PluginType.NETWORK_MONITOR)
                    val params = TelemetryRepository.parseParams(cfg?.paramsJson ?: "{}")
                    if (cfg?.enabled == true && params.optBoolean("logTransitions", true)) {
                        captureSample(eventKind = "TRANSITION", forceIfChanged = true)
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                scope.launch(Dispatchers.IO) {
                    val cfg = repository.getConfig(PluginType.NETWORK_MONITOR)
                    val params = TelemetryRepository.parseParams(cfg?.paramsJson ?: "{}")
                    if (cfg?.enabled == true && params.optBoolean("logTransitions", true)) {
                        captureSample(eventKind = "TRANSITION", forceIfChanged = true)
                    }
                }
            }
        }
        callback = cb
        runCatching { connectivityManager.registerDefaultNetworkCallback(cb) }
    }

    fun unregisterCallbacks() {
        callback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        callback = null
    }

    suspend fun captureSample(
        eventKind: String = "INTERVAL_SAMPLE",
        forceIfChanged: Boolean = false
    ): NetworkLogEntity {
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val transport = when {
            caps == null -> "NONE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }

        val networkName = resolveNetworkName(transport, caps)
        val isMetered = caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: false
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
        val downKbps = caps?.linkDownstreamBandwidthKbps ?: 0
        val upKbps = caps?.linkUpstreamBandwidthKbps ?: 0

        val snapshotKey = "$transport|$networkName|$isMetered"
        if (forceIfChanged && snapshotKey == lastTransportSnapshot) {
            return repository.getLatestNetworkLog() ?: NetworkLogEntity(
                timestamp = System.currentTimeMillis(),
                transportType = transport,
                networkName = networkName,
                isMetered = isMetered,
                isValidated = isValidated,
                linkDownKbps = downKbps,
                linkUpKbps = upKbps,
                eventKind = eventKind
            )
        }
        lastTransportSnapshot = snapshotKey

        val entity = NetworkLogEntity(
            timestamp = System.currentTimeMillis(),
            transportType = transport,
            networkName = networkName,
            isMetered = isMetered,
            isValidated = isValidated,
            linkDownKbps = downKbps,
            linkUpKbps = upKbps,
            eventKind = eventKind
        )
        repository.insertNetworkLog(entity)
        repository.updatePluginStatus(
            PluginType.NETWORK_MONITOR.id,
            "$transport ($networkName) • ${downKbps / 1000}Mbps down"
        )
        return entity
    }

    @Suppress("DEPRECATION")
    private fun resolveNetworkName(transport: String, caps: NetworkCapabilities?): String {
        return when (transport) {
            "NONE" -> "Offline"
            "WIFI" -> {
                val hasLoc = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                val ssidFromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (caps?.transportInfo as? WifiInfo)?.ssid?.trim('"')
                } else null

                val ssid = if (!ssidFromCaps.isNullOrBlank() && ssidFromCaps != "<unknown ssid>") {
                    ssidFromCaps
                } else if (hasLoc) {
                    val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    wifiMgr?.connectionInfo?.ssid?.trim('"')
                } else null

                if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") "Wi-Fi (Connected)" else ssid
            }
            "CELLULAR" -> {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "Mobile Data"
            }
            else -> transport
        }
    }
}
