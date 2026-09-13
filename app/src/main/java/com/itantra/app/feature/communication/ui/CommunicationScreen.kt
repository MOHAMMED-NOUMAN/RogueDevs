package com.itantra.app.feature.communication.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itantra.app.feature.communication.viewmodel.CommunicationUiState
import com.itantra.app.feature.communication.viewmodel.CommunicationViewModel

/**
 * Push-to-talk screen — UI shell only. Wire this up to the real audio
 * capture + STT pipeline once that layer exists; the button already
 * calls the right ViewModel hooks.
 */
@Composable
fun CommunicationScreen(viewModel: CommunicationViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            val label = when (uiState) {
                CommunicationUiState.Idle -> "Hold to talk"
                CommunicationUiState.Recording -> "Listening…"
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(32.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                viewModel.onPushToTalkPressed()
                                tryAwaitRelease()
                                viewModel.onPushToTalkReleased()
                            }
                        )
                    },
            ) {
                Box(modifier = Modifier.padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(text = label, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
