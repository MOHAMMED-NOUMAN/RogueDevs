package com.itantra.app.core.transport

import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException

/**
 * Two in-memory ends of one link. Frames arrive in order with no delay unless a drop rule
 * says otherwise. Closing either end closes both, like a socket.
 */
class FakeLinkPair(kind: LinkKind = LinkKind.WIFI_DIRECT) {
    private val aToB = Channel<ByteArray>(Channel.UNLIMITED)
    private val bToA = Channel<ByteArray>(Channel.UNLIMITED)

    /** Return true to silently drop a frame going A -> B. */
    var dropAtoB: (ByteArray) -> Boolean = { false }
    var dropBtoA: (ByteArray) -> Boolean = { false }

    /** Every frame each end sent, including dropped ones. */
    val sentByA = mutableListOf<ByteArray>()
    val sentByB = mutableListOf<ByteArray>()

    val a: Link = End(kind, out = aToB, input = bToA, drop = { dropAtoB(it) }, log = sentByA)
    val b: Link = End(kind, out = bToA, input = aToB, drop = { dropBtoA(it) }, log = sentByB)

    /** Everything is dropped both ways but nothing is closed, like walking out of range. */
    fun goSilent() {
        dropAtoB = { true }
        dropBtoA = { true }
    }

    private inner class End(
        override val kind: LinkKind,
        private val out: Channel<ByteArray>,
        private val input: Channel<ByteArray>,
        private val drop: (ByteArray) -> Boolean,
        private val log: MutableList<ByteArray>,
    ) : Link {
        override suspend fun receive(): ByteArray = input.receive()

        override suspend fun send(frame: ByteArray) {
            log += frame
            if (drop(frame)) return
            try {
                out.send(frame.copyOf())
            } catch (e: ClosedSendChannelException) {
                throw IOException("closed", e)
            }
        }

        override fun close() {
            aToB.close()
            bToA.close()
        }
    }
}

/** Hands out links that a test [offer]s, and fails when it has none, like an unreachable peer. */
class FakeConnector(
    override val kind: LinkKind = LinkKind.WIFI_DIRECT,
    private val onAttempt: () -> Unit = {},
) : LinkConnector {
    private val available = ArrayDeque<Link>()

    fun offer(link: Link) {
        available.addLast(link)
    }

    override suspend fun connect(): Link {
        onAttempt()
        return available.removeFirstOrNull() ?: throw IOException("peer not reachable")
    }
}

internal fun List<ByteArray>.dataFrames(): List<Frame.Data> =
    mapNotNull { FrameCodec.decode(it) as? Frame.Data }
