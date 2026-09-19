package com.itantra.app.core.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

@Serializable
data class WireMessage(
    val type: String,
    val sessionId: String? = null,
    val from: String? = null,
    val body: String? = null
) {
    companion object {
        const val PAIR_REQUEST = "PAIR_REQUEST"
        const val PAIR_ACCEPT = "PAIR_ACCEPT"
        const val PAIR_REJECT = "PAIR_REJECT"
        const val PING = "PING"
        const val TEXT = "TEXT"
    }
}

class FramedConnection(
    input: InputStream,
    output: OutputStream
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
    private val writeLock = Any()

    fun send(message: WireMessage) {
        synchronized(writeLock) {
            writer.write(json.encodeToString(message))
            writer.write("\n")
            writer.flush()
        }
    }

    fun readLoop(onMessage: (WireMessage) -> Unit) {
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            onMessage(json.decodeFromString(WireMessage.serializer(), line))
        }
    }

    fun closeQuietly() {
        runCatching { reader.close() }
        runCatching { writer.close() }
    }
}
