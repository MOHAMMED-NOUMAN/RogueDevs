package com.itantra.app.feature.communication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.audio.VoiceRecorder
import com.itantra.app.core.ml.SpeechToText
import com.itantra.app.core.ml.Transcript
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Push-to-talk: Idle -> Recording -> Transcribing -> Transcribed (or Problem). */
sealed interface CommunicationUiState {
    data object Idle : CommunicationUiState
    data object Recording : CommunicationUiState
    data object Transcribing : CommunicationUiState
    data class Transcribed(val transcript: Transcript) : CommunicationUiState
    data class Problem(val message: String) : CommunicationUiState
}

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val recorder: VoiceRecorder,
    private val speechToText: SpeechToText,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommunicationUiState>(CommunicationUiState.Idle)
    val uiState: StateFlow<CommunicationUiState> = _uiState.asStateFlow()

    /** Starts recording. The caller has already checked the microphone permission. */
    fun onPushToTalkPressed() {
        if (_uiState.value is CommunicationUiState.Transcribing) return
        _uiState.value = if (recorder.start()) CommunicationUiState.Recording
        else CommunicationUiState.Problem("Couldn't open the microphone.")
    }

    /** Stops recording and shows what was heard. Nothing is sent to the teammate yet. */
    fun onPushToTalkReleased() {
        if (_uiState.value !is CommunicationUiState.Recording) return
        viewModelScope.launch {
            val pcm = recorder.stop()
            if (pcm.size < MIN_SAMPLES) {
                _uiState.value = CommunicationUiState.Problem("Hold the button while you speak.")
                return@launch
            }
            _uiState.value = CommunicationUiState.Transcribing
            _uiState.value = try {
                val transcript = speechToText.transcribe(pcm)
                if (transcript.text.isBlank()) CommunicationUiState.Problem("Didn't catch that. Try again.")
                else CommunicationUiState.Transcribed(transcript)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CommunicationUiState.Problem("Speech-to-text failed: ${e.message ?: e}")
            }
        }
    }

    fun onMicPermissionDenied() {
        _uiState.value = CommunicationUiState.Problem("Allow microphone access to use Hold to Talk.")
    }

    private companion object {
        /** Presses shorter than half a second are taps, not speech. */
        const val MIN_SAMPLES = VoiceRecorder.SAMPLE_RATE / 2
    }
}
