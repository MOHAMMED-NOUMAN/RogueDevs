package com.itantra.app.feature.pairing

import androidx.lifecycle.ViewModel
import com.itantra.app.core.transport.LinkManager
import com.itantra.app.core.transport.LinkStatus
import com.itantra.app.core.transport.PairingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Pairing and link state for the Pairing, Nearby and Home screens. */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val linkManager: LinkManager,
) : ViewModel() {

    val pairing: StateFlow<PairingState> = linkManager.pairing
    val pairingError: StateFlow<String?> = linkManager.pairingError
    val linkStatus: StateFlow<LinkStatus> = linkManager.linkStatus
    val linkRunning: StateFlow<Boolean> = linkManager.linkRunning

    /** The code this phone shows while hosting; new each time the app is opened. */
    val hostCode: String = LinkManager.newPairingCode()

    fun host() = linkManager.host(hostCode)

    fun join(code: String) = linkManager.join(code)

    fun cancelPairing() = linkManager.cancelPairing()

    fun forget() = linkManager.forget()

    /** Brings the link up if this phone is paired and it isn't running yet. */
    fun ensureLinkRunning() = linkManager.startLink()

    fun stopLink() = linkManager.stopLink()
}
