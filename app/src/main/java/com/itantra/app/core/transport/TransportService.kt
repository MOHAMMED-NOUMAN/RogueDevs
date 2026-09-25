package com.itantra.app.core.transport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the offline link alive while the screen is off. It doesn't own the [Transport]; it
 * keeps the process in the foreground (so Android doesn't kill it) and holds a partial
 * wake lock (so the CPU stays awake for the 2 s heartbeat). Start it when the link starts and
 * stop it when the link stops. Must be started while the app is on screen.
 *
 * Its notification follows [LinkManager.linkStatus]: which radio is up and for how long, or
 * that it is reconnecting. Tapping it opens the app; Disconnect stops the link.
 *
 * The wake lock costs battery for as long as the link is up; that is the price of hearing an
 * SOS with the phone in a pocket.
 */
@AndroidEntryPoint
class TransportService : Service() {
    @Inject lateinit var linkManager: LinkManager

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectedSince = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            linkManager.stopLink() // also stops this service
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(linkManager.linkStatus.value),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iTantra:transport")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
            scope.launch {
                linkManager.linkStatus.collect { status ->
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification(status))
                }
            }
        }
        // If Android kills the process there is no Transport left to keep alive.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Offline link", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while iTantra keeps the offline link to your teammate open"
                setShowBadge(false)
            }
        )
    }

    private fun notification(status: LinkStatus): Notification {
        val connected = status is LinkStatus.Connected
        if (connected && connectedSince == 0L) connectedSince = System.currentTimeMillis()
        if (!connected) connectedSince = 0L

        val (title, text) = when {
            status is LinkStatus.Connected && status.kind == LinkKind.WIFI_DIRECT ->
                "Connected to your teammate" to "Wi-Fi Direct · works without internet"
            status is LinkStatus.Connected ->
                "Connected to your teammate" to "Bluetooth backup · works without internet"
            else ->
                "Reconnecting to your teammate" to "Trying Wi-Fi Direct, then Bluetooth"
        }

        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, TransportService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_itantra)
            .setColor(BRAND_GREEN)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Offline link")
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_stat_close, "Disconnect", disconnect)
            // While connected, the header counts up how long the link has been up.
            .setShowWhen(connected)
            .setUsesChronometer(connected)
            .setWhen(if (connected) connectedSince else System.currentTimeMillis())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "transport"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_DISCONNECT = "com.itantra.app.action.DISCONNECT"

        /** PrimaryGreen in ui/theme/Color.kt: tints the icon and app name in the shade. */
        private const val BRAND_GREEN = 0xFF19B878.toInt()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TransportService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TransportService::class.java))
        }
    }
}
