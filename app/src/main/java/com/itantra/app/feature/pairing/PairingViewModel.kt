package com.itantra.app.feature.pairing

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.pairing.DiscoveredPeer
import com.itantra.app.core.pairing.PairingController
import com.itantra.app.core.pairing.PairingService
import com.itantra.app.core.pairing.PairingUiState
import com.itantra.app.core.pairing.TransportKind
import com.itantra.app.core.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: PairingController,
    private val prefs: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<PairingUiState> = controller.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PairingUiState()
    )

    fun saveUsername(name: String) {
        viewModelScope.launch { prefs.setUsername(name) }
    }

    fun setTransport(kind: TransportKind) = controller.setTransport(kind)

    fun waitForTeammates(kind: TransportKind) {
        startService()
        controller.waitForTeammates(kind)
    }

    fun findTeammates(kind: TransportKind) {
        startService()
        controller.findTeammates(kind)
    }

    fun connect(peer: DiscoveredPeer) = controller.connectTo(peer)

    fun connectHotspot(ssid: String, password: String) = controller.connectHotspot(ssid, password)

    fun accept() = controller.acceptIncoming()

    fun reject() = controller.rejectIncoming()

    fun disconnect() {
        controller.disconnectAll()
        context.stopService(Intent(context, PairingService::class.java))
    }

    fun markDiscoverableRefreshed() = controller.refreshDiscoverableClock()

    private fun startService() {
        val intent = Intent(context, PairingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
