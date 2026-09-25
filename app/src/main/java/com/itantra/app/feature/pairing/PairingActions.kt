package com.itantra.app.feature.pairing

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.itantra.app.core.permissions.LinkPermissions
import com.itantra.app.core.transport.LinkKind
import com.itantra.app.core.transport.LinkStatus
import com.itantra.app.core.transport.PairingState
import com.itantra.app.core.transport.RfcommPairing

/**
 * Host and Join with their Android prerequisites handled: runtime permissions first, then
 * Bluetooth on (Join) or the "make visible" prompt (Host, which also turns Bluetooth on).
 * [problem] explains why an action couldn't start; it clears on the next attempt.
 */
class PairingActions internal constructor(
    val host: () -> Unit,
    val join: (code: String) -> Unit,
    val problem: String?,
)

@Composable
fun rememberPairingActions(viewModel: PairingViewModel): PairingActions {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<String?>(null) }
    var afterPermissions by remember { mutableStateOf<(() -> Unit)?>(null) }
    var afterBluetoothOn by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val next = afterPermissions
        afterPermissions = null
        if (LinkPermissions.allRequiredGranted(context)) next?.invoke()
        else problem = "Allow Nearby devices / Location so the phones can find each other."
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val next = afterBluetoothOn
        afterBluetoothOn = null
        if (result.resultCode == Activity.RESULT_OK) next?.invoke()
        else problem = "Turn Bluetooth on to pair."
    }
    val visibleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // RESULT_CANCELED means "deny"; allowing returns the visible duration in seconds.
        if (result.resultCode == Activity.RESULT_CANCELED) {
            problem = "Allow \"visible\" so your teammate's phone can find this one."
        } else {
            viewModel.host()
        }
    }

    fun withPermissions(action: () -> Unit) {
        problem = null
        if (LinkPermissions.allRequiredGranted(context)) {
            action()
        } else {
            afterPermissions = action
            permissionLauncher.launch(LinkPermissions.required + LinkPermissions.optional)
        }
    }

    fun withBluetoothOn(action: () -> Unit) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        when {
            adapter == null -> problem = "This phone has no Bluetooth."
            adapter.isEnabled -> action()
            else -> {
                afterBluetoothOn = action
                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }

    return PairingActions(
        host = { withPermissions { visibleLauncher.launch(RfcommPairing.discoverableIntent()) } },
        join = { code -> withPermissions { withBluetoothOn { viewModel.join(code) } } },
        problem = problem,
    )
}

/** One line describing the link, for headers and status cards. */
fun linkStatusLabel(pairing: PairingState, running: Boolean, status: LinkStatus): String = when (pairing) {
    PairingState.Unpaired -> "Not paired"
    is PairingState.Hosting -> "Waiting for teammate"
    is PairingState.Joining -> "Looking for teammate"
    is PairingState.Paired -> when {
        status is LinkStatus.Connected && status.kind == LinkKind.WIFI_DIRECT -> "Connected · Wi-Fi Direct"
        status is LinkStatus.Connected -> "Connected · Bluetooth"
        running -> "Paired · connecting…"
        else -> "Paired · link off"
    }
}
