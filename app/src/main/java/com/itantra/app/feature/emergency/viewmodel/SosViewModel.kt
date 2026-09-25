package com.itantra.app.feature.emergency.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.location.LocationProvider
import com.itantra.app.core.sos.IncomingSos
import com.itantra.app.core.sos.OutgoingSos
import com.itantra.app.core.sos.SosCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SosViewModel @Inject constructor(
    private val sos: SosCenter,
    private val locations: LocationProvider,
) : ViewModel() {

    val outgoing: StateFlow<OutgoingSos?> = sos.outgoing
    val incoming: StateFlow<IncomingSos?> = sos.incoming

    /** This phone's position, for "how far and which way" on an incoming SOS. */
    private val _myLocation = MutableStateFlow<Location?>(null)
    val myLocation: StateFlow<Location?> = _myLocation.asStateFlow()

    fun trigger() = sos.trigger()
    fun cancel() = sos.cancel()
    fun clearOutgoing() = sos.clearOutgoing()
    fun acknowledge() = sos.acknowledge()
    fun silence() = sos.silence()
    fun dismissIncoming() = sos.dismissIncoming()

    fun hasLocationPermission() = locations.hasPermission()

    /** Refreshes [myLocation]: the last known fix at once, then a fresh one if GPS gets it. */
    fun refreshMyLocation() {
        _myLocation.value = locations.lastKnown() ?: _myLocation.value
        viewModelScope.launch { locations.current(timeoutMs = 30_000)?.let { _myLocation.value = it } }
    }
}
