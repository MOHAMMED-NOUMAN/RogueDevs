package com.itantra.app.core.transport

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeoutOrNull

/** Largest packet [Transport.send] accepts. */
const val MAX_PACKET_SIZE = 240

enum class Priority(internal val code: Int) {
    NORMAL(0),
    ALERT(1),
    SOS(2);

    internal companion object {
        fun fromCode(code: Int): Priority? = entries.firstOrNull { it.code == code }
    }
}

enum class DeliveryStatus {
    /** Waiting in the outbox for a link. */
    QUEUED,

    /** On the wire, waiting for the peer's ACK. */
    SENT,

    /** The peer's transport ACKed it. Says nothing about whether it was played aloud. */
    DELIVERED,

    /** Sent 1 + [TransportConfig.maxResends] times on a live link with no ACK. */
    FAILED;

    val isFinal: Boolean get() = this == DELIVERED || this == FAILED
}

/** Handle for one [Transport.send] call. */
class Delivery internal constructor(val id: Int) {
    internal val mutableStatus = MutableStateFlow(DeliveryStatus.QUEUED)
    val status: StateFlow<DeliveryStatus> = mutableStatus.asStateFlow()

    /** Suspends until the message is [DeliveryStatus.DELIVERED] or [DeliveryStatus.FAILED]. */
    suspend fun awaitResult(): DeliveryStatus = status.first { it.isFinal }
}

sealed interface LinkStatus {
    data object Disconnected : LinkStatus
    data class Connected(val kind: LinkKind) : LinkStatus
}

data class TransportConfig(
    val ackTimeout: Duration = 1500.milliseconds,
    val maxResends: Int = 5,
    val heartbeatInterval: Duration = 2.seconds,
    /** No frame at all from the peer for this long means the link is dead (2 missed beats). */
    val deadAfter: Duration = 6.seconds,
    val connectTimeout: Duration = 15.seconds,
    val initialBackoff: Duration = 1.seconds,
    val maxBackoff: Duration = 16.seconds,
    /** Max DATA messages on the wire awaiting ACK at once. */
    val sendWindow: Int = 8,
    /** How many recent sequence numbers are remembered for dropping duplicates. */
    val dedupWindow: Int = 1024,
)

/**
 * The transport layer's only public entry point: [send] packets, collect [received].
 *
 * Connects through [connectors] in preference order (Wi-Fi Direct first, then RFCOMM),
 * ACKs and resends, drops duplicates, keeps unsent messages in an in-memory outbox while
 * no link is up, heartbeats, and reconnects with exponential back-off.
 *
 * Every piece of mutable state is touched only from coroutines on a single-threaded view of
 * [parentScope]'s dispatcher, so none of it needs locks. Nothing here blocks: links do their
 * blocking I/O on their own dispatcher.
 */
class Transport(
    private val connectors: List<LinkConnector>,
    parentScope: CoroutineScope,
    private val config: TransportConfig = TransportConfig(),
    private val epoch: Int = Random.nextInt(),
) {
    private val scope: CoroutineScope = run {
        val parent = parentScope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
            ?: Dispatchers.Default
        parentScope + parent.limitedParallelism(1)
    }

    private val nextSeq = AtomicInteger(1)
    private val outbox = Outbox()
    private val inFlight = HashMap<Int, Pending>()
    private var session: Session? = null
    private var runJob: Job? = null

    private var peerEpoch: Int? = null
    private val seen = SeqWindow(config.dedupWindow)
    private var lastPeerPttSeq = 0

    private var localTransmitting = false
    private var pendingPtt: Pending? = null

    private val _linkStatus = MutableStateFlow<LinkStatus>(LinkStatus.Disconnected)
    val linkStatus: StateFlow<LinkStatus> = _linkStatus.asStateFlow()

    private val _peerTransmitting = MutableStateFlow(false)

    /** True while the peer holds push-to-talk. Drops to false if the link is lost. */
    val peerTransmitting: StateFlow<Boolean> = _peerTransmitting.asStateFlow()

    private val inbox = Channel<ByteArray>(Channel.UNLIMITED)

    /** Packets from the peer, each exactly once, in arrival order. Collect from one place only. */
    val received: Flow<ByteArray> = inbox.receiveAsFlow()

    @Synchronized
    fun start() {
        if (runJob?.isActive == true) return
        runJob = scope.launch { runLoop() }
    }

    /** Drops the link and stops reconnecting. Queued messages stay queued for the next [start]. */
    @Synchronized
    fun stop() {
        runJob?.cancel()
        runJob = null
    }

    /**
     * Queues [packet] (1..[MAX_PACKET_SIZE] bytes, opaque to transport) and returns
     * immediately. Watch the returned [Delivery] for the outcome.
     */
    fun send(packet: ByteArray, priority: Priority): Delivery {
        require(packet.size in 1..MAX_PACKET_SIZE) {
            "packet must be 1..$MAX_PACKET_SIZE bytes, was ${packet.size}"
        }
        val seq = nextSeq.getAndIncrement()
        val message = OutgoingMessage(seq, priority, packet.copyOf(), Delivery(seq))
        scope.launch {
            outbox.add(message)
            pump()
        }
        return message.delivery
    }

    /**
     * Tells the peer whether we're holding push-to-talk. Sent with ACK and resend, but never
     * queued: while no link is up the state is simply sent when the next link comes up.
     */
    fun setTransmitting(transmitting: Boolean) {
        scope.launch {
            if (localTransmitting == transmitting) return@launch
            localTransmitting = transmitting
            session?.let { sendPtt(it) }
        }
    }

    private suspend fun runLoop() {
        var backoff = config.initialBackoff
        while (true) {
            val link = connectAny()
            if (link != null && runSession(link)) {
                // The link really worked, so reconnect straight away with a fresh back-off.
                backoff = config.initialBackoff
                continue
            }
            delay(backoff)
            backoff = minOf(backoff * 2, config.maxBackoff)
        }
    }

    private suspend fun connectAny(): Link? {
        // TODO(transport): once Wi-Fi Direct and RFCOMM can both listen (step 5), a listening
        //  phone must wait on both at once instead of on the first connector only.
        for (connector in connectors) {
            try {
                val link = if (connector.listens) {
                    connector.connect()
                } else {
                    withTimeoutOrNull(config.connectTimeout) { connector.connect() }
                }
                if (link != null) return link
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // This radio can't reach the peer right now; try the next one.
            }
        }
        return null
    }

    /** Runs one link until it dies. Returns true if the peer was heard on it at all. */
    private suspend fun runSession(link: Link): Boolean {
        var peerHeard = false
        val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
        try {
            coroutineScope {
                val current = Session(outgoing, this)
                session = current
                _linkStatus.value = LinkStatus.Connected(link.kind)

                // However the session ends, close the link at once so a blocked read or write
                // returns and this scope can finish.
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        link.close()
                    }
                }
                launch { for (frame in outgoing) link.send(frame) }
                launch {
                    while (true) {
                        delay(config.heartbeatInterval)
                        current.write(Frame.Heartbeat)
                    }
                }
                val heard = Channel<Unit>(Channel.CONFLATED)
                launch {
                    while (true) {
                        withTimeoutOrNull(config.deadAfter) { heard.receive() }
                            ?: throw IOException("nothing from peer for ${config.deadAfter}")
                    }
                }

                current.write(Frame.Hello(epoch))
                if (localTransmitting) sendPtt(current)
                pump()

                while (true) {
                    val bytes = link.receive()
                    peerHeard = true
                    heard.trySend(Unit)
                    FrameCodec.decode(bytes)?.let { handle(it, current) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The link died: I/O error, peer closed it, or heartbeat timeout.
        } finally {
            outgoing.close()
            link.close()
            session = null
            _linkStatus.value = LinkStatus.Disconnected
            _peerTransmitting.value = false
            requeueInFlight()
        }
        return peerHeard
    }

    private fun handle(frame: Frame, current: Session) {
        when (frame) {
            is Frame.Hello -> if (frame.epoch != peerEpoch) {
                // First contact, or the peer's app restarted and its sequence numbers began again.
                peerEpoch = frame.epoch
                seen.clear()
                lastPeerPttSeq = 0
            }
            is Frame.Data -> {
                current.write(Frame.Ack(frame.seq))
                if (seen.add(frame.seq)) inbox.trySend(frame.packet)
            }
            is Frame.Ack -> onAck(frame.seq)
            Frame.Heartbeat -> Unit
            is Frame.Ptt -> {
                current.write(Frame.Ack(frame.seq))
                if (frame.seq > lastPeerPttSeq) {
                    lastPeerPttSeq = frame.seq
                    _peerTransmitting.value = frame.transmitting
                }
            }
        }
    }

    /** Moves messages from the outbox onto the wire while the send window has room. */
    private fun pump() {
        val current = session ?: return
        while (inFlight.values.count { it.message != null } < config.sendWindow) {
            val message = outbox.poll() ?: return
            val frame = FrameCodec.encode(Frame.Data(message.seq, message.priority, message.packet))
            val pending = Pending(message.seq, frame, message)
            inFlight[message.seq] = pending
            message.delivery.mutableStatus.value = DeliveryStatus.SENT
            transmit(pending, current)
        }
    }

    private fun sendPtt(current: Session) {
        pendingPtt?.let { superseded ->
            superseded.timer?.cancel()
            inFlight.remove(superseded.seq)
        }
        val seq = nextSeq.getAndIncrement()
        val pending = Pending(seq, FrameCodec.encode(Frame.Ptt(seq, localTransmitting)), message = null)
        pendingPtt = pending
        inFlight[seq] = pending
        transmit(pending, current)
    }

    private fun transmit(pending: Pending, current: Session) {
        pending.attempts++
        current.outgoing.trySend(pending.frame)
        pending.timer = current.scope.launch {
            delay(config.ackTimeout)
            onAckTimeout(pending, current)
        }
    }

    private fun onAckTimeout(pending: Pending, current: Session) {
        if (inFlight[pending.seq] !== pending) return
        if (pending.attempts <= config.maxResends) {
            transmit(pending, current)
            return
        }
        inFlight.remove(pending.seq)
        if (pending === pendingPtt) pendingPtt = null
        pending.message?.let {
            it.delivery.mutableStatus.value = DeliveryStatus.FAILED
            pump()
        }
    }

    private fun onAck(seq: Int) {
        val pending = inFlight.remove(seq) ?: return
        pending.timer?.cancel()
        if (pending === pendingPtt) pendingPtt = null
        pending.message?.let {
            it.delivery.mutableStatus.value = DeliveryStatus.DELIVERED
            pump()
        }
    }

    /**
     * The link died with messages unACKed: put them back in the outbox. Their resend count
     * starts over on the next link, because a dead link is not the peer refusing them.
     * A pending PTT signal is dropped; the current state is re-sent on the next link.
     */
    private fun requeueInFlight() {
        for (pending in inFlight.values) {
            pending.timer?.cancel()
            pending.message?.let {
                it.delivery.mutableStatus.value = DeliveryStatus.QUEUED
                outbox.add(it)
            }
        }
        inFlight.clear()
        pendingPtt = null
    }

    private class Session(val outgoing: SendChannel<ByteArray>, val scope: CoroutineScope) {
        fun write(frame: Frame) {
            outgoing.trySend(FrameCodec.encode(frame))
        }
    }

    /** A DATA ([message] set) or PTT ([message] null) frame awaiting its ACK. */
    private class Pending(val seq: Int, val frame: ByteArray, val message: OutgoingMessage?) {
        var attempts = 0
        var timer: Job? = null
    }
}

/**
 * Remembers the last [capacity] sequence numbers received, to drop resent duplicates.
 * A duplicate only arises from resending a message whose ACK was lost, so it is recent; it
 * would have to arrive more than [capacity] messages later to slip through.
 */
internal class SeqWindow(private val capacity: Int) {
    private val seqs = LinkedHashSet<Int>()

    /** Returns true if [seq] hasn't been seen before. */
    fun add(seq: Int): Boolean {
        if (!seqs.add(seq)) return false
        if (seqs.size > capacity) seqs.remove(seqs.first())
        return true
    }

    fun clear() = seqs.clear()
}
