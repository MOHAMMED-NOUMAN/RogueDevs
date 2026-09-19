package com.itantra.app.core.pairing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.itantra.app.MainActivity
import com.itantra.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PairingService : LifecycleService() {

    @Inject
    lateinit var controller: PairingController

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground("iTantra session starting")
        lifecycleScope.launch {
            controller.state.collectLatest { state ->
                val text = when {
                    state.peers.isNotEmpty() ->
                        "Connected to ${state.peers.joinToString { it.name }}"
                    state.role == PairingRole.WAIT -> "Waiting for teammate"
                    state.role == PairingRole.FIND -> "Looking for teammates"
                    else -> "iTantra session active"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            controller.disconnectAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground("iTantra session active")
        return START_STICKY
    }

    private fun startInForeground(text: String) {
        val notification = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("iTantra session active")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Pairing session",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val ACTION_STOP = "com.itantra.app.pairing.STOP"
        private const val CHANNEL_ID = "itantra_pairing"
        private const val NOTIFICATION_ID = 17331
    }
}
