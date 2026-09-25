package com.itantra.app.core.transport

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where this phone is in pairing with its one teammate. */
sealed interface PairingState {
    data object Unpaired : PairingState

    /** Visible over Bluetooth and waiting for a teammate to join with [code]. */
    data class Hosting(val code: String) : PairingState

    /** Looking for the phone that is hosting [code]. */
    data class Joining(val code: String) : PairingState

    data class Paired(val peer: RfcommPeer) : PairingState
}

/**
 * The app's single owner of pairing and the live [Transport] link. Screens read [pairing],
 * [linkStatus] and [linkRunning]; nothing else in the app creates a Transport.
 *
 * The pairing (peer address, role and code) is kept in SharedPreferences, so a paired phone
 * reconnects after the app restarts once [startLink] is called.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LinkManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("link", Context.MODE_PRIVATE)
    private var pairingJob: Job? = null
    private var pairingCode: String? = null

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Unpaired)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    /** Last pairing failure, shown until the next attempt. */
    private val _pairingError = MutableStateFlow<String?>(null)
    val pairingError: StateFlow<String?> = _pairingError.asStateFlow()

    private val transport = MutableStateFlow<Transport?>(null)
    private var receiveJob: Job? = null

    /** Packets from the teammate, across link restarts. Collect from one place only. */
    private val inbox = Channel<ByteArray>(Channel.UNLIMITED)
    val received: Flow<ByteArray> = inbox.receiveAsFlow()

    /** True between [startLink] and [stopLink]: the link is up or being (re)connected. */
    val linkRunning: StateFlow<Boolean> = transport
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val linkStatus: StateFlow<LinkStatus> = transport
        .flatMapLatest { it?.linkStatus ?: flowOf(LinkStatus.Disconnected) }
        .stateIn(scope, SharingStarted.Eagerly, LinkStatus.Disconnected)

    init {
        pairingCode = prefs.getString(KEY_CODE, null)
        _pairing.value = savedPairing()
    }

    /**
     * Waits for a teammate to join with [code]. The phone must already be discoverable
     * (see [RfcommPairing.discoverableIntent]).
     */
    fun host(code: String) = pair(PairingState.Hosting(code), code) {
        RfcommPairing(context).host(code.encodeToByteArray())
    }

    /** Finds the nearby phone hosting [code] and pairs with it. */
    fun join(code: String) = pair(PairingState.Joining(code), code) {
        RfcommPairing(context).join(code.encodeToByteArray())
    }

    private fun pair(attempt: PairingState, code: String, block: suspend () -> RfcommPeer) {
        stopLink()
        pairingJob?.cancel()
        _pairingError.value = null
        _pairing.value = attempt
        pairingJob = scope.launch {
            try {
                val peer = block()
                prefs.edit()
                    .putString(KEY_ADDRESS, peer.address)
                    .putString(KEY_ROLE, peer.role.name)
                    .putString(KEY_CODE, code)
                    .apply()
                pairingCode = code
                _pairing.value = PairingState.Paired(peer)
                startLink()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pairingError.value = e.message ?: e.toString()
                _pairing.value = savedPairing()
            }
        }
    }

    /** Stops hosting or joining; an earlier pairing, if any, is kept. */
    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _pairing.value = savedPairing()
    }

    /** Drops the link and deletes the pairing. */
    fun forget() {
        cancelPairing()
        stopLink()
        prefs.edit().clear().apply()
        pairingCode = null
        _pairing.value = PairingState.Unpaired
    }

    /**
     * Starts the link to the paired teammate (Wi-Fi Direct first, Bluetooth fallback) if it
     * isn't running. Does nothing when unpaired. Call while the app is on screen: it starts a
     * foreground service.
     */
    @Synchronized
    fun startLink() {
        val peer = (_pairing.value as? PairingState.Paired)?.peer ?: return
        val code = pairingCode ?: return
        if (transport.value != null) return
        val connectors = listOf(
            WifiDirectConnector(context, WifiDirectPeer.fromPairingCode(code.encodeToByteArray(), peer.role)),
            RfcommConnector(context, peer),
        )
        val started = Transport(connectors, scope)
        TransportService.start(context)
        receiveJob = scope.launch { started.received.collect { inbox.send(it) } }
        started.start()
        transport.value = started
    }

    @Synchronized
    fun stopLink() {
        transport.value?.stop() ?: return
        receiveJob?.cancel()
        receiveJob = null
        transport.value = null
        TransportService.stop(context)
    }

    /**
     * Queues [packet] for the teammate (see [Transport.send]). Null when the link isn't running,
     * i.e. not paired or switched off; while it runs but is reconnecting, the packet waits.
     */
    fun send(packet: ByteArray, priority: Priority): Delivery? = transport.value?.send(packet, priority)

    private fun savedPairing(): PairingState {
        val address = prefs.getString(KEY_ADDRESS, null)
        val role = prefs.getString(KEY_ROLE, null)?.let { runCatching { PeerRole.valueOf(it) }.getOrNull() }
        return if (address != null && role != null && pairingCode != null) {
            PairingState.Paired(RfcommPeer(address, role))
        } else PairingState.Unpaired
    }

    companion object {
        private const val KEY_ADDRESS = "address"
        private const val KEY_ROLE = "role"
        private const val KEY_CODE = "code"

        /** A fresh 4-digit code for hosting a pairing. */
        fun newPairingCode(): String = "%04d".format(Random.nextInt(10_000))
    }
}
