package com.itantra.app.core.ml

import android.content.Context
import com.itantra.app.core.prefs.SpeechLanguage
import com.itantra.app.core.prefs.UserPreferences
import com.itantra.stt.WhisperStt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What Hold to Talk heard. */
data class Transcript(
    val text: String,
    val language: SpeechLanguage,
    val speechSeconds: Float,
    val convertMs: Long,
)

/**
 * Offline speech-to-text for Hold to Talk, in the language chosen in Settings. The model for
 * that language is loaded ahead of time (and swapped when the setting changes), so the first
 * message doesn't wait for it. One model is in memory at a time.
 */
@Singleton
class SpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var language: SpeechLanguage? = null
    private var model: WhisperStt? = null

    init {
        scope.launch {
            prefs.speechLanguage.collectLatest { lang -> lock.withLock { load(lang) } }
        }
    }

    /** Converts 16 kHz mono samples to text. Runs off the main thread. */
    suspend fun transcribe(pcm16k: FloatArray): Transcript = withContext(Dispatchers.Default) {
        lock.withLock {
            val lang = prefs.speechLanguage.first()
            val stt = load(lang)
            val result = stt.transcribe(pcm16k)
            Transcript(
                text = result.text,
                language = lang,
                speechSeconds = pcm16k.size / 16_000f,
                convertMs = result.totalMs,
            )
        }
    }

    /** Call with [lock] held. */
    private fun load(lang: SpeechLanguage): WhisperStt {
        model?.takeIf { language == lang }?.let { return it }
        model?.close()
        model = null
        return WhisperStt(context, assetDir(lang)).also {
            model = it
            language = lang
        }
    }

    private fun assetDir(lang: SpeechLanguage) = when (lang) {
        SpeechLanguage.ENGLISH -> "whisper-en"
        SpeechLanguage.HINDI -> "whisper-hi"
    }
}
