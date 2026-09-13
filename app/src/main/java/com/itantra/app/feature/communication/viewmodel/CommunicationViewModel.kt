package com.itantra.app.feature.communication.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Placeholder state for the push-to-talk screen. Replace with real
 * states once the STT pipeline exists: Idle -> Recording -> Transcribing
 * -> Sending -> Delivered / Failed.
 */
sealed interface CommunicationUiState {
    data object Idle : CommunicationUiState
    data object Recording : CommunicationUiState
}

@HiltViewModel
class CommunicationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<CommunicationUiState>(CommunicationUiState.Idle)
    val uiState: StateFlow<CommunicationUiState> = _uiState.asStateFlow()

    fun onPushToTalkPressed() {
        _uiState.value = CommunicationUiState.Recording
        // TODO: start AudioRecord capture, hand result to the STT pipeline
    }

    fun onPushToTalkReleased() {
        _uiState.value = CommunicationUiState.Idle
        // TODO: stop capture, run offline STT, send resulting text over the transport layer
    }
}
