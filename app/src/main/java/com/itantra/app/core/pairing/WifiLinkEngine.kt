package com.itantra.app.core.pairing

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WifiLinkEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDiscovered: (DiscoveredPeer) -> Unit,
    private val onSocket: (id: String, name: String, framed: FramedConnection) -> Unit,
    private val onHotspotReady: (ssid: String, password: String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val p2p: WifiP2pManager? = context.getSystemService(WifiP2pManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var boundNetwork: Network? = null
    private val openSockets = ConcurrentHashMap<String, Socket>()
    private var desiredName: String = "iTantra"
    private var connectingTo: String? = null

    fun start(username: String) {
        desiredName = PairingConstants.NAME_PREFIX + username
        val p2p = p2p ?: return onError("Wi-Fi Direct is not available on this phone.")
        if (channel == null) {
            channel = p2p.initialize(context, Looper.getMainLooper()) { }
        }
        setP2pDeviceName(desiredName)
        registerReceiver()
    }

    @SuppressLint("MissingPermission")
    fun startHost() {
        val p2p = p2p
        val channel = channel
        if (p2p == null || channel == null) {
            startHotspotFallback("Wi-Fi Direct missing")
            return
        }
        onStatus("Starting Wi-Fi Direct group…")
        p2p.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatus("Wi-Fi Direct group created. Waiting for teammate…")
                scope.launch {
                    delay(PairingConstants.WIFI_DIRECT_TIMEOUT_MS)
                    if (serverSocket == null && hotspotReservation == null) {
                        startHotspotFallback("Wi-Fi Direct did not get an IP")
                    }
                }
            }

            override fun onFailure(reason: Int) {
                startHotspotFallback("Wi-Fi Direct failed (code $reason)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val p2p = p2p
        val channel = channel
        if (p2p == null || channel == null) {
            onError("Wi-Fi Direct is not available. Use hotspot fields below.")
            return
        }
        p2p.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatus("Scanning for Wi-Fi Direct teammates…")
            }

            override fun onFailure(reason: Int) {
                onStatus("Wi-Fi Direct scan failed. You can join the host hotspot instead.")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String, displayName: String) {
        val p2p = p2p
        val channel = channel
        if (p2p == null || channel == null) {
            onError("Wi-Fi Direct is not available.")
            return
        }
        connectingTo = displayName
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            groupOwnerIntent = 0
        }
        p2p.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatus("Connecting to $displayName…")
            }

            override fun onFailure(reason: Int) {
                onError("Wi-Fi Direct connect failed (code $reason). Try hotspot.")
            }
        })
    }

    fun connectHotspot(ssid: String, password: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onError("Hotspot join from the app needs Android 10+. Open Wi-Fi settings and join $ssid.")
            return
        }
        onStatus("Joining hotspot $ssid…")
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid.trim())
            .setWpa2Passphrase(password.trim())
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                boundNetwork = network
                connectivityManager.bindProcessToNetwork(network)
                scope.launch(Dispatchers.IO) {
                    delay(800)
                    val gateway = gatewayAddress()
                    if (gateway == null) {
                        onError("Joined hotspot but could not find the host IP.")
                        return@launch
                    }
                    try {
                        val socket = Socket()
                        network.bindSocket(socket)
                        socket.connect(InetSocketAddress(gateway, PairingConstants.WIFI_PORT), 8_000)
                        attachSocket(socket, "Hotspot host")
                    } catch (e: Exception) {
                        onError("Hotspot joined but TCP failed: ${e.message}")
                    }
                }
            }

            override fun onUnavailable() {
                onError("Could not join hotspot. Check SSID and password on the host screen.")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startHotspotFallback(reason: String) {
        onStatus("$reason. Switching to local hotspot…")
        runCatching { p2p?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        }) }
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    hotspotReservation = reservation
                    val ssid: String
                    val password: String
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val conf = reservation.softApConfiguration
                        ssid = conf.ssid.orEmpty()
                        password = conf.passphrase.orEmpty()
                    } else {
                        @Suppress("DEPRECATION")
                        val conf = reservation.wifiConfiguration
                        ssid = conf?.SSID.orEmpty().trim('"')
                        password = conf?.preSharedKey.orEmpty().trim('"')
                    }
                    onHotspotReady(ssid, password)
                    startServer()
                }

                override fun onFailed(reason: Int) {
                    onError("Could not start hotspot (code $reason). Try Bluetooth.")
                }
            }, mainHandler)
        } catch (e: Exception) {
            onError("Hotspot error: ${e.message}")
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val p2p = p2p ?: return
        val channel = channel ?: return
        p2p.requestPeers(channel) { list ->
            list.deviceList.forEach { device ->
                val name = device.deviceName.orEmpty()
                if (name.startsWith(PairingConstants.NAME_PREFIX) ||
                    name.contains("iTantra", true) ||
                    device.status == WifiP2pDevice.AVAILABLE
                ) {
                    val shown = if (name.isBlank()) device.deviceAddress else name
                    onDiscovered(
                        DiscoveredPeer(device.deviceAddress, shown, TransportKind.WIFI)
                    )
                }
            }
        }
    }

    private fun requestConnectionInfo() {
        val p2p = p2p ?: return
        val channel = channel ?: return
        p2p.requestConnectionInfo(channel) { info ->
            handleP2pInfo(info)
        }
    }

    private fun handleP2pInfo(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return
        if (info.isGroupOwner) {
            startServer()
        } else {
            val host = info.groupOwnerAddress?.hostAddress ?: return
            scope.launch(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, PairingConstants.WIFI_PORT), 8_000)
                    attachSocket(socket, connectingTo ?: "Wi-Fi host")
                } catch (e: Exception) {
                    onError("Wi-Fi Direct TCP failed: ${e.message}")
                }
            }
        }
    }

    private fun startServer() {
        if (serverSocket != null) return
        try {
            serverSocket = ServerSocket(PairingConstants.WIFI_PORT)
        } catch (e: Exception) {
            onError("Could not open Wi-Fi port: ${e.message}")
            return
        }
        acceptJob = scope.launch(Dispatchers.IO) {
            while (true) {
                val socket = try {
                    serverSocket?.accept() ?: break
                } catch (_: Exception) {
                    break
                }
                attachSocket(socket, "Wi-Fi teammate")
            }
        }
    }

    private fun attachSocket(socket: Socket, fallbackName: String) {
        val id = UUID.randomUUID().toString()
        openSockets[id] = socket
        val framed = FramedConnection(socket.getInputStream(), socket.getOutputStream())
        onSocket(id, fallbackName, framed)
    }

    private fun gatewayAddress(): String? {
        @Suppress("DEPRECATION")
        val dhcp = wifiManager.dhcpInfo ?: return null
        val ip = dhcp.gateway
        if (ip == 0) return "192.168.49.1"
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    private fun setP2pDeviceName(name: String) {
        val p2p = p2p ?: return
        val channel = channel ?: return
        runCatching {
            val method = WifiP2pManager::class.java.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method.invoke(p2p, channel, name.take(22), object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {}
            })
        }
    }

    fun closeSocket(id: String) {
        openSockets.remove(id)?.let { runCatching { it.close() } }
    }

    fun shutdown() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        openSockets.keys.toList().forEach { closeSocket(it) }
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        runCatching { p2p?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        }) }
        runCatching { hotspotReservation?.close() }
        hotspotReservation = null
        connectivityManager.bindProcessToNetwork(null)
        boundNetwork = null
    }
}
