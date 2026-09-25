package com.itantra.app.core.transport

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A [Link] over a byte stream: an RFCOMM socket, or TCP over Wi-Fi Direct. Each frame goes on
 * the wire as a 2-byte big-endian length followed by the frame itself.
 */
internal class StreamLink(
    override val kind: LinkKind,
    input: InputStream,
    output: OutputStream,
    private val closeStreams: () -> Unit,
) : Link {
    private val input = DataInputStream(BufferedInputStream(input))
    private val output = DataOutputStream(BufferedOutputStream(output))
    private val closed = AtomicBoolean(false)

    override suspend fun receive(): ByteArray = withContext(Dispatchers.IO) {
        val size = input.readUnsignedShort()
        if (size !in 1..FrameCodec.MAX_FRAME_SIZE) throw IOException("bad frame length $size")
        ByteArray(size).also(input::readFully)
    }

    override suspend fun send(frame: ByteArray) = withContext(Dispatchers.IO) {
        require(frame.size in 1..FrameCodec.MAX_FRAME_SIZE) { "frame size ${frame.size}" }
        output.writeShort(frame.size)
        output.write(frame)
        output.flush()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) runCatching(closeStreams)
    }
}

/**
 * Runs a blocking call (socket connect, accept) on [Dispatchers.IO]. If the coroutine is
 * cancelled meanwhile, calls [cancel], e.g. closing the socket, so the blocked thread wakes up
 * instead of hanging on.
 */
internal suspend fun <T> cancellableBlocking(cancel: () -> Unit, block: () -> T): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) runCatching(cancel)
        }
    }
    try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: Throwable) {
        // If we were cancelled, the "socket closed" error is our own doing: report cancellation.
        ensureActive()
        throw e
    } finally {
        finished.set(true)
        watcher.cancel()
    }
}
