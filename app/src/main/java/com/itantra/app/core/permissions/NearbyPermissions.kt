package com.itantra.app.core.permissions

import android.Manifest
import android.os.Build

object NearbyPermissions {
    fun bluetooth(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            list += Manifest.permission.BLUETOOTH
            list += Manifest.permission.BLUETOOTH_ADMIN
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        // Classic discovery still needs location on many OEMs.
        list += Manifest.permission.ACCESS_FINE_LOCATION
        list += Manifest.permission.ACCESS_COARSE_LOCATION
        return list.toTypedArray()
    }

    fun wifi(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    fun allFor(transportIsBluetooth: Boolean): Array<String> =
        if (transportIsBluetooth) bluetooth() else wifi()
}
