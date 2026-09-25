package com.itantra.app.core.messaging

import com.itantra.app.core.transport.MAX_PACKET_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMessageCodecTest {

    private fun roundTrip(message: TextMessage, order: (List<ByteArray>) -> List<ByteArray> = { it }): TextMessage? {
        val assembler = MessageAssembler()
        var result: TextMessage? = null
        for (packet in order(TextMessageCodec.encode(message))) {
            val part = TextMessageCodec.decodePart(packet) ?: error("undecodable part")
            result = assembler.add(part, nowMs = 0) ?: result
        }
        return result
    }

    @Test
    fun `short english message is one packet and round-trips`() {
        val message = TextMessage(7, "Arfaan", MessageLanguage.ENGLISH, "We need water at the north gate.")
        assertEquals(1, TextMessageCodec.encode(message).size)
        assertEquals(message, roundTrip(message))
    }

    @Test
    fun `long hindi message splits into packets within the limit and reassembles exactly`() {
        val text = "हमने ओबीसी तबके के बच्चों के लिए उच्च शिक्षा में आरक्षण दिया ".repeat(6).trim()
        val message = TextMessage(65535, "अरफ़ान", MessageLanguage.HINDI, text)
        val packets = TextMessageCodec.encode(message)
        assertTrue("expected several parts, got ${packets.size}", packets.size > 3)
        assertTrue(packets.all { it.size <= MAX_PACKET_SIZE })
        assertEquals(message, roundTrip(message))
    }

    @Test
    fun `parts arriving out of order still reassemble`() {
        val message = TextMessage(3, "Priya", MessageLanguage.HINDI, "आरक्षण ".repeat(80).trim())
        assertEquals(message, roundTrip(message) { it.reversed() })
    }

    @Test
    fun `every part is valid utf-8 on its own`() {
        val text = "उच्च शिक्षा ".repeat(40)
        for (packet in TextMessageCodec.encode(TextMessage(1, "", MessageLanguage.HINDI, text))) {
            val part = TextMessageCodec.decodePart(packet)!!
            val decoded = part.text.decodeToString()
            assertTrue(!decoded.contains('�'))
        }
    }

    @Test
    fun `long sender name is cut without breaking a character`() {
        val message = TextMessage(1, "नाम".repeat(20), MessageLanguage.ENGLISH, "hi")
        val sender = roundTrip(message)!!.sender
        assertTrue(sender.encodeToByteArray().size <= TextMessageCodec.MAX_SENDER_BYTES)
        assertTrue(!sender.contains('�'))
    }

    @Test
    fun `packets that are not text parts are ignored`() {
        assertNull(TextMessageCodec.decodePart(byteArrayOf(9, 0, 0, 0, 1, 0, 0)))
        assertNull(TextMessageCodec.decodePart(byteArrayOf(1, 0)))
    }
}
