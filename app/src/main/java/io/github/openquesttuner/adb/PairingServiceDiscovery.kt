package io.github.openquesttuner.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ADB TLS pairing endpoints advertised by Android Wireless debugging. */
data class PairingService(
    val name: String,
    val host: String,
    val port: Int,
) {
    val id: String get() = "$host:$port"
}

class PairingServiceDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _services = MutableStateFlow<List<PairingService>>(emptyList())
    val services: StateFlow<List<PairingService>> = _services.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null

    @Synchronized
    fun start() {
        stop()
        _services.value = emptyList()
        _scanning.value = true
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) { _scanning.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { _scanning.value = false; stop() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { _scanning.value = false }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _services.value = _services.value.filterNot { it.name == serviceInfo.serviceName }
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_adb-tls-pairing")) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val item = PairingService(info.serviceName.ifBlank { "Quest" }, host, info.port)
                        _services.value = (_services.value.filterNot { it.id == item.id } + item)
                            .sortedBy { it.name.lowercase() }
                    }
                })
            }
        }
        listener = l
        try { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
        catch (_: Exception) { _scanning.value = false; listener = null }
    }

    @Synchronized
    fun stop() {
        val current = listener ?: return
        listener = null
        try { nsd.stopServiceDiscovery(current) } catch (_: Exception) { }
        _scanning.value = false
    }

    private companion object { const val SERVICE_TYPE = "_adb-tls-pairing._tcp." }
}
