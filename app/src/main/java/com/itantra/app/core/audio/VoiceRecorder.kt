package com.itantra.app.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Records the microphone for push-to-talk as 16 kHz mono, the format the speech model expects.
 * Stops by itself after [MAX_SECONDS], the longest clip the model can hear.
 */
@Singleton
class VoiceRecorder @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recording: Job? = null

    @Volatile
    private var captured = ShortArray(0)

    /** Starts recording; false if the microphone couldn't be opened. Needs RECORD_AUDIO. */
    @SuppressLint("MissingPermission") // callers check RECORD_AUDIO before starting
    fun start(): Boolean {
        if (recording?.isActive == true) return true
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return false
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer, SAMPLE_RATE / 5 * 2), // at least 200 ms of 16-bit audio
            )
        }.getOrNull() ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        captured = ShortArray(0)
        record.startRecording()
        recording = scope.launch {
            val out = ShortArray(MAX_SAMPLES)
            val chunk = ShortArray(minBuffer)
            var size = 0
            try {
                while (isActive && size < MAX_SAMPLES) {
                    val read = record.read(chunk, 0, minOf(chunk.size, MAX_SAMPLES - size))
                    if (read < 0) break
                    chunk.copyInto(out, size, 0, read)
                    size += read
                }
            } finally {
                record.stop()
                record.release()
                captured = out.copyOf(size)
            }
        }
        return true
    }

    /** Stops recording and returns the clip as samples in -1..1. */
    suspend fun stop(): FloatArray {
        recording?.cancelAndJoin()
        recording = null
        val pcm = captured
        return FloatArray(pcm.size) { pcm[it] / 32768f }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 30
        private const val MAX_SAMPLES = SAMPLE_RATE * MAX_SECONDS
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
