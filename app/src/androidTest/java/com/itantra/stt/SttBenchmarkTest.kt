package com.itantra.stt

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the STT benchmark on a connected phone:
 *
 *     ./gradlew connectedAndroidTest
 *     adb logcat -s SttBenchmark:I        # the report, line by line
 *
 * Fails only if the transcript is wrong — speed is reported, not asserted, so a slow device
 * still produces numbers instead of a red test.
 */
@RunWith(AndroidJUnit4::class)
class SttBenchmarkTest {

    @Test
    fun benchmarkHindiAndEnglish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = SttBenchmark.runAll(context)
        report.lineSequence().forEach { Log.i("SttBenchmark", it) }
        assertTrue(
            "a transcript did not match its reference:\n$report",
            !report.contains("output correct:      false"),
        )
    }
}
