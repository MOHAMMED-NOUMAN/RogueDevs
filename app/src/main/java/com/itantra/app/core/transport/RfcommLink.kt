package com.itantra.app.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull

// Every Bluetooth call below needs BLUETOOTH_CONNECT (Android 12+), plus BLUETOOTH_SCAN for
// discovery. Callers are expected to have them granted; a missing permission surfaces as a
// SecurityException, which Transport treats like any other failed connect.

/** iTantra's RFCOMM service. Fixed for the app; both phones must use the same value. */
internal val RFCOMM_SERVICE_UUID: UUID = UUID.fromString("12d0d8e6-b08c-457c-bef6-d7056ddd7f8d")
private const val RFCOMM_SERVICE_NAME = "iTantra"
private const val PAIRING_SERVICE_NAME = "iTantra pairing"

/** A [Link] over an RFCOMM socket. */
internal class RfcommLink(socket: BluetoothSocket) :
    Link by StreamLink(LinkKind.RFCOMM, socket.inputStream, socket.outputStream, socket::close)

/** What this phone needs to reach its paired peer over Bluetooth. Store it after pairing. */
data class RfcommPeer(val address: String, val role: PeerRole)

/**
 * Opens RFCOMM links to the paired [peer]. Uses insecure RFCOMM (no Bluetooth bonding dialog);
 * the packets are already encrypted by the crypto layer.
 */
@SuppressLint("MissingPermission")
class RfcommConnector(context: Context, private val peer: RfcommPeer) : LinkConnector {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    override val kind = LinkKind.RFCOMM
    override val listens = peer.role == PeerRole.LISTEN

    override suspend fun connect(): Link {
        val adapter = enabledAdapter(adapter)
        return when (peer.role) {
            PeerRole.DIAL -> dial(adapter)
            PeerRole.LISTEN -> accept(adapter)
        }
    }

    private suspend fun dial(adapter: BluetoothAdapter): Link {
        adapter.cancelDiscovery() // discovery slows connects down badly
        val socket = adapter.getRemoteDevice(peer.address)
            .createInsecureRfcommSocketToServiceRecord(RFCOMM_SERVICE_UUID)
        try {
            cancellableBlocking(socket::close) { socket.connect() }
        } catch (e: Exception) {
            socket.close()
            throw e
        }
        return RfcommLink(socket)
    }

    private suspend fun accept(adapter: BluetoothAdapter): Link {
        val server = adapter.listenUsingInsecureRfcommWithServiceRecord(
            RFCOMM_SERVICE_NAME, RFCOMM_SERVICE_UUID,
        )
        try {
            while (true) {
                val socket = cancellableBlocking(server::close) { server.accept() }
                if (socket.remoteDevice.address.equals(peer.address, ignoreCase = true)) {
                    return RfcommLink(socket)
                }
                socket.close() // another phone running iTantra, not our peer
            }
        } finally {
            server.close()
        }
    }
}

/**
 * First-time Bluetooth pairing between two phones, driven by the pairing QR code.
 *
 * The phone showing the QR code calls [host]; the phone that scanned it calls [join] with the
 * same code. Both sides use a service UUID derived from the code, so the joiner can pick the
 * right phone out of everything nearby without bonding. Afterwards each side has the other's
 * address as an [RfcommPeer] to keep and hand to [RfcommConnector].
 *
 * Android doesn't let an app read its own Bluetooth address, which is why the address can't
 * simply go into the QR code and the joiner has to discover the host instead.
 */
@SuppressLint("MissingPermission")
class RfcommPairing(context: Context) {
    private val context = context.applicationContext
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /**
     * Waits until the joiner connects. The host must be discoverable: launch
     * [discoverableIntent] first (Android shows its "make visible" prompt).
     */
    suspend fun host(code: ByteArray): RfcommPeer {
        val server = enabledAdapter(adapter)
            .listenUsingInsecureRfcommWithServiceRecord(PAIRING_SERVICE_NAME, pairingUuid(code))
        try {
            val socket = cancellableBlocking(server::close) { server.accept() }
            try {
                cancellableBlocking(socket::close) { handshake(socket, initiator = false) }
                return RfcommPeer(socket.remoteDevice.address, PeerRole.LISTEN)
            } finally {
                socket.close()
            }
        } finally {
            server.close()
        }
    }

    /**
     * Discovers nearby phones (about 12 s) and tries them strongest signal first until one is
     * hosting [code]. Throws if none is.
     */
    suspend fun join(code: ByteArray): RfcommPeer {
        val adapter = enabledAdapter(adapter)
        val uuid = pairingUuid(code)
        val nearby = discoverPhones(adapter)
        for (device in nearby.phones) {
            val socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            try {
                cancellableBlocking(socket::close) {
                    socket.connect()
                    handshake(socket, initiator = true)
                }
                return RfcommPeer(device.address, PeerRole.DIAL)
            } catch (e: IOException) {
                // Not hosting this code (or out of reach). Try the next phone.
            } finally {
                socket.close()
            }
        }
        throw IOException(
            "no nearby phone is hosting this pairing code (saw ${nearby.devicesSeen} Bluetooth " +
                "devices, ${nearby.phones.size} of them phones)"
        )
    }

    private class Discovery(val phones: List<BluetoothDevice>, val devicesSeen: Int)

    private suspend fun discoverPhones(adapter: BluetoothAdapter): Discovery {
        val found = LinkedHashMap<String, Pair<BluetoothDevice, Short>>()
        val seen = HashSet<String>()
        // Discovery normally ends by itself after about 12 s; the timeout only guards against
        // ACTION_DISCOVERY_FINISHED never arriving.
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = intent.bluetoothDevice() ?: return
                            trySend(device to intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE))
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> channel.close()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            // EXPORTED: these broadcasts come from the Bluetooth app, not from this app, and a
            // NOT_EXPORTED receiver silently drops them. Both actions are protected broadcasts,
            // so no other app can send them.
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            if (!adapter.startDiscovery()) close(IOException("could not start Bluetooth discovery"))
            awaitClose {
                adapter.cancelDiscovery()
                context.unregisterReceiver(receiver)
            }
        }.collect { (device, rssi) ->
            seen += device.address
            if (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE) {
                found[device.address] = device to rssi
            }
        } }
        return Discovery(found.values.sortedByDescending { it.second }.map { it.first }, seen.size)
    }

    /** One byte each way, so both sides know the other really got the connection. */
    private fun handshake(socket: BluetoothSocket, initiator: Boolean) {
        if (initiator) socket.outputStream.apply { write(HANDSHAKE); flush() }
        if (socket.inputStream.read() != HANDSHAKE) throw IOException("pairing handshake failed")
        if (!initiator) socket.outputStream.apply { write(HANDSHAKE); flush() }
    }

    companion object {
        private const val HANDSHAKE = 0x49
        private const val DISCOVERY_TIMEOUT_MS = 20_000L

        /** Asks the user to make this phone visible to nearby phones, for [host]. */
        fun discoverableIntent(seconds: Int = 120): Intent =
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, seconds)

        internal fun pairingUuid(code: ByteArray): UUID =
            UUID.nameUUIDFromBytes("itantra-pairing:".encodeToByteArray() + code)
    }
}

private fun enabledAdapter(adapter: BluetoothAdapter?): BluetoothAdapter =
    adapter?.takeIf { it.isEnabled } ?: throw IOException("Bluetooth is off or not available")

private fun Intent.bluetoothDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
