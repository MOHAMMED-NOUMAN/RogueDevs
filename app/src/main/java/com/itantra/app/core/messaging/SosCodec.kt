package com.itantra.app.core.messaging

import java.nio.ByteBuffer

/** Where the SOS sender was, as far as their phone knew. */
data class SosLocation(
    val latitude: Double,
    val longitude: Double,
    /** Radius of 68% confidence, metres. */
    val accuracyMeters: Int,
)

/** A packet of the SOS exchange between the two phones. */
sealed interface SosPacket {
    /** Sent (and re-sent) by the person in trouble until the teammate confirms. */
    data class Alert(
        val id: Int,
        val sender: String,
        val location: SosLocation?,
        val sentAtEpochSeconds: Long,
    ) : SosPacket

    /** The teammate's "I'm coming". */
    data class Ack(val id: Int, val responder: String) : SosPacket

    /** The sender called the SOS off. */
    data class Cancel(val id: Int) : SosPacket
}

/**
 * SOS packets on the link, next to [TextMessageCodec] (type byte 1). Fixed-size fields first,
 * the name last (UTF-8, cut to [TextMessageCodec.MAX_SENDER_BYTES]).
 *
 *   Alert  2 | id u16 | flags u8 (bit 0: has location) | lat i32 | lon i32 (micro-degrees)
 *          | accuracy u16 (m) | sent-at u32 (epoch s) | name length u8 | name
 *   Ack    3 | id u16 | name length u8 | name
 *   Cancel 4 | id u16
 */
object SosCodec {
    private const val TYPE_ALERT: Byte = 2
    private const val TYPE_ACK: Byte = 3
    private const val TYPE_CANCEL: Byte = 4
    private const val MICRO = 1_000_000.0

    fun isSos(packet: ByteArray): Boolean =
        packet.isNotEmpty() && packet[0] in listOf(TYPE_ALERT, TYPE_ACK, TYPE_CANCEL)

    fun encode(packet: SosPacket): ByteArray = when (packet) {
        is SosPacket.Alert -> {
            val name = utf8Prefix(packet.sender)
            ByteBuffer.allocate(19 + name.size).apply {
                put(TYPE_ALERT)
                putShort(packet.id.toShort())
                put(if (packet.location != null) 1 else 0)
                putInt(((packet.location?.latitude ?: 0.0) * MICRO).toInt())
                putInt(((packet.location?.longitude ?: 0.0) * MICRO).toInt())
                putShort((packet.location?.accuracyMeters ?: 0).coerceIn(0, 0xFFFF).toShort())
                putInt(packet.sentAtEpochSeconds.toInt())
                put(name.size.toByte())
                put(name)
            }.array()
        }
        is SosPacket.Ack -> {
            val name = utf8Prefix(packet.responder)
            ByteBuffer.allocate(4 + name.size).apply {
                put(TYPE_ACK)
                putShort(packet.id.toShort())
                put(name.size.toByte())
                put(name)
            }.array()
        }
        is SosPacket.Cancel -> ByteBuffer.allocate(3).apply {
            put(TYPE_CANCEL)
            putShort(packet.id.toShort())
        }.array()
    }

    /** The decoded packet, or null if it isn't a well-formed SOS packet. */
    fun decode(bytes: ByteArray): SosPacket? = runCatching {
        val b = ByteBuffer.wrap(bytes)
        when (b.get()) {
            TYPE_ALERT -> {
                val id = b.short.toInt() and 0xFFFF
                val hasLocation = (b.get().toInt() and 1) == 1
                val lat = b.int / MICRO
                val lon = b.int / MICRO
                val accuracy = b.short.toInt() and 0xFFFF
                val sentAt = b.int.toLong() and 0xFFFFFFFFL
                SosPacket.Alert(
                    id = id,
                    sender = readName(b),
                    location = if (hasLocation) SosLocation(lat, lon, accuracy) else null,
                    sentAtEpochSeconds = sentAt,
                )
            }
            TYPE_ACK -> {
                val id = b.short.toInt() and 0xFFFF
                SosPacket.Ack(id, readName(b))
            }
            TYPE_CANCEL -> SosPacket.Cancel(b.short.toInt() and 0xFFFF)
            else -> null
        }
    }.getOrNull()

    private fun readName(b: ByteBuffer): String {
        val length = b.get().toInt() and 0xFF
        return ByteArray(length).also { b.get(it) }.decodeToString()
    }

    private fun utf8Prefix(text: String): ByteArray {
        val bytes = text.encodeToByteArray()
        if (bytes.size <= TextMessageCodec.MAX_SENDER_BYTES) return bytes
        var end = TextMessageCodec.MAX_SENDER_BYTES
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return bytes.copyOf(end)
    }
}
