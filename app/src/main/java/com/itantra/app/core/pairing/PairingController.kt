package com.itantra.app.core.pairing

import android.content.Context
import com.itantra.app.core.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state

    private data class LiveLink(
        val id: String,
        var name: String,
        val transport: TransportKind,
        val framed: FramedConnection,
        var accepted: Boolean
    )

    private val links = ConcurrentHashMap<String, LiveLink>()
    private var pingJob: Job? = null
    private var hintJob: Job? = null

    private val bluetooth = BluetoothSppEngine(
        context = context,
        scope = scope,
        onDiscovered = { peer ->
            _state.update { current ->
                val next = (current.discovered + peer).distinctBy { it.id }
                current.copy(discovered = next, showSwitchToWait = false)
            }
        },
        onSocket = { id, name, framed -> onNewLink(id, name, TransportKind.BLUETOOTH, framed) },
        onError = { fail(it) }
    )

    private val wifi = WifiLinkEngine(
        context = context,
        scope = scope,
        onDiscovered = { peer ->
            _state.update { current ->
                val next = (current.discovered + peer).distinctBy { it.id }
                current.copy(discovered = next, showSwitchToWait = false)
            }
        },
        onSocket = { id, name, framed -> onNewLink(id, name, TransportKind.WIFI, framed) },
        onHotspotReady = { ssid, password ->
            _state.update {
                it.copy(
                    hotspotSsid = ssid,
                    hotspotPassword = password,
                    statusMessage = "Direct failed, using hotspot. Friend joins SSID $ssid"
                )
            }
        },
        onStatus = { message -> _state.update { it.copy(statusMessage = message) } },
        onError = { fail(it) }
    )

    init {
        scope.launch {
            val username = prefs.usernameOrDefault()
            val code = prefs.sessionCode()
            val last = prefs.lastPeer()
            _state.update {
                it.copy(
                    username = username,
                    sessionCode = code,
                    lastPeerId = last?.first,
                    lastPeerName = last?.second
                )
            }
        }
    }

    fun setTransport(kind: TransportKind) {
        if (_state.value.phase == PairingPhase.Connected) return
        stopSession(keepIdentity = true)
        _state.update {
            it.copy(
                transport = kind,
                role = null,
                phase = PairingPhase.Idle,
                statusMessage = "Pick Wait or Find. One phone waits, the other finds.",
                error = null
            )
        }
    }

    fun waitForTeammates(transport: TransportKind) {
        scope.launch {
            prepareIdentity()
            stopEngines()
            _state.update {
                it.copy(
                    transport = transport,
                    role = PairingRole.WAIT,
                    phase = PairingPhase.Waiting,
                    discovered = emptyList(),
                    incoming = null,
                    error = null,
                    showSwitchToFind = false,
                    showSwitchToWait = false,
                    hotspotSsid = null,
                    hotspotPassword = null,
                    visibleUntilEpochMs = if (transport == TransportKind.BLUETOOTH) {
                        System.currentTimeMillis() + PairingConstants.DISCOVERABLE_SECONDS * 1000L
                    } else null,
                    statusMessage = "Waiting for teammate…"
                )
            }
            if (transport == TransportKind.BLUETOOTH) {
                if (!bluetooth.isAvailable()) return@launch fail("No Bluetooth on this phone.")
                if (!bluetooth.isEnabled()) return@launch fail("Turn Bluetooth on, then tap Wait again.")
                bluetooth.setDiscoverableName(_state.value.username)
                bluetooth.startHost()
            } else {
                wifi.start(_state.value.username)
                wifi.startHost()
            }
            startWaitHint()
        }
    }

    fun findTeammates(transport: TransportKind) {
        scope.launch {
            prepareIdentity()
            stopEngines()
            _state.update {
                it.copy(
                    transport = transport,
                    role = PairingRole.FIND,
                    phase = PairingPhase.Scanning,
                    discovered = emptyList(),
                    incoming = null,
                    error = null,
                    showSwitchToWait = false,
                    showSwitchToFind = false,
                    statusMessage = "Scanning for iTantra phones…"
                )
            }
            if (transport == TransportKind.BLUETOOTH) {
                if (!bluetooth.isAvailable()) return@launch fail("No Bluetooth on this phone.")
                if (!bluetooth.isEnabled()) return@launch fail("Turn Bluetooth on, then tap Find again.")
                bluetooth.startScan()
            } else {
                wifi.start(_state.value.username)
                wifi.startScan()
            }
            startEmptyScanHint()
        }
    }

    fun connectTo(peer: DiscoveredPeer) {
        _state.update {
            it.copy(
                phase = PairingPhase.Connecting,
                statusMessage = "Waiting for ${peer.name} to accept…"
            )
        }
        if (peer.transport == TransportKind.BLUETOOTH) {
            bluetooth.connect(peer.id)
        } else {
            wifi.connect(peer.id, peer.name)
        }
    }

    fun connectHotspot(ssid: String, password: String) {
        _state.update {
            it.copy(
                phase = PairingPhase.Connecting,
                statusMessage = "Joining hotspot $ssid…"
            )
        }
        wifi.connectHotspot(ssid, password)
    }

    fun acceptIncoming() {
        val incoming = _state.value.incoming ?: return
        val link = links[incoming.connectionId] ?: return
        link.accepted = true
        link.framed.send(
            WireMessage(
                type = WireMessage.PAIR_ACCEPT,
                sessionId = _state.value.sessionCode,
                from = _state.value.username
            )
        )
        promoteAccepted(link)
        showNextIncoming()
    }

    fun rejectIncoming() {
        val incoming = _state.value.incoming ?: return
        val link = links.remove(incoming.connectionId)
        runCatching {
            link?.framed?.send(
                WireMessage(
                    type = WireMessage.PAIR_REJECT,
                    sessionId = _state.value.sessionCode,
                    from = _state.value.username
                )
            )
        }
        link?.framed?.closeQuietly()
        closeEngineSocket(incoming.connectionId, _state.value.transport)
        showNextIncoming()
    }

    fun disconnectAll() {
        stopSession(keepIdentity = true)
        _state.update {
            it.copy(
                role = null,
                phase = PairingPhase.Idle,
                peers = emptyList(),
                incoming = null,
                discovered = emptyList(),
                lastPing = null,
                hotspotSsid = null,
                hotspotPassword = null,
                statusMessage = "Disconnected. Pick Wait or Find to pair again."
            )
        }
    }

    fun refreshDiscoverableClock() {
        _state.update {
            it.copy(
                visibleUntilEpochMs = System.currentTimeMillis() +
                    PairingConstants.DISCOVERABLE_SECONDS * 1000L
            )
        }
    }

    private fun onNewLink(
        id: String,
        name: String,
        transport: TransportKind,
        framed: FramedConnection
    ) {
        val role = _state.value.role
        val link = LiveLink(id, name, transport, framed, accepted = false)
        links[id] = link
        scope.launch(Dispatchers.IO) {
            if (role == PairingRole.FIND) {
                framed.send(
                    WireMessage(
                        type = WireMessage.PAIR_REQUEST,
                        sessionId = _state.value.sessionCode,
                        from = _state.value.username
                    )
                )
            }
            try {
                framed.readLoop { message -> handleMessage(id, message) }
            } catch (_: Exception) {
            } finally {
                onLinkClosed(id)
            }
        }
        if (role == PairingRole.WAIT) {
            val request = IncomingRequest(id, name)
            _state.update {
                it.copy(
                    incoming = it.incoming ?: request,
                    phase = PairingPhase.IncomingRequest,
                    statusMessage = "$name wants to join. Accept or Reject."
                )
            }
        }
    }

    private fun handleMessage(id: String, message: WireMessage) {
        val link = links[id] ?: return
        when (message.type) {
            WireMessage.PAIR_REQUEST -> {
                val from = message.from?.ifBlank { null } ?: link.name
                link.name = from
                val request = IncomingRequest(id, from)
                _state.update {
                    it.copy(
                        incoming = it.incoming ?: request,
                        phase = PairingPhase.IncomingRequest,
                        statusMessage = "$from wants to join. Accept or Reject."
                    )
                }
            }
            WireMessage.PAIR_ACCEPT -> {
                link.accepted = true
                val from = message.from?.ifBlank { null } ?: link.name
                link.name = from
                promoteAccepted(link)
            }
            WireMessage.PAIR_REJECT -> {
                fail("Request rejected by ${message.from ?: "host"}.")
                disconnectAll()
            }
            WireMessage.PING -> {
                _state.update {
                    it.copy(lastPing = "${message.from ?: link.name}: ${message.body.orEmpty()}")
                }
            }
            WireMessage.TEXT -> {
                _state.update {
                    it.copy(lastPing = "${message.from ?: link.name}: ${message.body.orEmpty()}")
                }
            }
        }
    }

    private fun promoteAccepted(link: LiveLink) {
        val peer = ConnectedPeer(link.id, link.name, link.transport)
        _state.update { current ->
            current.copy(
                phase = PairingPhase.Connected,
                peers = (current.peers + peer).distinctBy { it.id },
                showSwitchToFind = false,
                showSwitchToWait = false,
                statusMessage = "Connected · ${link.name}",
                error = null
            )
        }
        scope.launch { prefs.saveLastPeer(link.id, link.name) }
        startPingLoop()
        runCatching {
            link.framed.send(
                WireMessage(
                    type = WireMessage.PING,
                    from = _state.value.username,
                    body = "hello from ${_state.value.username}"
                )
            )
        }
    }

    private fun showNextIncoming() {
        val next = links.values.firstOrNull { !it.accepted }
        if (next == null) {
            _state.update {
                val connected = it.peers.isNotEmpty()
                it.copy(
                    incoming = null,
                    phase = if (connected) PairingPhase.Connected else PairingPhase.Waiting,
                    statusMessage = if (connected) it.statusMessage else "Waiting for teammate…"
                )
            }
        } else {
            _state.update {
                it.copy(
                    incoming = IncomingRequest(next.id, next.name),
                    phase = PairingPhase.IncomingRequest,
                    statusMessage = "${next.name} wants to join. Accept or Reject."
                )
            }
        }
    }

    private fun onLinkClosed(id: String) {
        val removed = links.remove(id)
        removed?.framed?.closeQuietly()
        _state.update { current ->
            val peers = current.peers.filterNot { it.id == id }
            current.copy(
                peers = peers,
                incoming = if (current.incoming?.connectionId == id) null else current.incoming,
                phase = if (peers.isEmpty() && current.role == PairingRole.WAIT) {
                    PairingPhase.Waiting
                } else if (peers.isEmpty()) {
                    PairingPhase.Idle
                } else PairingPhase.Connected,
                statusMessage = if (peers.isEmpty()) "Host left — reconnect or pair again." else current.statusMessage
            )
        }
    }

    private fun startPingLoop() {
        if (pingJob?.isActive == true) return
        pingJob = scope.launch {
            while (isActive) {
                delay(PairingConstants.PING_INTERVAL_MS)
                val username = _state.value.username
                links.values.filter { it.accepted }.forEach { link ->
                    runCatching {
                        link.framed.send(
                            WireMessage(type = WireMessage.PING, from = username, body = "ping")
                        )
                    }
                }
            }
        }
    }

    private fun startEmptyScanHint() {
        hintJob?.cancel()
        hintJob = scope.launch {
            delay(PairingConstants.EMPTY_SCAN_HINT_MS)
            _state.update { current ->
                if (current.phase == PairingPhase.Scanning && current.discovered.isEmpty()) {
                    current.copy(showSwitchToWait = true)
                } else current
            }
        }
    }

    private fun startWaitHint() {
        hintJob?.cancel()
        hintJob = scope.launch {
            delay(PairingConstants.IDLE_WAIT_HINT_MS)
            _state.update { current ->
                if (current.phase == PairingPhase.Waiting && current.peers.isEmpty() && current.incoming == null) {
                    current.copy(showSwitchToFind = true)
                } else current
            }
        }
    }

    private suspend fun prepareIdentity() {
        val username = prefs.usernameOrDefault()
        val code = prefs.sessionCode()
        val last = prefs.lastPeer()
        _state.update {
            it.copy(
                username = username,
                sessionCode = code,
                lastPeerId = last?.first,
                lastPeerName = last?.second
            )
        }
    }

    private fun fail(reason: String) {
        _state.update {
            it.copy(
                phase = PairingPhase.Failed,
                error = reason,
                statusMessage = reason
            )
        }
    }

    private fun closeEngineSocket(id: String, transport: TransportKind) {
        if (transport == TransportKind.BLUETOOTH) bluetooth.closeSocket(id) else wifi.closeSocket(id)
    }

    private fun stopEngines() {
        pingJob?.cancel()
        hintJob?.cancel()
        links.values.forEach { it.framed.closeQuietly() }
        links.clear()
        bluetooth.shutdown()
        wifi.shutdown()
    }

    private fun stopSession(keepIdentity: Boolean) {
        stopEngines()
        val identity = _state.value
        if (keepIdentity) {
            _state.update {
                PairingUiState(
                    transport = identity.transport,
                    username = identity.username,
                    sessionCode = identity.sessionCode,
                    lastPeerId = identity.lastPeerId,
                    lastPeerName = identity.lastPeerName
                )
            }
        }
    }
}
