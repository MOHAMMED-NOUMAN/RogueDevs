package com.itantra.stt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONObject
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Offline Hindi/English speech-to-text: three ONNX graphs, no network, no DSP code.
 *
 *   melgram.onnx   pcm   [1, 480000] float  ->  mel [1, 80, 3000]
 *   encoder.onnx   mel                      ->  cross_k, cross_v  (audio attended by the decoder)
 *   decoder.onnx   one step at a time, carrying a fixed-size 448-token cache
 *
 * Feed [transcribe] exactly 16 kHz mono float samples in -1..1 (what AudioRecord gives after
 * dividing 16-bit samples by 32768). Anything past 30 s is ignored: Whisper's window is fixed.
 *
 * The greedy loop below mirrors `itantra/onnx_whisper.py`, which was verified against PyTorch
 * on 50 clips (49/50 identical text). Two rules in it are easy to get wrong:
 *  - a token >= firstSpecialToken (a timestamp) stays in the decoder's context but never
 *    reaches the text — this model ends utterances with one, and dropping it makes decoding
 *    run away to the token limit;
 *  - beginSuppressTokens are banned only on the very first generated token.
 */
class WhisperStt(
    context: Context,
    assetDir: String = "whisper-hi",
    threads: Int = 4,
) : AutoCloseable {

    data class Result(
        val text: String,
        val melMs: Long,
        val encoderMs: Long,
        val decoderMs: Long,
        val decoderSteps: Int,
        val hitTokenLimit: Boolean,
    ) {
        val totalMs: Long get() = melMs + encoderMs + decoderMs
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }

    private val melSession: OrtSession
    private val encoderSession: OrtSession
    private val decoderSession: OrtSession

    private val nLayers: Int
    private val nCtx: Int
    private val dModel: Int
    private val nSamples = 480_000 // 30 s at 16 kHz
    private val promptTokens: LongArray
    private val eosToken: Int
    private val firstSpecialToken: Int
    private val suppressTokens: IntArray
    private val beginSuppressTokens: IntArray
    private val tokenBytes: HashMap<Int, ByteArray>

    init {
        fun asset(name: String) = context.assets.open("$assetDir/$name").use { it.readBytes() }

        melSession = env.createSession(asset("melgram.onnx"), options)
        encoderSession = env.createSession(asset("encoder.onnx"), options)
        decoderSession = env.createSession(asset("decoder.onnx"), options)

        val config = JSONObject(String(asset("whisper_config.json"), Charsets.UTF_8))
        nLayers = config.getInt("n_layers")
        nCtx = config.getInt("n_ctx")
        dModel = config.getInt("d_model")
        eosToken = config.getInt("eos_token")
        firstSpecialToken = config.getInt("first_special_token")
        promptTokens = config.getJSONArray("prompt_tokens").let { array ->
            LongArray(array.length()) { array.getLong(it) }
        }
        suppressTokens = config.getJSONArray("suppress_tokens").let { array ->
            IntArray(array.length()) { array.getInt(it) }
        }
        beginSuppressTokens = config.getJSONArray("begin_suppress_tokens").let { array ->
            IntArray(array.length()) { array.getInt(it) }
        }

        // tokens.txt: "<id> <hex of the token's UTF-8 bytes>" — decoding is byte concatenation,
        // so the app needs no BPE implementation.
        tokenBytes = HashMap()
        String(asset("tokens.txt"), Charsets.UTF_8).lineSequence().forEach { line ->
            if (line.isNotBlank()) {
                val space = line.indexOf(' ')
                val id = line.substring(0, space).toInt()
                val hex = line.substring(space + 1)
                tokenBytes[id] = ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            }
        }
    }

    private var bannedLater: BooleanArray? = null
    private var bannedFirst: BooleanArray? = null

    /** Tokens the sampler may not pick; `begin_suppress_tokens` apply only to the first one. */
    private fun bannedMask(vocab: Int, firstStep: Boolean): BooleanArray {
        if (bannedLater == null) {
            val later = BooleanArray(vocab)
            suppressTokens.forEach { if (it in 0 until vocab) later[it] = true }
            val first = later.copyOf()
            beginSuppressTokens.forEach { if (it in 0 until vocab) first[it] = true }
            bannedLater = later
            bannedFirst = first
        }
        return if (firstStep) bannedFirst!! else bannedLater!!
    }

    fun transcribe(pcm16k: FloatArray, maxNewTokens: Int = 200): Result {
        val melStart = System.nanoTime()
        val padded = FloatArray(nSamples)
        System.arraycopy(pcm16k, 0, padded, 0, minOf(pcm16k.size, nSamples))
        val mel: OnnxTensor
        OnnxTensor.createTensor(env, FloatBuffer.wrap(padded), longArrayOf(1, nSamples.toLong())).use { pcmTensor ->
            melSession.run(mapOf("pcm" to pcmTensor)).use { out ->
                // copy out of the result before it is closed
                val data = (out[0] as OnnxTensor).floatBuffer
                val copy = FloatArray(data.remaining()).also { data.get(it) }
                mel = OnnxTensor.createTensor(env, FloatBuffer.wrap(copy), longArrayOf(1, 80, 3000))
            }
        }
        val melMs = (System.nanoTime() - melStart) / 1_000_000

        val encoderStart = System.nanoTime()
        val encoderOut = encoderSession.run(mapOf("mel" to mel))
        mel.close()
        val crossK = encoderOut[0] as OnnxTensor
        val crossV = encoderOut[1] as OnnxTensor
        val encoderMs = (System.nanoTime() - encoderStart) / 1_000_000

        val decoderStart = System.nanoTime()
        val cacheShape = longArrayOf(nLayers.toLong(), 1, nCtx.toLong(), dModel.toLong())
        val cacheSize = nLayers * nCtx * dModel
        var selfK: OnnxTensor = OnnxTensor.createTensor(env, FloatBuffer.allocate(cacheSize), cacheShape)
        var selfV: OnnxTensor = OnnxTensor.createTensor(env, FloatBuffer.allocate(cacheSize), cacheShape)
        // Holds the tensors handed back by the previous step; closing it frees that step's
        // logits and cache in one go. The first two caches are standalone and closed by hand.
        var previous: OrtSession.Result? = null
        var initialCaches: Pair<OnnxTensor, OnnxTensor>? = Pair(selfK, selfV)

        var tokens = promptTokens
        var offset = 0L
        val bytes = java.io.ByteArrayOutputStream()
        var steps = 0
        var hitLimit = true
        val limit = minOf(maxNewTokens, nCtx - promptTokens.size)

        try {
            for (step in 0 until limit) {
                val tokenTensor = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())
                )
                val offsetTensor = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(longArrayOf(offset)), longArrayOf(1)
                )
                val result = decoderSession.run(
                    mapOf(
                        "tokens" to tokenTensor,
                        "self_k" to selfK,
                        "self_v" to selfV,
                        "cross_k" to crossK,
                        "cross_v" to crossV,
                        "offset" to offsetTensor,
                    )
                )
                tokenTensor.close()
                offsetTensor.close()
                steps++
                offset += tokens.size

                val logits = (result[0] as OnnxTensor).floatBuffer
                val vocab = logits.remaining() / tokens.size
                val base = (tokens.size - 1) * vocab // only the last position matters
                val banned = bannedMask(vocab, firstStep = step == 0)

                var best = -1
                var bestScore = Float.NEGATIVE_INFINITY
                for (id in 0 until vocab) {
                    if (banned[id]) continue
                    val score = logits.get(base + id)
                    if (score > bestScore) {
                        bestScore = score
                        best = id
                    }
                }

                // This step's caches become the next step's inputs; everything the previous
                // step produced can now be released.
                selfK = result[1] as OnnxTensor
                selfV = result[2] as OnnxTensor
                initialCaches?.let { (k, v) -> k.close(); v.close() }
                initialCaches = null
                previous?.close()
                previous = result

                if (best == eosToken) {
                    hitLimit = false
                    break
                }
                // A timestamp token stays in the decoder's context but never reaches the text.
                if (best < firstSpecialToken) tokenBytes[best]?.let { bytes.write(it) }
                tokens = longArrayOf(best.toLong())
            }
        } finally {
            initialCaches?.let { (k, v) -> k.close(); v.close() }
            previous?.close()
            encoderOut.close()
        }
        val text = String(bytes.toByteArray(), Charsets.UTF_8).trim()
        val decoderMs = (System.nanoTime() - decoderStart) / 1_000_000
        return Result(text, melMs, encoderMs, decoderMs, steps, hitLimit)
    }

    override fun close() {
        melSession.close()
        encoderSession.close()
        decoderSession.close()
        options.close()
    }
}
