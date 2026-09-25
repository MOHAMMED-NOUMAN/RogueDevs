package com.itantra.app.core.sos

import android.location.Location
import com.itantra.app.core.location.LocationProvider
import com.itantra.app.core.messaging.SosCodec
import com.itantra.app.core.messaging.SosLocation
import com.itantra.app.core.messaging.SosPacket
import com.itantra.app.core.prefs.UserPreferences
import com.itantra.app.core.transport.Delivery
import com.itantra.app.core.transport.DeliveryStatus
import com.itantra.app.core.transport.LinkManager
import com.itantra.app.core.transport.PairingState
import com.itantra.app.core.transport.Priority
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The SOS this phone sent. */
data class OutgoingSos(
    val id: Int,
    val status: Status,
    val location: SosLocation?,
    /** Still waiting for a fresh GPS fix. */
    val locating: Boolean,
    /** Who said "I'm coming". */
    val responder: String? = null,
) {
    enum class Status { SENDING, DELIVERED, ACKNOWLEDGED, NOT_PAIRED, LINK_OFF, CANCELLED }

    val active: Boolean get() = status == Status.SENDING || status == Status.DELIVERED || status == Status.LINK_OFF
}

/** An SOS received from the teammate. */
data class IncomingSos(
    val id: Int,
    val sender: String,
    val location: SosLocation?,
    val sentAtEpochSeconds: Long,
    val state: State,
) {
    enum class State { RINGING, SILENCED, ACKNOWLEDGED, CANCELLED }
}

/**
 * SOS between the two paired phones.
 *
 * Sender: [trigger] sends an alert with the last known location straight away (priority SOS),
 * asks GPS for a fresh fix, and sends again every [RESEND_MS] (with the newest location) until
 * the teammate answers "I'm coming" or the SOS is [cancel]led.
 *
 * Receiver: the first alert rings [SosAlarm]; repeats only update the location. [acknowledge]
 * stops the alarm and answers; the answer is repeated if the sender keeps sending.
 */
@Singleton
class SosCenter @Inject constructor(
    private val link: LinkManager,
    private val prefs: UserPreferences,
    private val locations: LocationProvider,
    private val alarm: SosAlarm,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sendLoop: Job? = null
    private var lastDelivery: Delivery? = null

    private val _outgoing = MutableStateFlow<OutgoingSos?>(null)
    val outgoing: StateFlow<OutgoingSos?> = _outgoing.asStateFlow()

    private val _incoming = MutableStateFlow<IncomingSos?>(null)
    val incoming: StateFlow<IncomingSos?> = _incoming.asStateFlow()

    /** Sends an SOS to the paired teammate. Ignored while one is already active. */
    @Synchronized
    fun trigger() {
        if (_outgoing.value?.active == true) return
        val id = Random.nextInt(0x10000)
        val location = locations.lastKnown()?.toSos()
        if (link.pairing.value !is PairingState.Paired) {
            _outgoing.value = OutgoingSos(id, OutgoingSos.Status.NOT_PAIRED, location, locating = false)
            return
        }
        // An emergency shouldn't depend on the Offline Link switch: bring the link up.
        if (!link.linkRunning.value) link.startLink()
        _outgoing.value = OutgoingSos(id, OutgoingSos.Status.SENDING, location, locating = locations.hasPermission())
        lastDelivery = null
        sendLoop = scope.launch {
            launch {
                val fresh = locations.current(timeoutMs = 60_000)?.toSos()
                _outgoing.update { current ->
                    if (current?.id != id) current
                    else current.copy(location = fresh ?: current.location, locating = false)
                }
                if (fresh != null) sendAlert(force = true)
            }
            while (isActive) {
                sendAlert(force = false)
                delay(RESEND_MS)
            }
        }
    }

    /** Stops sending and tells the teammate the SOS is off. */
    @Synchronized
    fun cancel() {
        val current = _outgoing.value ?: return
        sendLoop?.cancel()
        sendLoop = null
        if (current.active) link.send(SosCodec.encode(SosPacket.Cancel(current.id)), Priority.SOS)
        _outgoing.value = current.copy(status = OutgoingSos.Status.CANCELLED, locating = false)
    }

    /** Clears a finished SOS from the screen. */
    fun clearOutgoing() {
        if (_outgoing.value?.active != true) _outgoing.value = null
    }

    /** "I'm coming": stops the alarm and answers the sender. */
    fun acknowledge() {
        val current = _incoming.value ?: return
        alarm.silence()
        alarm.clearNotification()
        _incoming.value = current.copy(state = IncomingSos.State.ACKNOWLEDGED)
        scope.launch { sendAck(current.id) }
    }

    /** Stops the sound and vibration; the alert stays on screen. */
    fun silence() {
        val current = _incoming.value ?: return
        alarm.silence()
        if (current.state == IncomingSos.State.RINGING) {
            _incoming.value = current.copy(state = IncomingSos.State.SILENCED)
            alarm.showNotification(current.sender, "Alarm silenced. Open iTantra to respond.", ringing = false)
        }
    }

    /** Closes a finished alert. */
    fun dismissIncoming() {
        alarm.silence()
        alarm.clearNotification()
        _incoming.value = null
    }

    /** Called by the message layer for every SOS packet from the link. */
    fun onPacket(bytes: ByteArray) {
        when (val packet = SosCodec.decode(bytes)) {
            is SosPacket.Alert -> onAlert(packet)
            is SosPacket.Ack -> synchronized(this) {
                val current = _outgoing.value
                if (current?.id == packet.id && current.active) {
                    sendLoop?.cancel()
                    sendLoop = null
                    _outgoing.value = current.copy(
                        status = OutgoingSos.Status.ACKNOWLEDGED,
                        responder = packet.responder,
                        locating = false,
                    )
                }
            }
            is SosPacket.Cancel -> {
                val current = _incoming.value ?: return
                if (current.id != packet.id) return
                alarm.silence()
                _incoming.value = current.copy(state = IncomingSos.State.CANCELLED)
                alarm.showNotification(current.sender, "${current.sender.ifBlank { "Your teammate" }} cancelled the SOS.", ringing = false)
            }
            null -> Unit
        }
    }

    private fun onAlert(alert: SosPacket.Alert) {
        val current = _incoming.value
        if (current != null && current.id == alert.id) {
            // A repeat: newer location, same alarm. If we already answered, answer again in
            // case the first answer was lost.
            _incoming.value = current.copy(
                location = alert.location ?: current.location,
                sentAtEpochSeconds = alert.sentAtEpochSeconds,
            )
            if (current.state == IncomingSos.State.ACKNOWLEDGED) scope.launch { sendAck(alert.id) }
            return
        }
        _incoming.value = IncomingSos(alert.id, alert.sender, alert.location, alert.sentAtEpochSeconds, IncomingSos.State.RINGING)
        alarm.ring()
        alarm.showNotification(
            alert.sender,
            if (alert.location != null) "Needs help. Tap to see where they are." else "Needs help. Tap to respond.",
            ringing = true,
        )
    }

    /** Sends one alert unless the previous one is still on its way ([force]: send anyway). */
    private suspend fun sendAlert(force: Boolean) {
        val current = _outgoing.value ?: return
        if (!current.active) return
        val pending = lastDelivery?.status?.value
        if (!force && pending != null && !pending.isFinal) return
        val packet = SosCodec.encode(
            SosPacket.Alert(current.id, prefs.profile.first().username, current.location, System.currentTimeMillis() / 1000)
        )
        val delivery = link.send(packet, Priority.SOS)
        if (delivery == null) {
            _outgoing.update { if (it?.id == current.id && it.active) it.copy(status = OutgoingSos.Status.LINK_OFF) else it }
            return
        }
        lastDelivery = delivery
        _outgoing.update { if (it?.id == current.id && it.status == OutgoingSos.Status.LINK_OFF) it.copy(status = OutgoingSos.Status.SENDING) else it }
        scope.launch {
            if (delivery.awaitResult() == DeliveryStatus.DELIVERED) {
                _outgoing.update {
                    if (it?.id == current.id && it.status == OutgoingSos.Status.SENDING) it.copy(status = OutgoingSos.Status.DELIVERED) else it
                }
            }
        }
    }

    private suspend fun sendAck(id: Int) {
        link.send(SosCodec.encode(SosPacket.Ack(id, prefs.profile.first().username)), Priority.SOS)
    }

    private fun Location.toSos() = SosLocation(latitude, longitude, if (hasAccuracy()) accuracy.toInt() else 0)

    private companion object {
        const val RESEND_MS = 8_000L
    }
}
