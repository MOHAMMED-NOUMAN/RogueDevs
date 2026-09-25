package com.itantra.app.core.transport

/** Which radio a [Link] runs over. */
enum class LinkKind { WIFI_DIRECT, RFCOMM }

/**
 * Which side of the connection this phone takes, fixed at pairing and the same on every radio.
 */
enum class PeerRole {
    /** Showed the pairing QR code. Waits for the peer: Bluetooth server, Wi-Fi Direct group owner. */
    LISTEN,

    /** Scanned the pairing QR code. Reaches out to the peer. */
    DIAL,
}

/**
 * One live connection to the paired peer, carrying whole frames.
 *
 * A Link is single-use: once [receive] or [send] throws, or [close] is called, it is dead and
 * [Transport] asks a [LinkConnector] for a fresh one. Stream-based implementations (TCP over
 * Wi-Fi Direct, RFCOMM) do their own length-prefix framing so that each [receive] returns
 * exactly one frame that the other side passed to [send].
 *
 * [Transport] calls [send] from one coroutine and [receive] from one coroutine, so
 * implementations don't need to guard against concurrent sends or concurrent receives.
 *
 * A Link always talks to exactly one peer. Supporting more than two phones later means
 * [Transport] keeping one Link per peer; this interface does not need to change for that.
 */
interface Link {
    val kind: LinkKind

    /** Suspends until the next whole frame arrives. Throws when the connection is gone. */
    suspend fun receive(): ByteArray

    /** Writes one whole frame. Throws when the connection is gone. */
    suspend fun send(frame: ByteArray)

    /**
     * Closes the connection. Idempotent and safe from any thread. Must make a pending
     * [receive] or [send] return (by throwing) promptly, e.g. by closing the socket.
     */
    fun close()
}

/** Opens [Link]s over one radio. [Transport] tries its connectors in preference order. */
interface LinkConnector {
    val kind: LinkKind

    /**
     * True if [connect] waits for the peer to dial in rather than dialling out. [Transport]
     * then lets it wait as long as it takes instead of applying [TransportConfig.connectTimeout].
     */
    val listens: Boolean get() = false

    /**
     * Connects to the paired peer, or throws if this radio can't reach it right now.
     * Must be cancellable: [Transport] gives up on dialling after [TransportConfig.connectTimeout].
     */
    suspend fun connect(): Link

    /**
     * Called when [Transport] stops. Tears down anything kept up between links, such as a
     * Wi-Fi Direct group. Must not block.
     */
    fun release() {}
}
