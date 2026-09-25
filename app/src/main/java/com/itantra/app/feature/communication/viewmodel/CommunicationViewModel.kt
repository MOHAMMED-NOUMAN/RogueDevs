package com.itantra.app.feature.communication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.audio.VoiceRecorder
import com.itantra.app.core.messaging.IncomingMessage
import com.itantra.app.core.messaging.MessageCenter
import com.itantra.app.core.messaging.SendStatus
import com.itantra.app.core.ml.SpeechToText
import com.itantra.app.core.ml.Transcript
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Push-to-talk: Idle -> Recording -> Transcribing -> Transcribed (or Problem). A transcript is
 * sent to the teammate straight away; [Transcribed.sendStatus] follows it.
 */
sealed interface CommunicationUiState {
    data object Idle : CommunicationUiState
    data object Recording : CommunicationUiState
    data object Transcribing : CommunicationUiState
    data class Transcribed(
        val transcript: Transcript,
        val sendStatus: SendStatus = SendStatus.SENDING,
    ) : CommunicationUiState
    data class Problem(val message: String) : CommunicationUiState
}

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val recorder: VoiceRecorder,
    private val speechToText: SpeechToText,
    private val messages: MessageCenter,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommunicationUiState>(CommunicationUiState.Idle)
    val uiState: StateFlow<CommunicationUiState> = _uiState.asStateFlow()

    /** Messages from the teammate, newest first. */
    val incoming: StateFlow<List<IncomingMessage>> = messages.incoming
    private var sendJob: Job? = null

    /** Starts recording. The caller has already checked the microphone permission. */
    fun onPushToTalkPressed() {
        if (_uiState.value is CommunicationUiState.Transcribing) return
        _uiState.value = if (recorder.start()) CommunicationUiState.Recording
        else CommunicationUiState.Problem("Couldn't open the microphone.")
    }

    /** Stops recording, shows what was heard and sends it to the teammate. */
    fun onPushToTalkReleased() {
        if (_uiState.value !is CommunicationUiState.Recording) return
        viewModelScope.launch {
            val pcm = recorder.stop()
            if (pcm.size < MIN_SAMPLES) {
                _uiState.value = CommunicationUiState.Problem("Hold the button while you speak.")
                return@launch
            }
            _uiState.value = CommunicationUiState.Transcribing
            val transcript = try {
                speechToText.transcribe(pcm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CommunicationUiState.Problem("Speech-to-text failed: ${e.message ?: e}")
                return@launch
            }
            if (transcript.text.isBlank()) {
                _uiState.value = CommunicationUiState.Problem("Didn't catch that. Try again.")
                return@launch
            }
            val shown = CommunicationUiState.Transcribed(transcript)
            _uiState.value = shown
            send(shown)
        }
    }

    /** Sends the transcript and keeps its card's status current until the next message. */
    private fun send(shown: CommunicationUiState.Transcribed) {
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            messages.send(shown.transcript.text, shown.transcript.language).collect { status ->
                _uiState.update { current ->
                    if (current is CommunicationUiState.Transcribed && current.transcript == shown.transcript) {
                        current.copy(sendStatus = status)
                    } else current
                }
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
