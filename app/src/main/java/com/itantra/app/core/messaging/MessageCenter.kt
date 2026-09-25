package com.itantra.app.core.messaging

import com.itantra.app.core.sos.SosCenter
import com.itantra.app.core.prefs.SpeechLanguage
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A message from the teammate, as shown on Home. */
data class IncomingMessage(
    val sender: String,
    val language: MessageLanguage,
    val text: String,
    val receivedAtMs: Long,
)

/** Where a sent message is, for the sender's "You said" card. */
enum class SendStatus {
    /** No teammate paired. */
    NOT_PAIRED,

    /** Paired, but the link is switched off (Settings > Offline Link). */
    LINK_OFF,

    /** Queued until the link comes back. */
    WAITING,

    /** On the link, waiting for the teammate's phone to confirm. */
    SENDING,
    DELIVERED,
    FAILED,
}

/**
 * Text messages to and from the paired teammate over [LinkManager]: splits outgoing text into
 * packets, reassembles incoming ones and keeps the latest [KEEP] received messages in memory.
 * It is the one reader of the link; SOS packets are handed to [SosCenter].
 */
@Singleton
class MessageCenter @Inject constructor(
    private val link: LinkManager,
    private val prefs: UserPreferences,
    private val sos: SosCenter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val assembler = MessageAssembler()
    private var nextId = Random.nextInt(0x10000)

    private val _incoming = MutableStateFlow<List<IncomingMessage>>(emptyList())

    /** Newest first. */
    val incoming: StateFlow<List<IncomingMessage>> = _incoming.asStateFlow()

    init {
        scope.launch {
            link.received.collect { packet ->
                if (SosCodec.isSos(packet)) {
                    sos.onPacket(packet)
                    return@collect
                }
                val part = TextMessageCodec.decodePart(packet) ?: return@collect
                val message = synchronized(assembler) { assembler.add(part, System.currentTimeMillis()) }
                    ?: return@collect
                val shown = IncomingMessage(message.sender, message.language, message.text, System.currentTimeMillis())
                _incoming.update { (listOf(shown) + it).take(KEEP) }
            }
        }
    }

    /** Sends [text] to the teammate; the flow follows it until delivered or failed. */
    suspend fun send(text: String, language: SpeechLanguage): Flow<SendStatus> {
        if (link.pairing.value !is PairingState.Paired) return flowOf(SendStatus.NOT_PAIRED)
        if (!link.linkRunning.value) return flowOf(SendStatus.LINK_OFF)

        val sender = prefs.profile.first().username
        val id = synchronized(this) { nextId.also { nextId = (it + 1) and 0xFFFF } }
        val message = TextMessage(id, sender, language.toMessageLanguage(), text)
        val deliveries: List<Delivery> = TextMessageCodec.encode(message).map { packet ->
            link.send(packet, Priority.NORMAL) ?: return flowOf(SendStatus.LINK_OFF)
        }
        return combine(deliveries.map { it.status }) { statuses -> overall(statuses.toList()) }
    }

    private fun overall(parts: List<DeliveryStatus>): SendStatus = when {
        parts.any { it == DeliveryStatus.FAILED } -> SendStatus.FAILED
        parts.all { it == DeliveryStatus.DELIVERED } -> SendStatus.DELIVERED
        parts.all { it == DeliveryStatus.QUEUED } -> SendStatus.WAITING
        else -> SendStatus.SENDING
    }

    private fun SpeechLanguage.toMessageLanguage() = when (this) {
        SpeechLanguage.ENGLISH -> MessageLanguage.ENGLISH
        SpeechLanguage.HINDI -> MessageLanguage.HINDI
    }

    private companion object {
        const val KEEP = 20
    }
}
