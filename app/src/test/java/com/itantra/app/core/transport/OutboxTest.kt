package com.itantra.app.core.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxTest {

    private fun message(seq: Int, priority: Priority) =
        OutgoingMessage(seq, priority, byteArrayOf(1), Delivery(seq))

    @Test
    fun `priority first, then oldest first`() {
        val outbox = Outbox()
        listOf(
            message(1, Priority.NORMAL),
            message(2, Priority.ALERT),
            message(3, Priority.NORMAL),
            message(4, Priority.SOS),
            message(5, Priority.ALERT),
            message(6, Priority.SOS),
        ).shuffled().forEach(outbox::add)

        val order = generateSequence { outbox.poll() }.map { it.seq }.toList()

        assertEquals(listOf(4, 6, 2, 5, 1, 3), order)
        assertNull(outbox.poll())
    }

    @Test
    fun `a requeued message goes back to its original place`() {
        val outbox = Outbox()
        outbox.add(message(2, Priority.NORMAL))
        outbox.add(message(3, Priority.NORMAL))
        outbox.add(message(1, Priority.NORMAL)) // was in flight, came back

        assertEquals(1, outbox.poll()?.seq)
    }

    @Test
    fun `dedup window forgets only the oldest`() {
        val window = SeqWindow(capacity = 3)
        assertTrue(window.add(1))
        assertTrue(window.add(2))
        assertTrue(window.add(3))
        assertFalse(window.add(2))
        assertTrue(window.add(4)) // evicts 1
        assertTrue(window.add(1))
        assertFalse(window.add(4))
    }
}
