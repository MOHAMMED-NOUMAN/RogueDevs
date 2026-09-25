package com.itantra.app.core.messaging

import com.itantra.app.core.transport.MAX_PACKET_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosCodecTest {

    @Test
    fun `alert with location round-trips to within a micro-degree`() {
        val alert = SosPacket.Alert(
            id = 65000,
            sender = "Arfaan",
            location = SosLocation(17.385044, 78.486671, 12),
            sentAtEpochSeconds = 1_790_000_000L,
        )
        val decoded = SosCodec.decode(SosCodec.encode(alert)) as SosPacket.Alert
        assertEquals(alert.id, decoded.id)
        assertEquals(alert.sender, decoded.sender)
        assertEquals(alert.sentAtEpochSeconds, decoded.sentAtEpochSeconds)
        assertEquals(17.385044, decoded.location!!.latitude, 1e-6)
        assertEquals(78.486671, decoded.location!!.longitude, 1e-6)
        assertEquals(12, decoded.location!!.accuracyMeters)
    }

    @Test
    fun `alert without location keeps location null`() {
        val alert = SosPacket.Alert(1, "प्रिया", null, 0)
        assertEquals(alert, SosCodec.decode(SosCodec.encode(alert)))
    }

    @Test
    fun `southern and western coordinates keep their sign`() {
        val alert = SosPacket.Alert(2, "x", SosLocation(-33.8688, -151.2093, 5), 10)
        val location = (SosCodec.decode(SosCodec.encode(alert)) as SosPacket.Alert).location!!
        assertEquals(-33.8688, location.latitude, 1e-6)
        assertEquals(-151.2093, location.longitude, 1e-6)
    }

    @Test
    fun `ack and cancel round-trip`() {
        assertEquals(SosPacket.Ack(7, "Imran"), SosCodec.decode(SosCodec.encode(SosPacket.Ack(7, "Imran"))))
        assertEquals(SosPacket.Cancel(7), SosCodec.decode(SosCodec.encode(SosPacket.Cancel(7))))
    }

    @Test
    fun `packets are small and are told apart from text messages`() {
        val alert = SosCodec.encode(SosPacket.Alert(1, "नाम".repeat(20), SosLocation(1.0, 2.0, 3), 4))
        assertTrue(alert.size <= 19 + TextMessageCodec.MAX_SENDER_BYTES)
        assertTrue(alert.size <= MAX_PACKET_SIZE)
        assertTrue(SosCodec.isSos(alert))
        val text = TextMessageCodec.encode(TextMessage(1, "a", MessageLanguage.ENGLISH, "hi")).first()
        assertFalse(SosCodec.isSos(text))
        assertNull(TextMessageCodec.decodePart(alert))
    }

    @Test
    fun `truncated packets decode to null`() {
        assertNull(SosCodec.decode(byteArrayOf(2, 0)))
        assertNull(SosCodec.decode(byteArrayOf()))
    }
}
