package com.itantra.stt

import android.content.Context
import android.os.Build
import android.os.Debug
import org.json.JSONObject

/**
 * Measures the on-device cost of one speech-to-text pass and checks the output is still correct.
 *
 * Call [run] from anywhere with a Context (an Activity, or the instrumented test in
 * androidTest/). It returns a plain-text report — paste that back to the ML side.
 */
object SttBenchmark {

    /** Model asset folder paired with the sample clip used to check it. */
    val LANGUAGES = listOf("whisper-hi" to "sample_hi", "whisper-en" to "sample_en")

    /** Benchmarks every language in [LANGUAGES] and returns one report. */
    fun runAll(context: Context, threadCounts: IntArray = intArrayOf(1, 2, 4), iterations: Int = 5): String =
        LANGUAGES.joinToString("\n") { (dir, sample) -> run(context, dir, sample, threadCounts, iterations) }

    fun run(
        context: Context,
        assetDir: String = "whisper-hi",
        sampleBase: String = "sample_hi",
        threadCounts: IntArray = intArrayOf(1, 2, 4),
        iterations: Int = 5,
    ): String {
        val meta = JSONObject(context.assets.open("$sampleBase.json").use { String(it.readBytes(), Charsets.UTF_8) })
        val expected = meta.getString("expected_output")
        val audioSeconds = meta.getDouble("duration_s")
        val pcm = readWavMono16(context, "$sampleBase.wav")

        val report = StringBuilder()
        report.appendLine("=== iTantra STT benchmark: $assetDir ===")
        report.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) | ${Build.SUPPORTED_ABIS.firstOrNull()}")
        report.appendLine("cores: ${Runtime.getRuntime().availableProcessors()} | clip: ${audioSeconds}s, " +
            "${pcm.size} samples")
        report.appendLine()

        for (threads in threadCounts) {
            val loadStart = System.nanoTime()
            WhisperStt(context, assetDir, threads).use { stt ->
                val coldLoadMs = (System.nanoTime() - loadStart) / 1_000_000

                val first = stt.transcribe(pcm)          // cold run: includes lazy allocations
                val runs = (1..iterations).map { stt.transcribe(pcm) }

                val totals = runs.map { it.totalMs }.sorted()
                val median = totals[totals.size / 2]
                val mel = runs.map { it.melMs }.sorted()[runs.size / 2]
                val encoder = runs.map { it.encoderMs }.sorted()[runs.size / 2]
                val decoder = runs.map { it.decoderMs }.sorted()[runs.size / 2]
                val steps = runs.first().decoderSteps
                val rtf = median / 1000.0 / audioSeconds
                val correct = runs.all { it.text.trim() == expected.trim() }

                report.appendLine("--- $threads thread(s)")
                report.appendLine("  model load (cold):   $coldLoadMs ms")
                report.appendLine("  first transcribe:    ${first.totalMs} ms")
                report.appendLine("  median total:        $median ms   (min ${totals.first()}, max ${totals.last()})")
                report.appendLine("  mel / encoder / decoder: $mel / $encoder / $decoder ms")
                report.appendLine("  decoder steps:       $steps  (${"%.2f".format(decoder.toDouble() / steps)} ms/token)")
                report.appendLine("  real-time factor:    ${"%.3f".format(rtf)}  (<1 = faster than the audio)")
                report.appendLine("  output correct:      $correct")
                if (!correct) {
                    report.appendLine("  EXPECTED: $expected")
                    report.appendLine("  GOT:      ${runs.first().text}")
                }
                report.appendLine("  hit token limit:     ${runs.first().hitTokenLimit}")
                report.appendLine("  native heap:         ${Debug.getNativeHeapAllocatedSize() / 1024 / 1024} MB")
                report.appendLine()
            }
        }
        report.appendLine("targets: total < 700 ms for STT, RTF < 1, output correct = true")
        return report.toString()
    }

    /** Minimal 16-bit PCM WAV reader: returns mono float samples in -1..1. */
    private fun readWavMono16(context: Context, asset: String): FloatArray {
        val raw = context.assets.open(asset).use { it.readBytes() }
        // Walk the RIFF chunks rather than assuming a 44-byte header.
        var pos = 12
        var channels = 1
        var dataStart = -1
        var dataSize = 0
        while (pos + 8 <= raw.size) {
            val id = String(raw, pos, 4, Charsets.US_ASCII)
            val size = le32(raw, pos + 4)
            when (id) {
                "fmt " -> channels = le16(raw, pos + 10)
                "data" -> { dataStart = pos + 8; dataSize = size }
            }
            if (dataStart >= 0) break
            pos += 8 + size + (size and 1)
        }
        require(dataStart >= 0) { "no data chunk in $asset" }
        val frames = dataSize / 2 / channels
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                val o = dataStart + (i * channels + c) * 2
                sum += (raw[o].toInt() and 0xFF or (raw[o + 1].toInt() shl 8)).toShort().toInt()
            }
            out[i] = sum / channels / 32768f
        }
        return out
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) = le16(b, o) or (le16(b, o + 2) shl 16)
}
