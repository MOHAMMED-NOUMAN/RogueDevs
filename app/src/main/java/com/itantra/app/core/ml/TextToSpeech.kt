package com.itantra.app.core.ml

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.itantra.app.core.messaging.IncomingMessage
import com.itantra.app.core.messaging.MessageLanguage
import com.itantra.app.core.messaging.toMessageLanguage
import com.itantra.app.core.prefs.UserPreferences
import com.itantra.tts.MmsTts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What reading aloud is doing. [Speaking.source] and [Problem.source] are what was read (an
 * [IncomingMessage], or the user's own [Transcript]), so each card can tell it is the one.
 */
sealed interface ReadAloudState {
    data object Idle : ReadAloudState
    data class Speaking(val source: Any) : ReadAloudState
    data class Problem(val source: Any, val reason: String) : ReadAloudState
}

/**
 * Reads the teammate's messages aloud, each in its own language, with the offline MMS voices.
 * Messages are spoken one after another in arrival order; [playNow] interrupts (Play again,
 * and Play aloud for the user's own transcript, to try the voices on one phone).
 * One voice is in memory at a time (the Settings language is loaded ahead of time), and long
 * messages are spoken sentence by sentence, the next one synthesized while the current plays.
 */
@Singleton
class TextToSpeech @Inject constructor(
    @ApplicationContext private val context: Context,
    prefs: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val voiceLock = Mutex()
    private var voiceLanguage: MessageLanguage? = null
    private var voice: MmsTts? = null

    private class Item(val source: Any, val text: String, val language: MessageLanguage)

    private val pending = ArrayDeque<Item>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var playing: Job? = null

    private val _state = MutableStateFlow<ReadAloudState>(ReadAloudState.Idle)
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    init {
        scope.launch {
            val preload = prefs.speechLanguage.first().toMessageLanguage()
            runCatching { voiceLock.withLock { load(preload) } }
                .onFailure { Log.w(TAG, "Couldn't preload the $preload voice", it) }
        }
        scope.launch {
            while (true) {
                val job = synchronized(pending) {
                    pending.removeFirstOrNull()?.let { next -> launch { play(next) }.also { playing = it } }
                }
                if (job == null) wake.receive() else job.join()
            }
        }
    }

    /** Reads [message] once the ones before it have been read. */
    fun speak(message: IncomingMessage) {
        synchronized(pending) { pending.addLast(Item(message, message.text, message.language)) }
        wake.trySend(Unit)
    }

    /** Stops whatever is playing and reads [text] now; queued messages follow. */
    fun playNow(source: Any, text: String, language: MessageLanguage) {
        val interrupted = synchronized(pending) {
            pending.addFirst(Item(source, text, language))
            playing
        }
        interrupted?.cancel()
        wake.trySend(Unit)
    }

    private suspend fun play(item: Item) {
        _state.value = ReadAloudState.Speaking(item.source)
        try {
            val sentences = SpeakableText.chunks(item.text, item.language)
            if (sentences.isNotEmpty()) speakSentences(sentences, item.language)
            _state.value = ReadAloudState.Idle
        } catch (e: CancellationException) {
            _state.value = ReadAloudState.Idle
            throw e
        } catch (e: Throwable) { // OutOfMemoryError included: a voice needs ~120 MB
            Log.e(TAG, "Reading aloud failed", e)
            _state.value = ReadAloudState.Problem(item.source, e.message ?: e.toString())
        }
    }

    /** Synthesizes the next sentence while the current one plays. */
    private suspend fun speakSentences(sentences: List<String>, language: MessageLanguage) = coroutineScope {
        val audio = Channel<FloatArray>(capacity = 1)
        var sampleRate = 0
        launch {
            for (sentence in sentences) {
                val samples = voiceLock.withLock {
                    val v = load(language)
                    sampleRate = v.sampleRate
                    v.synthesize(sentence)
                }
                audio.send(samples)
            }
            audio.close()
        }
        val first = audio.receive()
        val track = newTrack(sampleRate)
        try {
            track.play()
            var written = writeAll(track, first)
            for (samples in audio) {
                written += writeAll(track, FloatArray(sampleRate * SENTENCE_GAP_MS / 1000))
                written += writeAll(track, samples)
            }
            // Let the buffered end play out before releasing the track.
            withTimeoutOrNull(DRAIN_TIMEOUT_MS) {
                while (track.playbackHeadPosition < written) delay(20)
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }

    /** Writes in short blocks so a cancel (Play again) stops playback within one block. */
    private suspend fun writeAll(track: AudioTrack, samples: FloatArray): Int = coroutineScope {
        var offset = 0
        while (offset < samples.size) {
            ensureActive()
            val count = minOf(WRITE_BLOCK, samples.size - offset)
            val wrote = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            check(wrote >= 0) { "AudioTrack write failed: $wrote" }
            offset += wrote
        }
        samples.size
    }

    private fun newTrack(sampleRate: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, WRITE_BLOCK * 4 * 2))
            .build()
    }

    /** Call with [voiceLock] held. */
    private fun load(language: MessageLanguage): MmsTts {
        voice?.takeIf { voiceLanguage == language }?.let { return it }
        voice?.close()
        voice = null
        voiceLanguage = null
        val started = System.currentTimeMillis()
        return MmsTts(context, assetDir(language)).also {
            voice = it
            voiceLanguage = language
            Log.i(TAG, "Loaded the $language voice in ${System.currentTimeMillis() - started} ms")
        }
    }

    private fun assetDir(language: MessageLanguage) = when (language) {
        MessageLanguage.ENGLISH -> "mms-tts-en"
        MessageLanguage.HINDI -> "mms-tts-hi"
    }

    private companion object {
        const val TAG = "TextToSpeech"

        /** 100 ms at 16 kHz. */
        const val WRITE_BLOCK = 1_600

        /** Silence between sentences. */
        const val SENTENCE_GAP_MS = 200

        /** Far longer than the track's buffer takes to play out. */
        const val DRAIN_TIMEOUT_MS = 2_000L
    }
}
