package com.itantra.app.core.messaging

import com.itantra.app.core.transport.MAX_PACKET_SIZE

/** Language of a message's text, as carried on the wire. */
enum class MessageLanguage(val code: Int) {
    ENGLISH(0),
    HINDI(1);

    companion object {
        fun fromCode(code: Int): MessageLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** A whole text message, before splitting or after reassembly. */
data class TextMessage(
    val id: Int,
    val sender: String,
    val language: MessageLanguage,
    val text: String,
)

/**
 * Splits a [TextMessage] into transport packets (each at most [MAX_PACKET_SIZE] bytes) and
 * reads them back. Plain UTF-8 for now; encryption comes with QR pairing.
 *
 * Packet layout:
 *   0     type (1 = text part)
 *   1..2  message id (big-endian, wraps at 65536)
 *   3     part index, 0-based
 *   4     part count (1..255)
 *   5     language code
 *   6     sender name length N in bytes (part 0 only; 0 on later parts)
 *   7..   N bytes of sender name, then this part's slice of the UTF-8 text
 *
 * Text is split only between characters, so every part is valid UTF-8 on its own.
 */
object TextMessageCodec {
    private const val TYPE_TEXT = 1
    private const val HEADER = 7
    private const val MAX_PARTS = 255

    /** Longest sender name carried, in bytes. */
    const val MAX_SENDER_BYTES = 24

    fun encode(message: TextMessage): List<ByteArray> {
        val sender = utf8Prefix(message.sender, MAX_SENDER_BYTES)
        val slices = splitUtf8(message.text, firstCapacity = MAX_PACKET_SIZE - HEADER - sender.size,
            capacity = MAX_PACKET_SIZE - HEADER)
        require(slices.size <= MAX_PARTS) { "message too long: ${slices.size} parts" }
        return slices.mapIndexed { index, slice ->
            val name = if (index == 0) sender else ByteArray(0)
            ByteArray(HEADER + name.size + slice.size).also { p ->
                p[0] = TYPE_TEXT.toByte()
                p[1] = (message.id shr 8).toByte()
                p[2] = message.id.toByte()
                p[3] = index.toByte()
                p[4] = slices.size.toByte()
                p[5] = message.language.code.toByte()
                p[6] = name.size.toByte()
                name.copyInto(p, HEADER)
                slice.copyInto(p, HEADER + name.size)
            }
        }
    }

    /** One decoded part, or null if [packet] isn't a text part this version understands. */
    fun decodePart(packet: ByteArray): Part? {
        if (packet.size < HEADER || packet[0].toInt() != TYPE_TEXT) return null
        val index = packet[3].toInt() and 0xFF
        val count = packet[4].toInt() and 0xFF
        val language = MessageLanguage.fromCode(packet[5].toInt() and 0xFF) ?: return null
        val nameLength = packet[6].toInt() and 0xFF
        if (count == 0 || index >= count || HEADER + nameLength > packet.size) return null
        return Part(
            id = ((packet[1].toInt() and 0xFF) shl 8) or (packet[2].toInt() and 0xFF),
            index = index,
            count = count,
            language = language,
            sender = packet.decodeToString(HEADER, HEADER + nameLength),
            text = packet.copyOfRange(HEADER + nameLength, packet.size),
        )
    }

    class Part(
        val id: Int,
        val index: Int,
        val count: Int,
        val language: MessageLanguage,
        val sender: String,
        val text: ByteArray,
    )

    /** UTF-8 bytes of [text], split between characters: first slice up to [firstCapacity]. */
    private fun splitUtf8(text: String, firstCapacity: Int, capacity: Int): List<ByteArray> {
        val bytes = text.encodeToByteArray()
        val slices = mutableListOf<ByteArray>()
        var start = 0
        while (start < bytes.size || slices.isEmpty()) {
            var end = minOf(bytes.size, start + if (slices.isEmpty()) firstCapacity else capacity)
            // Back off to the start of a character (continuation bytes are 10xxxxxx).
            while (end < bytes.size && end > start && (bytes[end].toInt() and 0xC0) == 0x80) end--
            slices += bytes.copyOfRange(start, end)
            start = end
        }
        return slices
    }

    /** UTF-8 bytes of [text], cut to at most [maxBytes] without splitting a character. */
    private fun utf8Prefix(text: String, maxBytes: Int): ByteArray {
        val bytes = text.encodeToByteArray()
        if (bytes.size <= maxBytes) return bytes
        var end = maxBytes
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return bytes.copyOf(end)
    }
}

/**
 * Collects parts until a message is complete. Parts may arrive in any order; the transport
 * already drops duplicates. Unfinished messages are forgotten after [staleAfterMs].
 */
class MessageAssembler(private val staleAfterMs: Long = 60_000) {
    private class Pending(val count: Int, val firstSeenMs: Long) {
        val parts = arrayOfNulls<TextMessageCodec.Part>(count)
    }

    private val pending = HashMap<Int, Pending>()

    /** Adds [part]; returns the whole message once its last missing part arrives. */
    fun add(part: TextMessageCodec.Part, nowMs: Long): TextMessage? {
        pending.entries.removeAll { nowMs - it.value.firstSeenMs > staleAfterMs }
        val entry = pending[part.id]?.takeIf { it.count == part.count }
            ?: Pending(part.count, nowMs).also { pending[part.id] = it }
        entry.parts[part.index] = part
        if (entry.parts.any { it == null }) return null
        pending.remove(part.id)
        val parts = entry.parts.requireNoNulls()
        val text = parts.fold(ByteArray(0)) { acc, p -> acc + p.text }.decodeToString()
        return TextMessage(part.id, parts[0].sender, parts[0].language, text)
    }
}
