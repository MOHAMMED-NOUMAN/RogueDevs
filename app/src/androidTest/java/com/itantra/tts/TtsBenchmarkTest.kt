package com.itantra.tts

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itantra.app.core.messaging.MessageLanguage
import com.itantra.app.core.ml.SpeakableText
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Loads each MMS voice straight from the APK's assets and times speech for one sentence:
 *
 *     adb shell am instrument -w -e class com.itantra.tts.TtsBenchmarkTest \
 *         com.itantra.app.test/androidx.test.runner.AndroidJUnitRunner
 *     adb logcat -s TtsBenchmark:I
 *
 * Fails only if a voice produces no audio; speed is reported, not asserted.
 */
@RunWith(AndroidJUnit4::class)
class TtsBenchmarkTest {

    private val sentences = mapOf(
        MessageLanguage.ENGLISH to ("mms-tts-en" to "2 people are trapped near the north gate, send help now."),
        MessageLanguage.HINDI to ("mms-tts-hi" to "उत्तर गेट के पास 2 लोग फंसे हैं, तुरंत मदद भेजो।"),
    )

    @Test
    fun benchmarkEnglishAndHindi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        log("device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} | " +
            "${Build.SUPPORTED_ABIS.firstOrNull()} | cores ${Runtime.getRuntime().availableProcessors()}")
        for ((language, entry) in sentences) {
            val (dir, text) = entry
            val spoken = SpeakableText.chunks(text, language).joinToString(" ")
            for (threads in intArrayOf(2, 4)) {
                val loadStart = System.nanoTime()
                val tts = MmsTts(context, dir, threads)
                val loadMs = (System.nanoTime() - loadStart) / 1_000_000
                tts.use {
                    val first = it.synthesize(spoken)
                    val runs = LongArray(3) { _ ->
                        val start = System.nanoTime()
                        it.synthesize(spoken)
                        (System.nanoTime() - start) / 1_000_000
                    }
                    runs.sort()
                    val audioS = first.size / it.sampleRate.toFloat()
                    log("$dir threads=$threads load=${loadMs}ms synth median=${runs[1]}ms " +
                        "audio=${"%.2f".format(audioS)}s rtf=${"%.2f".format(runs[1] / 1000f / audioS)}")
                    assertTrue("$dir produced no audio", first.isNotEmpty())
                }
            }
            log("$dir text: $spoken")
        }
        val heapMb = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1_048_576 }
        log("java heap used: ${heapMb} MB (the voices live in native memory)")
    }

    private fun log(line: String) = Log.i("TtsBenchmark", line)
}
