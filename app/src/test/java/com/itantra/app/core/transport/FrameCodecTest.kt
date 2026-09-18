package com.itantra.app.core.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {

    private fun roundTrip(frame: Frame): Frame? = FrameCodec.decode(FrameCodec.encode(frame))

    @Test
    fun `control frames round-trip`() {
        assertEquals(Frame.Hello(-123456), roundTrip(Frame.Hello(-123456)))
        assertEquals(Frame.Ack(42), roundTrip(Frame.Ack(42)))
        assertEquals(Frame.Heartbeat, roundTrip(Frame.Heartbeat))
        assertEquals(Frame.Ptt(7, transmitting = true), roundTrip(Frame.Ptt(7, transmitting = true)))
        assertEquals(Frame.Ptt(8, transmitting = false), roundTrip(Frame.Ptt(8, transmitting = false)))
    }

    @Test
    fun `data round-trips every priority with the packet untouched`() {
        val packet = ByteArray(84) { (it * 7).toByte() }
        for (priority in Priority.entries) {
            val decoded = roundTrip(Frame.Data(99, priority, packet)) as Frame.Data
            assertEquals(99, decoded.seq)
            assertEquals(priority, decoded.priority)
            assertArrayEquals(packet, decoded.packet)
        }
    }

    @Test
    fun `frame sizes match the documented wire format`() {
        assertEquals(1, FrameCodec.encode(Frame.Heartbeat).size)
        assertEquals(5, FrameCodec.encode(Frame.Ack(1)).size)
        assertEquals(6 + 84, FrameCodec.encode(Frame.Data(1, Priority.NORMAL, ByteArray(84))).size)
        assertEquals(
            FrameCodec.MAX_FRAME_SIZE,
            FrameCodec.encode(Frame.Data(1, Priority.SOS, ByteArray(MAX_PACKET_SIZE))).size,
        )
    }

    @Test
    fun `encoding refuses empty or oversized packets`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameCodec.encode(Frame.Data(1, Priority.NORMAL, ByteArray(0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameCodec.encode(Frame.Data(1, Priority.NORMAL, ByteArray(MAX_PACKET_SIZE + 1)))
        }
    }

    @Test
    fun `malformed or foreign frames decode to null`() {
        val ack = FrameCodec.encode(Frame.Ack(1))
        val data = FrameCodec.encode(Frame.Data(1, Priority.NORMAL, ByteArray(10)))

        assertNull(FrameCodec.decode(ByteArray(0)))
        assertNull(FrameCodec.decode(ack.copyOf().also { it[0] = 0x23 })) // version 2
        assertNull(FrameCodec.decode(byteArrayOf(0x1F))) // unknown type
        assertNull(FrameCodec.decode(ack.copyOf(4))) // truncated
        assertNull(FrameCodec.decode(data.copyOf(6))) // data with empty packet
        assertNull(FrameCodec.decode(data.copyOf(FrameCodec.MAX_FRAME_SIZE + 1))) // too big
        assertNull(FrameCodec.decode(data.copyOf().also { it[1] = 9 })) // unknown priority
        assertNull(FrameCodec.decode(byteArrayOf(0x15, 0, 0, 0, 1, 2))) // PTT state not 0/1
        assertTrue(FrameCodec.decode(data) is Frame.Data)
    }
}
