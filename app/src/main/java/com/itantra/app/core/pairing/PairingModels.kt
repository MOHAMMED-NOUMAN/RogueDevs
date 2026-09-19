package com.itantra.app.core.pairing

enum class TransportKind {
    BLUETOOTH,
    WIFI
}

enum class PairingRole {
    WAIT,
    FIND
}

enum class PairingPhase {
    Idle,
    Waiting,
    Scanning,
    Connecting,
    IncomingRequest,
    Connected,
    Failed
}

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val transport: TransportKind
)

data class IncomingRequest(
    val connectionId: String,
    val fromName: String
)

data class ConnectedPeer(
    val id: String,
    val name: String,
    val transport: TransportKind
)

data class PairingUiState(
    val transport: TransportKind = TransportKind.BLUETOOTH,
    val role: PairingRole? = null,
    val phase: PairingPhase = PairingPhase.Idle,
    val username: String = "",
    val sessionCode: String = "",
    val statusMessage: String = "Pick Wait or Find. One phone waits, the other finds.",
    val discovered: List<DiscoveredPeer> = emptyList(),
    val incoming: IncomingRequest? = null,
    val peers: List<ConnectedPeer> = emptyList(),
    val lastPing: String? = null,
    val hotspotSsid: String? = null,
    val hotspotPassword: String? = null,
    val visibleUntilEpochMs: Long? = null,
    val showSwitchToWait: Boolean = false,
    val showSwitchToFind: Boolean = false,
    val lastPeerId: String? = null,
    val lastPeerName: String? = null,
    val error: String? = null
)

object PairingConstants {
    const val NAME_PREFIX = "iTantra-"
    const val WIFI_PORT = 17331
    const val WIFI_DIRECT_TIMEOUT_MS = 10_000L
    const val EMPTY_SCAN_HINT_MS = 8_000L
    const val IDLE_WAIT_HINT_MS = 20_000L
    const val DISCOVERABLE_SECONDS = 120
    const val PING_INTERVAL_MS = 5_000L
    val SPP_UUID = java.util.UUID.fromString("c0e0d1a0-7a3b-4f2e-9c11-5b6e2f1a9d44")
}
