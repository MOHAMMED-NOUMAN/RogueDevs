package com.itantra.app.core.transport

import com.itantra.app.core.transport.LinkKind.RFCOMM
import com.itantra.app.core.transport.LinkKind.WIFI_DIRECT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** All timings run on virtual time: advanceTimeBy(1_500) is 1.5 s of transport time, instantly. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportTest {

    private fun TestScope.startTransport(vararg connectors: LinkConnector, epoch: Int) =
        Transport(connectors.toList(), backgroundScope, epoch = epoch).also { it.start() }

    private fun TestScope.receivedText(transport: Transport): List<String> {
        val out = mutableListOf<String>()
        backgroundScope.launch { transport.received.collect { out += it.decodeToString() } }
        return out
    }

    private fun text(s: String) = s.encodeToByteArray()

    /** Two transports joined by [pair], A on the .a end and B on the .b end. */
    private fun TestScope.connectedPair(pair: FakeLinkPair = FakeLinkPair()): Pair<Transport, Transport> {
        val a = startTransport(FakeConnector().apply { offer(pair.a) }, epoch = 1)
        val b = startTransport(FakeConnector().apply { offer(pair.b) }, epoch = 2)
        runCurrent()
        return a to b
    }

    @Test
    fun `packet is delivered once and reported delivered`() = runTest {
        val (a, b) = connectedPair()
        val got = receivedText(b)

        val delivery = a.send(text("hello"), Priority.NORMAL)
        runCurrent()

        assertEquals(DeliveryStatus.DELIVERED, delivery.status.value)
        assertEquals(listOf("hello"), got)
        assertEquals(LinkStatus.Connected(WIFI_DIRECT), a.linkStatus.value)
    }

    @Test
    fun `resends after 1_5 s when the ACK is lost and the peer drops the duplicate`() = runTest {
        val pair = FakeLinkPair()
        var ackDropped = false
        pair.dropBtoA = { frame ->
            (FrameCodec.decode(frame) is Frame.Ack && !ackDropped).also { if (it) ackDropped = true }
        }
        val (a, b) = connectedPair(pair)
        val got = receivedText(b)

        val delivery = a.send(text("hello"), Priority.NORMAL)
        advanceTimeBy(1_499)
        runCurrent()
        assertEquals(DeliveryStatus.SENT, delivery.status.value)
        assertEquals(1, pair.sentByA.dataFrames().size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, pair.sentByA.dataFrames().size)
        assertEquals(DeliveryStatus.DELIVERED, delivery.status.value)
        assertEquals(listOf("hello"), got)
    }

    @Test
    fun `fails after 5 resends on a live link`() = runTest {
        val pair = FakeLinkPair()
        pair.dropAtoB = { FrameCodec.decode(it) is Frame.Data } // heartbeats still flow
        val (a, _) = connectedPair(pair)

        val delivery = a.send(text("lost"), Priority.NORMAL)
        advanceTimeBy(8_999) // sends at 0, 1.5, 3, 4.5, 6, 7.5 s
        runCurrent()
        assertEquals(DeliveryStatus.SENT, delivery.status.value)
        assertEquals(6, pair.sentByA.dataFrames().size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(DeliveryStatus.FAILED, delivery.status.value)
        assertEquals(6, pair.sentByA.dataFrames().size)
        assertEquals(LinkStatus.Connected(WIFI_DIRECT), a.linkStatus.value)
    }

    @Test
    fun `outbox holds messages while down and flushes SOS, ALERT, then oldest first`() = runTest {
        val connectorA = FakeConnector()
        val connectorB = FakeConnector()
        val a = startTransport(connectorA, epoch = 1)
        val b = startTransport(connectorB, epoch = 2)
        val got = receivedText(b)

        val deliveries = listOf(
            a.send(text("n1"), Priority.NORMAL),
            a.send(text("a1"), Priority.ALERT),
            a.send(text("n2"), Priority.NORMAL),
            a.send(text("s1"), Priority.SOS),
        )
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(deliveries.all { it.status.value == DeliveryStatus.QUEUED })
        assertEquals(LinkStatus.Disconnected, a.linkStatus.value)

        val pair = FakeLinkPair()
        connectorA.offer(pair.a)
        connectorB.offer(pair.b)
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(listOf("s1", "a1", "n1", "n2"), got)
        assertTrue(deliveries.all { it.status.value == DeliveryStatus.DELIVERED })
    }

    @Test
    fun `link declared dead after 6 s of silence, message requeued and delivered on the next link`() = runTest {
        val first = FakeLinkPair()
        val second = FakeLinkPair()
        val connectorA = FakeConnector().apply { offer(first.a); offer(second.a) }
        val connectorB = FakeConnector().apply { offer(first.b); offer(second.b) }
        val a = startTransport(connectorA, epoch = 1)
        val b = startTransport(connectorB, epoch = 2)
        val got = receivedText(b)
        runCurrent()

        first.goSilent()
        val delivery = a.send(text("m"), Priority.NORMAL)
        advanceTimeBy(5_999)
        runCurrent()
        assertEquals(DeliveryStatus.SENT, delivery.status.value)
        assertTrue(second.sentByA.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(DeliveryStatus.DELIVERED, delivery.status.value)
        assertEquals(listOf("m"), got)
        assertEquals(1, second.sentByA.dataFrames().size)
        assertEquals(LinkStatus.Connected(WIFI_DIRECT), a.linkStatus.value)
    }

    @Test
    fun `falls back to RFCOMM when Wi-Fi Direct can't connect`() = runTest {
        val pair = FakeLinkPair(RFCOMM)
        val a = startTransport(FakeConnector(WIFI_DIRECT), FakeConnector(RFCOMM).apply { offer(pair.a) }, epoch = 1)
        val b = startTransport(FakeConnector(WIFI_DIRECT), FakeConnector(RFCOMM).apply { offer(pair.b) }, epoch = 2)
        val got = receivedText(b)
        runCurrent()

        a.send(text("over bt"), Priority.NORMAL)
        runCurrent()

        assertEquals(LinkStatus.Connected(RFCOMM), a.linkStatus.value)
        assertEquals(listOf("over bt"), got)
    }

    @Test
    fun `reconnect attempts back off exponentially up to 16 s`() = runTest {
        val attemptTimes = mutableListOf<Long>()
        startTransport(FakeConnector(onAttempt = { attemptTimes += currentTime }), epoch = 1)

        advanceTimeBy(50_000)

        assertEquals(listOf(0L, 1_000, 3_000, 7_000, 15_000, 31_000, 47_000), attemptTimes)
    }

    @Test
    fun `a listening connector may wait longer than the connect timeout`() = runTest {
        val listener = ListeningFakeConnector(RFCOMM)
        val pair = FakeLinkPair(RFCOMM)
        val a = startTransport(listener, epoch = 1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, listener.attempts)
        assertEquals(LinkStatus.Disconnected, a.linkStatus.value)

        startTransport(FakeConnector(RFCOMM).apply { offer(pair.b) }, epoch = 2)
        listener.offer(pair.a)
        runCurrent()

        assertEquals(1, listener.attempts)
        assertEquals(LinkStatus.Connected(RFCOMM), a.linkStatus.value)
    }

    @Test
    fun `a dialling phone moves back to Wi-Fi when it recovers, carrying unACKed messages over`() = runTest {
        val bluetooth = FakeLinkPair(RFCOMM)
        val wifi = FakeLinkPair(WIFI_DIRECT)
        val wifiA = FakeConnector(WIFI_DIRECT)
        val wifiB = FakeConnector(WIFI_DIRECT)
        val a = startTransport(wifiA, FakeConnector(RFCOMM).apply { offer(bluetooth.a) }, epoch = 1)
        val b = startTransport(wifiB, FakeConnector(RFCOMM).apply { offer(bluetooth.b) }, epoch = 2)
        val got = receivedText(b)
        runCurrent()
        assertEquals(LinkStatus.Connected(RFCOMM), a.linkStatus.value)

        // A message goes out on Bluetooth just before Wi-Fi returns, and its ACK never comes back.
        advanceTimeBy(29_000)
        bluetooth.dropAtoB = { FrameCodec.decode(it) is Frame.Data }
        val delivery = a.send(text("switch"), Priority.NORMAL)
        runCurrent()
        assertEquals(DeliveryStatus.SENT, delivery.status.value)

        wifiA.offer(wifi.a)
        wifiB.offer(wifi.b)
        advanceTimeBy(1_000) // retry of the preferred radio is due at 30 s
        runCurrent()

        assertEquals(LinkStatus.Connected(WIFI_DIRECT), a.linkStatus.value)
        assertEquals(DeliveryStatus.DELIVERED, delivery.status.value)
        assertEquals(listOf("switch"), got)
        assertEquals(1, wifi.sentByA.dataFrames().size)
    }

    @Test
    fun `a listening phone waits on both radios and moves to Wi-Fi when the peer does`() = runTest {
        val wifi = ListeningFakeConnector(WIFI_DIRECT)
        val bluetooth = ListeningFakeConnector(RFCOMM)
        val a = startTransport(wifi, bluetooth, epoch = 1)
        runCurrent()
        assertEquals(1, wifi.attempts) // waiting on both at once
        assertEquals(1, bluetooth.attempts)

        bluetooth.offer(FakeLinkPair(RFCOMM).a)
        runCurrent()
        assertEquals(LinkStatus.Connected(RFCOMM), a.linkStatus.value)

        wifi.offer(FakeLinkPair(WIFI_DIRECT).a)
        runCurrent()
        assertEquals(LinkStatus.Connected(WIFI_DIRECT), a.linkStatus.value)
    }

    @Test
    fun `stop releases every connector`() = runTest {
        val wifi = FakeConnector(WIFI_DIRECT)
        val bluetooth = FakeConnector(RFCOMM)
        val a = startTransport(wifi, bluetooth, epoch = 1)
        runCurrent()

        a.stop()

        assertTrue(wifi.released && bluetooth.released)
    }

    @Test
    fun `peer sees push-to-talk state, and it clears when the link drops`() = runTest {
        val pair = FakeLinkPair()
        val (a, b) = connectedPair(pair)

        a.setTransmitting(true)
        runCurrent()
        assertTrue(b.peerTransmitting.value)

        a.setTransmitting(false)
        runCurrent()
        assertFalse(b.peerTransmitting.value)

        a.setTransmitting(true)
        runCurrent()
        assertTrue(b.peerTransmitting.value)
        pair.a.close()
        runCurrent()
        assertFalse(b.peerTransmitting.value)
    }

    @Test
    fun `a restarted peer's messages are not mistaken for duplicates`() = runTest {
        val first = FakeLinkPair()
        val second = FakeLinkPair()
        val b = startTransport(FakeConnector().apply { offer(first.b); offer(second.b) }, epoch = 2)
        val got = receivedText(b)
        val before = startTransport(FakeConnector().apply { offer(first.a) }, epoch = 1)
        runCurrent()
        before.send(text("before restart"), Priority.NORMAL) // seq 1
        runCurrent()

        before.stop()
        val after = startTransport(FakeConnector().apply { offer(second.a) }, epoch = 99)
        after.send(text("after restart"), Priority.NORMAL) // seq 1 again
        runCurrent()

        assertEquals(listOf("before restart", "after restart"), got)
    }

    @Test
    fun `rejects empty and oversized packets`() = runTest {
        val a = Transport(emptyList(), backgroundScope)
        assertThrows(IllegalArgumentException::class.java) { a.send(ByteArray(0), Priority.NORMAL) }
        assertThrows(IllegalArgumentException::class.java) {
            a.send(ByteArray(MAX_PACKET_SIZE + 1), Priority.SOS)
        }
    }
}
