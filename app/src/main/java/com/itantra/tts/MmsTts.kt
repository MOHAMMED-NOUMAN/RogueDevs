package com.itantra.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.LongBuffer
import java.nio.channels.FileChannel

/**
 * Offline text-to-speech with one Meta MMS-TTS voice (VITS, a single ONNX graph): text in,
 * 16 kHz mono float samples out. No network.
 *
 *   model.onnx   x [1, n] int64 symbol ids  ->  audio [1, 1, samples] float
 *   tokens.txt   "<character> <id>" per line (a line starting with two spaces is the space)
 *   config.json  sample_rate
 *
 * The weights are stored 16-bit (half the APK size) with a cast back to 32-bit in the graph,
 * so all maths runs in 32-bit. model.onnx is stored uncompressed in the APK
 * (`noCompress += "onnx"`), so it is memory-mapped straight from the APK: no copy to storage.
 *
 * Characters the voice has no symbol for are skipped; spell out numbers and abbreviations first.
 * Not thread-safe: call [synthesize] from one thread at a time.
 */
class MmsTts(
    context: Context,
    assetDir: String,
    threads: Int = 4,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
    private val session: OrtSession
    private val symbols: Map<Char, Long>

    val sampleRate: Int

    init {
        fun text(name: String) = context.assets.open("$assetDir/$name").use { String(it.readBytes(), Charsets.UTF_8) }

        sampleRate = JSONObject(text("config.json")).getInt("sample_rate")

        val parsed = HashMap<Char, Long>()
        text("tokens.txt").lineSequence().forEach { line ->
            val split = line.lastIndexOf(' ')
            if (split == 1) parsed[line[0]] = line.substring(2).toLong()
        }
        symbols = parsed

        session = context.assets.openFd("$assetDir/model.onnx").use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                env.createSession(mapped, options)
            }
        }
    }

    /** Speech for [text], or an empty array if it has no character this voice can say. */
    fun synthesize(text: String): FloatArray {
        val ids = text.mapNotNull { symbols[it] }
        if (ids.isEmpty()) return FloatArray(0)

        // MMS VITS expects symbol 0 before, between and after the characters, as Hugging Face's
        // VitsTokenizer does (add_blank). Not '_': that is an ordinary symbol, and putting it
        // between the characters gives speech-like gibberish of the right length.
        val input = LongArray(ids.size * 2 + 1) { BLANK }
        ids.forEachIndexed { i, id -> input[i * 2 + 1] = id }

        return OnnxTensor.createTensor(env, LongBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { x ->
            session.run(mapOf("x" to x)).use { result ->
                val buffer = (result[0] as OnnxTensor).floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
    }

    override fun close() {
        session.close()
        options.close()
    }

    private companion object {
        const val BLANK = 0L
    }
}
