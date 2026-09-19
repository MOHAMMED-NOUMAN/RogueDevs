package com.itantra.app.core.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothSppEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDiscovered: (DiscoveredPeer) -> Unit,
    private val onSocket: (id: String, name: String, framed: FramedConnection) -> Unit,
    private val onError: (String) -> Unit
) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var scanReceiver: BroadcastReceiver? = null
    private val openSockets = ConcurrentHashMap<String, BluetoothSocket>()

    fun isAvailable(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun setDiscoverableName(username: String) {
        val adapter = adapter ?: return
        val target = (PairingConstants.NAME_PREFIX + username).take(20)
        runCatching { adapter.name = target }
    }

    @SuppressLint("MissingPermission")
    fun startHost() {
        val adapter = adapter ?: return onError("This phone has no Bluetooth.")
        stopHost()
        try {
            serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
                "iTantra",
                PairingConstants.SPP_UUID
            )
        } catch (e: Exception) {
            onError("Could not listen on Bluetooth: ${e.message}")
            return
        }
        acceptJob = scope.launch(Dispatchers.IO) {
            while (true) {
                val socket = try {
                    serverSocket?.accept() ?: break
                } catch (_: Exception) {
                    break
                }
                attachSocket(socket)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = adapter ?: return onError("This phone has no Bluetooth.")
        stopScan()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return
                val name = device.name ?: return
                if (!name.startsWith(PairingConstants.NAME_PREFIX) && !name.contains("iTantra", ignoreCase = true)) {
                    return
                }
                onDiscovered(
                    DiscoveredPeer(
                        id = device.address,
                        name = name,
                        transport = TransportKind.BLUETOOTH
                    )
                )
            }
        }
        scanReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        adapter.bondedDevices
            .filter { (it.name ?: "").startsWith(PairingConstants.NAME_PREFIX) || (it.name ?: "").contains("iTantra", true) }
            .forEach { device ->
                onDiscovered(
                    DiscoveredPeer(device.address, device.name ?: "iTantra", TransportKind.BLUETOOTH)
                )
            }
        runCatching {
            adapter.cancelDiscovery()
            adapter.startDiscovery()
        }.onFailure { onError("Bluetooth scan failed: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val adapter = adapter ?: return onError("This phone has no Bluetooth.")
        scope.launch(Dispatchers.IO) {
            try {
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address)
                val socket = device.createInsecureRfcommSocketToServiceRecord(PairingConstants.SPP_UUID)
                socket.connect()
                attachSocket(socket)
            } catch (e: Exception) {
                onError("Could not connect over Bluetooth: ${e.message}")
            }
        }
    }

    private fun attachSocket(socket: BluetoothSocket) {
        val id = UUID.randomUUID().toString()
        openSockets[id] = socket
        val name = runCatching { socket.remoteDevice.name }.getOrNull()
            ?: runCatching { socket.remoteDevice.address }.getOrNull()
            ?: "Teammate"
        val framed = FramedConnection(socket.inputStream, socket.outputStream)
        onSocket(id, name, framed)
    }

    fun closeSocket(id: String) {
        openSockets.remove(id)?.let { runCatching { it.close() } }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching { adapter?.cancelDiscovery() }
        scanReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        scanReceiver = null
    }

    fun stopHost() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun shutdown() {
        stopScan()
        stopHost()
        openSockets.keys.toList().forEach { closeSocket(it) }
    }
}
