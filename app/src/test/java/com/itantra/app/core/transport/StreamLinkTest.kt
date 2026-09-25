package com.itantra.app.core.transport

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real blocking sockets and real time, over TCP loopback. */
class StreamLinkTest {

    private fun socketPair(): Pair<Socket, Socket> =
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val client = Socket(InetAddress.getLoopbackAddress(), server.localPort)
            client to server.accept()
        }

    private fun link(socket: Socket) =
        StreamLink(LinkKind.WIFI_DIRECT, socket.getInputStream(), socket.getOutputStream(), socket::close)

    @Test
    fun `frames keep their boundaries over a real socket`() = runBlocking {
        val (x, y) = socketPair()
        val a = link(x)
        val b = link(y)
        val frames = listOf(
            byteArrayOf(1),
            ByteArray(FrameCodec.MAX_FRAME_SIZE) { it.toByte() },
            byteArrayOf(2, 3),
        )

        frames.forEach { a.send(it) }

        frames.forEach { assertArrayEquals(it, withTimeout(5_000) { b.receive() }) }
        a.close()
        b.close()
    }

    @Test
    fun `close unblocks a pending receive`() = runBlocking {
        val (x, y) = socketPair()
        val b = link(y)
        val pending = async(Dispatchers.Default) { runCatching { b.receive() } }
        delay(200)

        b.close()

        assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
        x.close()
    }

    @Test
    fun `receive fails when the peer hangs up`() = runBlocking {
        val (x, y) = socketPair()
        val b = link(y)

        x.close()

        assertTrue(runCatching { withTimeout(5_000) { b.receive() } }.exceptionOrNull() is IOException)
        b.close()
    }

    @Test
    fun `a corrupt length kills the link`() = runBlocking {
        val (x, y) = socketPair()
        val b = link(y)

        x.getOutputStream().apply { write(byteArrayOf(0, 0)); flush() } // length 0

        assertTrue(runCatching { withTimeout(5_000) { b.receive() } }.exceptionOrNull() is IOException)
        x.close()
        b.close()
    }

    @Test
    fun `cancelling a blocked accept closes the socket and returns`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val job = launch(Dispatchers.Default) { cancellableBlocking(server::close) { server.accept() } }
        delay(200)

        withTimeout(5_000) {
            job.cancel()
            job.join()
        }

        assertTrue(server.isClosed)
    }

    @Test
    fun `transport delivers end to end over stream links`() = runBlocking {
        val (x, y) = socketPair()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val a = Transport(listOf(FakeConnector().apply { offer(link(x)) }), scope, epoch = 1)
        val b = Transport(listOf(FakeConnector().apply { offer(link(y)) }), scope, epoch = 2)
        a.start()
        b.start()
        val got = async { b.received.first() }

        val delivery = a.send("over tcp".encodeToByteArray(), Priority.SOS)

        assertEquals(DeliveryStatus.DELIVERED, withTimeout(5_000) { delivery.awaitResult() })
        assertEquals("over tcp", withTimeout(5_000) { got.await() }.decodeToString())
        scope.cancel()
    }
}
