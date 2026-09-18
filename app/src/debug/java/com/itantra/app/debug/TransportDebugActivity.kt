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
import com.itantra.app.core.transport.Delivery
import com.itantra.app.core.transport.MAX_PACKET_SIZE
import com.itantra.app.core.transport.Priority
import com.itantra.app.core.transport.RfcommConnector
import com.itantra.app.core.transport.RfcommPairing
import com.itantra.app.core.transport.RfcommPeer
import com.itantra.app.core.transport.RfcommRole
import com.itantra.app.core.transport.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Debug-only screen for testing the transport layer between two real phones: pair over
 * Bluetooth, start the link, send packets at each priority, hold push-to-talk. Not product UI;
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

    val peer = MutableStateFlow<RfcommPeer?>(null)
    val status = MutableStateFlow("")
    val transport = MutableStateFlow<Transport?>(null)
    val sent = MutableStateFlow<List<Pair<String, Delivery>>>(emptyList())
    val received = MutableStateFlow<List<String>>(emptyList())

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context
        prefs = context.getSharedPreferences("transport_debug", Context.MODE_PRIVATE)
        val address = prefs.getString("address", null)
        val role = prefs.getString("role", null)?.let(RfcommRole::valueOf)
        if (address != null && role != null) peer.value = RfcommPeer(address, role)
    }

    fun host(code: String) = pair("Hosting code $code. Waiting for the other phone to join…") {
        RfcommPairing(appContext).host(code.encodeToByteArray())
    }

    fun join(code: String) = pair("Looking for the phone hosting $code (takes about 12 s)…") {
        RfcommPairing(appContext).join(code.encodeToByteArray())
    }

    private fun pair(message: String, block: suspend () -> RfcommPeer) {
        stop()
        pairingJob?.cancel()
        status.value = message
        pairingJob = scope.launch {
            try {
                val paired = block()
                prefs.edit().putString("address", paired.address).putString("role", paired.role.name).apply()
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
        if (transport.value != null) return
        val started = Transport(listOf(RfcommConnector(appContext, paired)), scope)
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

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

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
        OutlinedButton(onClick = { permissionLauncher.launch(bluetoothPermissions()) }) {
            Text("Grant Bluetooth permissions")
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
