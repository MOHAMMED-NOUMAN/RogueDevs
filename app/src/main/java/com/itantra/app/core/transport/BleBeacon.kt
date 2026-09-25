package com.itantra.app.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

// Advertising needs BLUETOOTH_ADVERTISE and scanning BLUETOOTH_SCAN (Android 12+); on
// Android 8-11 both need BLUETOOTH_ADMIN, and scanning also needs location permission.

/** Size of the SOS payload a beacon carries. The crypto layer owns what's inside. */
const val SOS_BEACON_PAYLOAD_SIZE = 21

/**
 * Manufacturer ID 0xFFFF is the Bluetooth SIG's "reserved for testing" value: fine for the
 * demo, but a released app needs its own assigned company ID.
 */
private const val BEACON_COMPANY_ID = 0xFFFF

/** Marker in front of the payload so scanners can ignore other devices using 0xFFFF. */
private val BEACON_MARKER = byteArrayOf(0x69, 0x54) // "iT"

/** An SOS beacon heard nearby. */
class SosBeacon(val payload: ByteArray, val rssi: Int)

sealed interface BeaconStart {
    data object Started : BeaconStart

    /**
     * This phone can't advertise over BLE (common on low-end chips). Send the SOS over the
     * paired link instead: `transport.send(packet, Priority.SOS)`.
     */
    data object Unsupported : BeaconStart

    data class Failed(val reason: String) : BeaconStart
}

/**
 * The connection-less SOS beacon: any phone running iTantra nearby hears it, paired or not.
 * The beacon is a single BLE advertisement carrying a [SOS_BEACON_PAYLOAD_SIZE]-byte payload.
 */
@SuppressLint("MissingPermission")
class BleBeacon(context: Context) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private var advertising: AdvertiseCallback? = null

    val canAdvertise: Boolean
        get() = adapter?.isEnabled == true && adapter.isMultipleAdvertisementSupported &&
            adapter.bluetoothLeAdvertiser != null

    /** Starts advertising [payload] until [stopAdvertising]. Replaces any beacon already running. */
    suspend fun startAdvertising(payload: ByteArray): BeaconStart {
        require(payload.size == SOS_BEACON_PAYLOAD_SIZE) {
            "SOS payload must be $SOS_BEACON_PAYLOAD_SIZE bytes, was ${payload.size}"
        }
        if (!canAdvertise) return BeaconStart.Unsupported
        val advertiser = adapter!!.bluetoothLeAdvertiser ?: return BeaconStart.Unsupported
        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // reach matters more than battery for SOS
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BEACON_COMPANY_ID, BEACON_MARKER + payload)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    continuation.resume(BeaconStart.Started)
                }

                override fun onStartFailure(errorCode: Int) {
                    advertising = null
                    continuation.resume(
                        if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) BeaconStart.Unsupported
                        else BeaconStart.Failed(advertiseFailureName(errorCode))
                    )
                }
            }
            advertising = callback
            advertiser.startAdvertising(settings, data, callback)
            continuation.invokeOnCancellation { stopAdvertising() }
        }
    }

    fun stopAdvertising() {
        val callback = advertising ?: return
        advertising = null
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback) }
    }

    /**
     * SOS beacons heard nearby, until the collector stops. The same beacon is reported every
     * time it's heard; the caller decides how to de-duplicate. Fails if Bluetooth is off.
     * Uses a hardware filter, so scanning keeps working with the screen off.
     */
    fun scan(): Flow<SosBeacon> = callbackFlow {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw IOException("Bluetooth is off or not available")
        val filter = ScanFilter.Builder()
            .setManufacturerData(BEACON_COMPANY_ID, BEACON_MARKER, byteArrayOf(-1, -1))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val data = result.scanRecord?.getManufacturerSpecificData(BEACON_COMPANY_ID) ?: return
                if (data.size != BEACON_MARKER.size + SOS_BEACON_PAYLOAD_SIZE) return
                if (data[0] != BEACON_MARKER[0] || data[1] != BEACON_MARKER[1]) return
                trySend(SosBeacon(data.copyOfRange(BEACON_MARKER.size, data.size), result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IOException("BLE scan failed (code $errorCode)"))
            }
        }
        scanner.startScan(listOf(filter), settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}

/**
 * PLACEHOLDER until the crypto team defines the real SOS payload: version 1, a sender ID and
 * a sequence number, with the remaining 14 bytes left as zero.
 */
fun placeholderSosPayload(senderId: Int, sequence: Int): ByteArray =
    ByteBuffer.allocate(SOS_BEACON_PAYLOAD_SIZE)
        .put(1)
        .putInt(senderId)
        .putShort(sequence.toShort())
        .array()

private fun advertiseFailureName(code: Int) = when (code) {
    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
    else -> "code $code"
}
