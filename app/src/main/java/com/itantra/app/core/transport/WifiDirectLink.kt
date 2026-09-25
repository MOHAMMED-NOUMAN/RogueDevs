package com.itantra.app.core.transport

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

// Wi-Fi Direct calls need NEARBY_WIFI_DEVICES (Android 13+) or ACCESS_FINE_LOCATION
// (Android 10-12), and Wi-Fi switched on. A missing permission or Wi-Fi being off makes
// connect() throw, and Transport falls back to Bluetooth.

/** TCP port the group owner listens on inside the Wi-Fi Direct group. */
private const val WIFI_DIRECT_PORT = 47120
private const val DIAL_TIMEOUT_MS = 5_000

/**
 * The private Wi-Fi Direct group two paired phones share. Both derive the same name and
 * passphrase from the pairing code, so the dialling phone can join the listening phone's
 * group directly: no peer discovery, and no "accept invitation" dialog.
 */
data class WifiDirectPeer(val networkName: String, val passphrase: String, val role: PeerRole) {
    companion object {
        fun fromPairingCode(code: ByteArray, role: PeerRole): WifiDirectPeer {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest("itantra-wifi-direct:".encodeToByteArray() + code)
            // Android requires group names of the form "DIRECT-xy..." (xy: two characters).
            val name = "DIRECT-${hash.hex(0, 1)}-iT${hash.hex(1, 5)}"
            return WifiDirectPeer(name, passphrase = hash.hex(5, 17), role = role)
        }

        private fun ByteArray.hex(from: Int, to: Int) =
            copyOfRange(from, to).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Opens TCP links over Wi-Fi Direct to the paired [peer]. The LISTEN phone owns the group and
 * accepts; the DIAL phone joins the group and connects to the owner. Needs Android 10+,
 * because joining a group by name and passphrase is not available before that.
 */
@SuppressLint("MissingPermission")
class WifiDirectConnector(context: Context, private val peer: WifiDirectPeer) : LinkConnector {
    private val context = context.applicationContext
    private val manager: WifiP2pManager? = context.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null

    override val kind = LinkKind.WIFI_DIRECT
    override val listens = peer.role == PeerRole.LISTEN

    override suspend fun connect(): Link {
        if (Build.VERSION.SDK_INT < 29) throw IOException("Wi-Fi Direct link needs Android 10 or newer")
        val manager = manager ?: throw IOException("this phone has no Wi-Fi Direct")
        val channel = channel(manager)
        return if (listens) hostAndAccept(manager, channel) else joinAndDial(manager, channel)
    }

    override fun release() {
        val manager = manager ?: return
        val channel = synchronized(this) { channel.also { channel = null } } ?: return
        if (listens) manager.removeGroup(channel, null) else manager.cancelConnect(channel, null)
        if (Build.VERSION.SDK_INT >= 27) channel.close()
    }

    @RequiresApi(29)
    private suspend fun hostAndAccept(manager: WifiP2pManager, channel: WifiP2pManager.Channel): Link {
        val info = manager.connectionInfo(channel)
        val ownsOurGroup = info?.groupFormed == true && info.isGroupOwner &&
            manager.groupInfo(channel)?.networkName == peer.networkName
        if (!ownsOurGroup) {
            if (info?.groupFormed == true) manager.await { removeGroup(channel, it) }
            manager.await { createGroup(channel, groupConfig(), it) }
        }
        val owner = waitForGroup(manager, channel) { it.isGroupOwner }

        // Bound to the group owner address so only phones inside the Wi-Fi Direct group can reach it.
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(owner.groupOwnerAddress, WIFI_DIRECT_PORT))
            val socket = cancellableBlocking(server::close) { server.accept() }
            return socketLink(socket)
        } finally {
            server.close()
        }
    }

    @RequiresApi(29)
    private suspend fun joinAndDial(manager: WifiP2pManager, channel: WifiP2pManager.Channel): Link {
        val info = manager.connectionInfo(channel)
        val inOurGroup = info?.groupFormed == true && !info.isGroupOwner &&
            manager.groupInfo(channel)?.networkName == peer.networkName
        val joined = if (inOurGroup) {
            info!!
        } else {
            if (info?.groupFormed == true) manager.await { removeGroup(channel, it) }
            manager.await { connect(channel, groupConfig(), it) }
            try {
                waitForGroup(manager, channel) { !it.isGroupOwner }
            } catch (e: CancellationException) {
                manager.cancelConnect(channel, null) // gave up: stop the phone searching for the group
                throw e
            }
        }

        val socket = Socket()
        try {
            cancellableBlocking(socket::close) {
                socket.connect(InetSocketAddress(joined.groupOwnerAddress, WIFI_DIRECT_PORT), DIAL_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            socket.close()
            throw e
        }
        return socketLink(socket)
    }

    @RequiresApi(29)
    private fun groupConfig(): WifiP2pConfig = WifiP2pConfig.Builder()
        .setNetworkName(peer.networkName)
        .setPassphrase(peer.passphrase)
        .build()

    /** Polls until a group has formed that [matches]. The caller's timeout bounds the wait. */
    private suspend fun waitForGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        matches: (WifiP2pInfo) -> Boolean,
    ): WifiP2pInfo {
        while (true) {
            val info = manager.connectionInfo(channel)
            if (info != null && info.groupFormed && info.groupOwnerAddress != null && matches(info)) return info
            delay(500)
        }
    }

    @Synchronized
    private fun channel(manager: WifiP2pManager): WifiP2pManager.Channel =
        channel ?: (manager.initialize(context, Looper.getMainLooper(), null)
            ?: throw IOException("Wi-Fi Direct is not available")).also { channel = it }

    private fun socketLink(socket: Socket): Link {
        socket.tcpNoDelay = true // packets are tiny; send each one immediately
        return StreamLink(LinkKind.WIFI_DIRECT, socket.getInputStream(), socket.getOutputStream(), socket::close)
    }
}

/** Runs one WifiP2pManager request (called on the manager itself) and waits for its result. */
private suspend fun WifiP2pManager.await(call: WifiP2pManager.(WifiP2pManager.ActionListener) -> Unit) =
    suspendCancellableCoroutine { continuation ->
        call(object : WifiP2pManager.ActionListener {
            override fun onSuccess() = continuation.resume(Unit)

            override fun onFailure(reason: Int) = continuation.resumeWithException(
                IOException("Wi-Fi Direct request failed: ${failureName(reason)}")
            )
        })
    }

private suspend fun WifiP2pManager.connectionInfo(channel: WifiP2pManager.Channel): WifiP2pInfo? =
    suspendCancellableCoroutine { continuation ->
        requestConnectionInfo(channel) { continuation.resume(it) }
    }

@SuppressLint("MissingPermission")
private suspend fun WifiP2pManager.groupInfo(channel: WifiP2pManager.Channel): WifiP2pGroup? =
    suspendCancellableCoroutine { continuation ->
        requestGroupInfo(channel) { continuation.resume(it) }
    }

private fun failureName(reason: Int) = when (reason) {
    WifiP2pManager.P2P_UNSUPPORTED -> "not supported"
    WifiP2pManager.BUSY -> "busy (is Wi-Fi on?)"
    WifiP2pManager.ERROR -> "internal error"
    else -> "code $reason"
}
