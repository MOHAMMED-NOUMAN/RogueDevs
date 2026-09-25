package com.itantra.app.core.transport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.itantra.app.R

/**
 * Keeps the offline link alive while the screen is off. It doesn't own the [Transport]; it
 * only keeps the process in the foreground (so Android doesn't kill it) and holds a partial
 * wake lock (so the CPU stays awake for the 2 s heartbeat). Start it when the link starts and
 * stop it when the link stops. Must be started while the app is on screen.
 *
 * The wake lock costs battery for as long as the link is up; that is the price of hearing an
 * SOS with the phone in a pocket.
 */
class TransportService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iTantra:transport")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        // If Android kills the process there is no Transport left to keep alive.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun notification(): android.app.Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Offline link", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("iTantra link active")
            .setContentText("Keeping the offline link to your teammate open")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "transport"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TransportService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TransportService::class.java))
        }
    }
}
