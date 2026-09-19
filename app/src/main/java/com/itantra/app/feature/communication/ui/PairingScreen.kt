package com.itantra.app.feature.communication.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itantra.app.core.pairing.DiscoveredPeer
import com.itantra.app.core.pairing.PairingConstants
import com.itantra.app.core.pairing.PairingPhase
import com.itantra.app.core.pairing.PairingRole
import com.itantra.app.core.pairing.PairingUiState
import com.itantra.app.core.pairing.TransportKind
import com.itantra.app.core.permissions.NearbyPermissions
import com.itantra.app.feature.pairing.PairingViewModel
import com.itantra.app.ui.theme.DeepDarkGreen
import com.itantra.app.ui.theme.GrayBorder
import com.itantra.app.ui.theme.OffWhite
import com.itantra.app.ui.theme.SoftLightGreen

private val PrimaryGreen = Color(0xFF19B878)
private val PrimaryText = Color(0xFF17231F)
private val SecondaryText = Color(0xFF71807A)

@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PairingScreenContent(
        state = state,
        onSelectTransport = viewModel::setTransport,
        onWait = viewModel::waitForTeammates,
        onFind = viewModel::findTeammates,
        onConnect = viewModel::connect,
        onConnectHotspot = viewModel::connectHotspot,
        onAccept = viewModel::accept,
        onReject = viewModel::reject,
        onDisconnect = viewModel::disconnect,
        onDiscoverableGranted = viewModel::markDiscoverableRefreshed
    )
}

@Composable
fun PairingScreenContent(
    state: PairingUiState,
    onSelectTransport: (TransportKind) -> Unit,
    onWait: (TransportKind) -> Unit,
    onFind: (TransportKind) -> Unit,
    onConnect: (DiscoveredPeer) -> Unit,
    onConnectHotspot: (String, String) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDisconnect: () -> Unit,
    onDiscoverableGranted: () -> Unit
) {
    val context = LocalContext.current
    var pendingRole by remember { mutableStateOf<PairingRole?>(null) }
    var hotspotSsid by remember { mutableStateOf("") }
    var hotspotPassword by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            pendingRole?.let { startRole(context, it, state.transport, onWait, onFind, onDiscoverableGranted) }
        }
    }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode != Activity.RESULT_CANCELED) {
            pendingRole?.let { startRole(context, it, state.transport, onWait, onFind, onDiscoverableGranted) }
        }
    }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode > 0) onDiscoverableGranted()
        onWait(TransportKind.BLUETOOTH)
    }

    fun requestThenStart(role: PairingRole) {
        pendingRole = role
        val needed = NearbyPermissions.allFor(state.transport == TransportKind.BLUETOOTH)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        if (state.transport == TransportKind.BLUETOOTH) {
            val enable = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && !adapter.isEnabled) {
                enableBtLauncher.launch(enable)
                return
            }
            if (role == PairingRole.WAIT) {
                val discover = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(
                        BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                        PairingConstants.DISCOVERABLE_SECONDS
                    )
                }
                discoverableLauncher.launch(discover)
                return
            }
        }
        startRole(context, role, state.transport, onWait, onFind, onDiscoverableGranted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .padding(bottom = 100.dp)
    ) {
        Text("Pairing", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DeepDarkGreen)
        Spacer(Modifier.height(4.dp))
        Text(
            "One phone waits. The other finds. No internet.",
            fontSize = 14.sp,
            color = SecondaryText
        )
        Spacer(Modifier.height(18.dp))

        PairingSegmentedControl(
            selected = if (state.transport == TransportKind.BLUETOOTH) 0 else 1,
            firstText = "Bluetooth",
            secondText = "Wi-Fi",
            onSelected = {
                onSelectTransport(if (it == 0) TransportKind.BLUETOOTH else TransportKind.WIFI)
            }
        )
        Spacer(Modifier.height(10.dp))
        PairingSegmentedControl(
            selected = when (state.role) {
                PairingRole.FIND -> 1
                else -> 0
            },
            firstText = "Wait for teammates",
            secondText = "Find teammates",
            onSelected = {
                requestThenStart(if (it == 0) PairingRole.WAIT else PairingRole.FIND)
            }
        )

        Spacer(Modifier.height(16.dp))
        StatusBanner(state)

        if (state.incoming != null) {
            Spacer(Modifier.height(12.dp))
            IncomingCard(
                name = state.incoming.fromName,
                onAccept = onAccept,
                onReject = onReject
            )
        }

        if (state.role == PairingRole.WAIT || state.role == null) {
            Spacer(Modifier.height(16.dp))
            WaitPanel(
                state = state,
                onBecomeVisible = { requestThenStart(PairingRole.WAIT) },
                onSwitchToFind = { requestThenStart(PairingRole.FIND) },
                onDisconnect = onDisconnect
            )
        }

        if (state.role == PairingRole.FIND) {
            Spacer(Modifier.height(16.dp))
            FindPanel(
                state = state,
                hotspotSsid = hotspotSsid,
                hotspotPassword = hotspotPassword,
                onSsid = { hotspotSsid = it },
                onPassword = { hotspotPassword = it },
                onConnect = onConnect,
                onConnectHotspot = { onConnectHotspot(hotspotSsid, hotspotPassword) },
                onSwitchToWait = { requestThenStart(PairingRole.WAIT) },
                onDisconnect = onDisconnect
            )
        }
    }
}

private fun startRole(
    context: android.content.Context,
    role: PairingRole,
    transport: TransportKind,
    onWait: (TransportKind) -> Unit,
    onFind: (TransportKind) -> Unit,
    onDiscoverableGranted: () -> Unit
) {
    if (transport == TransportKind.WIFI) {
        val wifiManager = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
        if (wifiManager?.isWifiEnabled == false) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }
    if (role == PairingRole.WAIT) {
        if (transport == TransportKind.BLUETOOTH) onDiscoverableGranted()
        onWait(transport)
    } else {
        onFind(transport)
    }
}

@Composable
private fun StatusBanner(state: PairingUiState) {
    val bg = when (state.phase) {
        PairingPhase.Connected -> SoftLightGreen.copy(alpha = 0.55f)
        PairingPhase.Failed -> Color(0xFFFFE8E6)
        else -> Color.White
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = when (state.phase) {
                    PairingPhase.Connected -> "CONNECTED"
                    PairingPhase.IncomingRequest -> "INCOMING REQUEST"
                    PairingPhase.Failed -> "NEEDS ATTENTION"
                    PairingPhase.Waiting -> "WAITING"
                    PairingPhase.Scanning -> "SCANNING"
                    PairingPhase.Connecting -> "CONNECTING"
                    PairingPhase.Idle -> "READY"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = SecondaryText
            )
            Spacer(Modifier.height(4.dp))
            Text(state.statusMessage, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
            if (state.lastPing != null) {
                Spacer(Modifier.height(4.dp))
                Text("Ping: ${state.lastPing}", fontSize = 12.sp, color = DeepDarkGreen)
            }
            Text(
                "${state.username}  ·  ${state.sessionCode}",
                fontSize = 12.sp,
                color = SecondaryText,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun IncomingCard(name: String, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("$name wants to join") },
        text = { Text("Accept only if this is your teammate standing next to you.") },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Accept") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Reject") }
        }
    )
}

@Composable
private fun WaitPanel(
    state: PairingUiState,
    onBecomeVisible: () -> Unit,
    onSwitchToFind: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        Text(
            if (state.role == PairingRole.WAIT) "You are hosting. Stay in range." else "You will host if you tap Wait.",
            fontSize = 13.sp,
            color = SecondaryText
        )
        if (state.hotspotSsid != null) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "Hotspot credentials",
                body = "SSID: ${state.hotspotSsid}\nPassword: ${state.hotspotPassword.orEmpty()}\nFriend taps Find → Wi-Fi → enter these."
            )
        }
        if (state.visibleUntilEpochMs != null && state.transport == TransportKind.BLUETOOTH) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Visible for about 2 minutes. Tap Wait again to become visible again.",
                fontSize = 12.sp,
                color = SecondaryText
            )
            Spacer(Modifier.height(8.dp))
            ActionButton("Become visible again", onBecomeVisible)
        }
        if (state.showSwitchToFind) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "No one joined",
                body = "If your friend is also waiting, one of you should tap Find teammates."
            )
            Spacer(Modifier.height(8.dp))
            ActionButton("Find teammates instead", onSwitchToFind)
        }
        if (state.peers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            state.peers.forEach { peer ->
                InfoCard(title = peer.name, body = "Linked over ${peer.transport.name.lowercase()}")
                Spacer(Modifier.height(8.dp))
            }
            ActionButton("Disconnect", onDisconnect, filled = false)
        }
    }
}

@Composable
private fun FindPanel(
    state: PairingUiState,
    hotspotSsid: String,
    hotspotPassword: String,
    onSsid: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConnect: (DiscoveredPeer) -> Unit,
    onConnectHotspot: () -> Unit,
    onSwitchToWait: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        Text(
            "${state.discovered.size} device(s) found",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = SecondaryText
        )
        Spacer(Modifier.height(10.dp))
        if (state.discovered.isEmpty()) {
            Text("Looking for names that start with iTantra- …", fontSize = 13.sp, color = SecondaryText)
        }
        state.discovered.forEach { peer ->
            DeviceRow(peer = peer, enabled = state.phase != PairingPhase.Connecting, onConnect = { onConnect(peer) })
            Spacer(Modifier.height(8.dp))
        }
        if (state.showSwitchToWait) {
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Nobody is waiting",
                body = "One of you should tap Wait for teammates."
            )
            Spacer(Modifier.height(8.dp))
            ActionButton("Wait for teammates instead", onSwitchToWait)
        }
        if (state.transport == TransportKind.WIFI) {
            Spacer(Modifier.height(16.dp))
            Text("Join host hotspot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DeepDarkGreen)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = hotspotSsid,
                onValueChange = onSsid,
                label = { Text("SSID from host screen") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = hotspotPassword,
                onValueChange = onPassword,
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            ActionButton("Connect via hotspot", onConnectHotspot)
        }
        if (state.peers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ActionButton("Disconnect", onDisconnect, filled = false)
        }
    }
}

@Composable
private fun DeviceRow(peer: DiscoveredPeer, enabled: Boolean, onConnect: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GrayBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(peer.name, fontWeight = FontWeight.Bold, color = PrimaryText, fontSize = 14.sp)
                Text(peer.transport.name.lowercase(), fontSize = 11.sp, color = SecondaryText)
            }
            Button(
                onClick = onConnect,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Connect", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = DeepDarkGreen, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(body, color = SecondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, filled: Boolean = true) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (filled) PrimaryGreen else Color.White,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, GrayBorder)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = if (filled) Color.White else DeepDarkGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun PairingSegmentedControl(
    selected: Int,
    firstText: String,
    secondText: String,
    onSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFE8EFEB)
    ) {
        Row(Modifier.fillMaxSize().padding(3.dp)) {
            SegmentItem(firstText, selected == 0, Modifier.weight(1f)) { onSelected(0) }
            SegmentItem(secondText, selected == 1, Modifier.weight(1f)) { onSelected(1) }
        }
    }
}

@Composable
private fun SegmentItem(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(if (selected) PrimaryGreen else Color.Transparent, RoundedCornerShape(21.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else SecondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PairingScreenPreview() {
    PairingScreenContent(
        state = PairingUiState(username = "Talha", sessionCode = "ITN-4827"),
        onSelectTransport = {},
        onWait = {},
        onFind = {},
        onConnect = {},
        onConnectHotspot = { _, _ -> },
        onAccept = {},
        onReject = {},
        onDisconnect = {},
        onDiscoverableGranted = {}
    )
}
