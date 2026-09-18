package com.itantra.app.core.transport

import java.nio.ByteBuffer

/**
 * Transport-level frames. The app's packet rides inside [Data] untouched; transport never
 * looks into it.
 *
 * Wire format, big-endian. Byte 0 is (protocol version shl 4) or type.
 *
 *   HELLO      [0x11][epoch:4]                          5 bytes, first frame on every link
 *   DATA       [0x12][priority:1][seq:4][packet:1..240] 7..246 bytes
 *   ACK        [0x13][seq:4]                            5 bytes, acknowledges DATA or PTT
 *   HEARTBEAT  [0x14]                                   1 byte
 *   PTT        [0x15][seq:4][transmitting:1]            6 bytes
 */
internal sealed interface Frame {
    /**
     * [epoch] is random per app run. Sequence numbers restart at 1 when the peer's app
     * restarts; a new epoch tells us to forget the old ones instead of dropping new
     * messages as duplicates.
     */
    data class Hello(val epoch: Int) : Frame

    class Data(val seq: Int, val priority: Priority, val packet: ByteArray) : Frame

    data class Ack(val seq: Int) : Frame

    data object Heartbeat : Frame

    data class Ptt(val seq: Int, val transmitting: Boolean) : Frame
}

internal object FrameCodec {
    const val VERSION = 1
    const val MAX_FRAME_SIZE = 6 + MAX_PACKET_SIZE

    private const val HELLO = 1
    private const val DATA = 2
    private const val ACK = 3
    private const val HEARTBEAT = 4
    private const val PTT = 5

    fun encode(frame: Frame): ByteArray = when (frame) {
        is Frame.Hello -> ByteBuffer.allocate(5).put(header(HELLO)).putInt(frame.epoch).array()
        is Frame.Data -> {
            require(frame.packet.size in 1..MAX_PACKET_SIZE) { "packet size ${frame.packet.size}" }
            ByteBuffer.allocate(6 + frame.packet.size)
                .put(header(DATA))
                .put(frame.priority.code.toByte())
                .putInt(frame.seq)
                .put(frame.packet)
                .array()
        }
        is Frame.Ack -> ByteBuffer.allocate(5).put(header(ACK)).putInt(frame.seq).array()
        Frame.Heartbeat -> byteArrayOf(header(HEARTBEAT))
        is Frame.Ptt -> ByteBuffer.allocate(6)
            .put(header(PTT))
            .putInt(frame.seq)
            .put(if (frame.transmitting) 1 else 0)
            .array()
    }

    /** Returns null for anything malformed or from a protocol version we don't speak. */
    fun decode(bytes: ByteArray): Frame? {
        if (bytes.isEmpty()) return null
        val head = bytes[0].toInt() and 0xFF
        if (head ushr 4 != VERSION) return null
        val body = ByteBuffer.wrap(bytes, 1, bytes.size - 1)
        return when (head and 0x0F) {
            HELLO -> if (body.remaining() == 4) Frame.Hello(body.int) else null
            DATA -> {
                if (body.remaining() - 5 !in 1..MAX_PACKET_SIZE) return null
                val priority = Priority.fromCode(body.get().toInt() and 0xFF) ?: return null
                val seq = body.int
                Frame.Data(seq, priority, ByteArray(body.remaining()).also { body.get(it) })
            }
            ACK -> if (body.remaining() == 4) Frame.Ack(body.int) else null
            HEARTBEAT -> if (body.remaining() == 0) Frame.Heartbeat else null
            PTT -> {
                if (body.remaining() != 5) return null
                val seq = body.int
                when (body.get().toInt()) {
                    0 -> Frame.Ptt(seq, transmitting = false)
                    1 -> Frame.Ptt(seq, transmitting = true)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun header(type: Int): Byte = ((VERSION shl 4) or type).toByte()
}
