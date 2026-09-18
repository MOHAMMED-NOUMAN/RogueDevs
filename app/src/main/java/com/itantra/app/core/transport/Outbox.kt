package com.itantra.app.core.transport

import java.util.PriorityQueue

internal class OutgoingMessage(
    val seq: Int,
    val priority: Priority,
    val packet: ByteArray,
    val delivery: Delivery,
)

/**
 * Messages waiting to go on the wire: queued while the link is down, and messages that were
 * in flight when a link died. Order is priority first (SOS > ALERT > NORMAL), then oldest
 * first. A requeued message keeps its sequence number, so it goes back to its original place.
 *
 * In memory only: the contents are lost if the app process dies.
 * TODO(transport): persist the outbox (Room) once surviving process death is required.
 *
 * Not thread-safe. [Transport] only touches it from its single confined dispatcher.
 */
internal class Outbox {
    private val queue = PriorityQueue(
        compareByDescending<OutgoingMessage> { it.priority.code }.thenBy { it.seq }
    )

    val size: Int get() = queue.size

    fun add(message: OutgoingMessage) {
        queue.add(message)
    }

    fun poll(): OutgoingMessage? = queue.poll()
}
