package com.itantra.app.debug

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itantra.app.core.transport.BeaconStart
import com.itantra.app.core.transport.BleBeacon
import com.itantra.app.core.transport.Delivery
import com.itantra.app.core.transport.MAX_PACKET_SIZE
import com.itantra.app.core.transport.Priority
import com.itantra.app.core.transport.RfcommConnector
import com.itantra.app.core.transport.RfcommPairing
import com.itantra.app.core.transport.PeerRole
import com.itantra.app.core.transport.RfcommPeer
import com.itantra.app.core.transport.Transport
import com.itantra.app.core.transport.TransportService
import com.itantra.app.core.transport.WifiDirectConnector
import com.itantra.app.core.transport.WifiDirectPeer
import com.itantra.app.core.transport.placeholderSosPayload
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Debug-only screen for testing the transport layer between two real phones: pair, start the
 * link (Wi-Fi Direct first, Bluetooth fallback), send packets at each priority, hold
 * push-to-talk, and send or listen for the SOS beacon. Not product UI;
 * release builds don't contain it. Packets here are plain text because there's no crypto layer
 * yet; in the app they arrive already encrypted.
 */
class TransportDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DebugTransport.init(applicationContext)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { TransportDebugScreen() }
            }
        }
    }
}

/** Lives as long as the process, so rotating the screen doesn't drop the link. */
private object DebugTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private var pairingJob: Job? = null
    private var receiveJob: Job? = null
    private var scanJob: Job? = null
    private var pairingCode: String? = null
    private val beacon by lazy { BleBeacon(appContext) }
    private var sosSequence = 0

    val peer = MutableStateFlow<RfcommPeer?>(null)
    val status = MutableStateFlow("")
    val transport = MutableStateFlow<Transport?>(null)
    val sent = MutableStateFlow<List<Pair<String, Delivery>>>(emptyList())
    val received = MutableStateFlow<List<String>>(emptyList())
    val beaconOn = MutableStateFlow(false)
    val listening = MutableStateFlow(false)
    val beaconsHeard = MutableStateFlow<List<String>>(emptyList())

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context
        prefs = context.getSharedPreferences("transport_debug", Context.MODE_PRIVATE)
        val address = prefs.getString("address", null)
        val role = prefs.getString("role", null)?.let(PeerRole::valueOf)
        pairingCode = prefs.getString("code", null)
        if (address != null && role != null && pairingCode != null) peer.value = RfcommPeer(address, role)
    }

    fun host(code: String) = pair(code, "Hosting code $code. Waiting for the other phone to join…") {
        RfcommPairing(appContext).host(code.encodeToByteArray())
    }

    fun join(code: String) = pair(code, "Looking for the phone hosting $code (takes about 12 s)…") {
        RfcommPairing(appContext).join(code.encodeToByteArray())
    }

    private fun pair(code: String, message: String, block: suspend () -> RfcommPeer) {
        stop()
        pairingJob?.cancel()
        status.value = message
        pairingJob = scope.launch {
            try {
                val paired = block()
                prefs.edit()
                    .putString("address", paired.address)
                    .putString("role", paired.role.name)
                    .putString("code", code)
                    .apply()
                pairingCode = code
                peer.value = paired
                status.value = "Paired with ${paired.address}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.value = "Pairing failed: ${e.message ?: e}"
            }
        }
    }

    fun forget() {
        stop()
        prefs.edit().clear().apply()
        peer.value = null
    }

    fun start() {
        val paired = peer.value ?: return
        val code = pairingCode ?: return
        if (transport.value != null) return
        // Preference order: Wi-Fi Direct first, Bluetooth as the fallback.
        val connectors = listOf(
            WifiDirectConnector(appContext, WifiDirectPeer.fromPairingCode(code.encodeToByteArray(), paired.role)),
            RfcommConnector(appContext, paired),
        )
        val started = Transport(connectors, scope)
        TransportService.start(appContext)
        receiveJob = scope.launch {
            started.received.collect { packet -> received.update { it + packet.decodeToString() } }
        }
        started.start()
        transport.value = started
    }

    fun stop() {
        transport.value?.stop()
        receiveJob?.cancel()
        transport.value = null
        if (::appContext.isInitialized) TransportService.stop(appContext)
    }

    fun toggleBeacon() {
        if (beaconOn.value) {
            beacon.stopAdvertising()
            beaconOn.value = false
            return
        }
        scope.launch {
            val payload = placeholderSosPayload(senderId(), ++sosSequence)
            when (val result = beacon.startAdvertising(payload)) {
                BeaconStart.Started -> {
                    beaconOn.value = true
                    status.value = "SOS beacon on"
                }
                BeaconStart.Unsupported -> {
                    status.value = "This phone can't advertise over BLE; sending SOS over the link instead"
                    transport.value?.send(payload, Priority.SOS)
                }
                is BeaconStart.Failed -> status.value = "SOS beacon failed: ${result.reason}"
            }
        }
    }

    fun toggleListening() {
        if (scanJob != null) {
            scanJob?.cancel()
            scanJob = null
            listening.value = false
            return
        }
        listening.value = true
        scanJob = scope.launch {
            try {
                beacon.scan().collect { heard ->
                    val sender = ByteBuffer.wrap(heard.payload, 1, 4).int
                    val line = "sender ${Integer.toHexString(sender)}  RSSI ${heard.rssi} dBm"
                    beaconsHeard.update { (listOf(line) + it).take(20) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.value = "Beacon scan failed: ${e.message ?: e}"
            } finally {
                listening.value = false
                scanJob = null
            }
        }
    }

    /** Random per install, so two test phones' beacons can be told apart. */
    private fun senderId(): Int {
        val saved = prefs.getInt("sender", 0)
        if (saved != 0) return saved
        return Random.nextInt().also { prefs.edit().putInt("sender", it).apply() }
    }

    fun send(text: String, priority: Priority) {
        val current = transport.value ?: return
        val packet = text.encodeToByteArray()
        if (packet.size !in 1..MAX_PACKET_SIZE) {
            status.value = "Message must be 1 to $MAX_PACKET_SIZE bytes"
            return
        }
        sent.update { it + ("$priority: $text" to current.send(packet, priority)) }
    }
}

/** Everything transport needs at runtime on this Android version. */
private fun transportPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        // Wi-Fi Direct up to Android 12L, and Bluetooth discovery up to 11, need location.
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}.toTypedArray()

@Composable
private fun TransportDebugScreen() {
    val status by DebugTransport.status.collectAsState()
    val peer by DebugTransport.peer.collectAsState()
    val transport by DebugTransport.transport.collectAsState()
    val sent by DebugTransport.sent.collectAsState()
    val received by DebugTransport.received.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys.map { it.substringAfterLast('.') }
        DebugTransport.status.value =
            if (denied.isEmpty()) "Permissions granted" else "Denied: ${denied.joinToString()}"
    }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            DebugTransport.status.value = "The phone must be visible to host a pairing"
        } else {
            DebugTransport.host(code)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Transport debug", style = MaterialTheme.typography.titleLarge)
        if (status.isNotEmpty()) Text(status)
        OutlinedButton(onClick = { permissionLauncher.launch(transportPermissions()) }) {
            Text("Grant permissions")
        }

        Heading("1. Pair (once per pair of phones)")
        Text(
            "Type the same 4 digits on both phones. Tap Host on one, then Join on the other.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(4) },
            label = { Text("Pairing code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = code.length == 4,
                onClick = { discoverableLauncher.launch(RfcommPairing.discoverableIntent()) },
            ) { Text("Host") }
            Button(enabled = code.length == 4, onClick = { DebugTransport.join(code) }) { Text("Join") }
        }
        Text(peer?.let { "Paired with ${it.address}, this phone ${it.role.name.lowercase()}s" } ?: "Not paired")
        if (peer != null) OutlinedButton(onClick = DebugTransport::forget) { Text("Forget peer") }

        Heading("2. Link")
        val current = transport
        if (current == null) {
            Button(enabled = peer != null, onClick = DebugTransport::start) { Text("Start link") }
        } else {
            val link by current.linkStatus.collectAsState()
            val peerTransmitting by current.peerTransmitting.collectAsState()
            OutlinedButton(onClick = DebugTransport::stop) { Text("Stop link") }
            Text("Link: $link")
            Text(if (peerTransmitting) "Peer is transmitting" else "Peer is not transmitting")
        }

        Heading("3. Send")
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { priority ->
                Button(
                    enabled = current != null && message.isNotEmpty(),
                    onClick = { DebugTransport.send(message, priority) },
                ) { Text(priority.name) }
            }
        }
        if (current != null) PushToTalk(current)

        Heading("4. SOS beacon (no pairing needed)")
        val beaconOn by DebugTransport.beaconOn.collectAsState()
        val listening by DebugTransport.listening.collectAsState()
        val beaconsHeard by DebugTransport.beaconsHeard.collectAsState()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = DebugTransport::toggleBeacon) {
                Text(if (beaconOn) "Stop SOS beacon" else "Send SOS beacon")
            }
            OutlinedButton(onClick = DebugTransport::toggleListening) {
                Text(if (listening) "Stop listening" else "Listen")
            }
        }
        if (beaconsHeard.isEmpty()) {
            Text(
                if (listening) "Listening for SOS beacons…" else "No beacons heard",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        beaconsHeard.forEach { Text(it) }

        Heading("Sent")
        if (sent.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall)
        sent.asReversed().forEach { (label, delivery) ->
            key(delivery) {
                val deliveryStatus by delivery.status.collectAsState()
                Text("$label  →  $deliveryStatus")
            }
        }

        Heading("Received")
        if (received.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall)
        received.asReversed().forEach { Text(it) }
    }
}

@Composable
private fun PushToTalk(transport: Transport) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        color = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(transport) {
                detectTapGestures(onPress = {
                    pressed = true
                    transport.setTransmitting(true)
                    tryAwaitRelease()
                    transport.setTransmitting(false)
                    pressed = false
                })
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (pressed) "Transmitting… release to stop" else "Hold to signal push-to-talk")
        }
    }
}

@Composable
private fun Heading(text: String) {
    HorizontalDivider()
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
