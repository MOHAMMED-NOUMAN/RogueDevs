package com.itantra.app.core.sos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.itantra.app.MainActivity
import com.itantra.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the teammate's phone does when an SOS arrives: the alarm sound, looped at full alarm
 * volume (the alarm stream, which Do Not Disturb normally lets through), a repeating vibration,
 * and a full-screen notification with an "I'm coming" action.
 */
@Singleton
class SosAlarm @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: MediaPlayer? = null
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SOS alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "An SOS from your paired teammate"
                // The alarm plays its own sound and vibration, on the alarm stream.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    /** Starts the sound and vibration; does nothing if already ringing. */
    @Synchronized
    fun ring() {
        if (player != null) return
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } // may be refused under Do Not Disturb; the alarm still plays at its current volume
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator?.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    @Synchronized
    fun silence() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
    }

    /** Shows (or updates) the SOS notification. [ringing]: full-screen and high priority. */
    fun showNotification(sender: String, detail: String, ringing: Boolean) {
        val open = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SOS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val coming = PendingIntent.getBroadcast(
            context, 11,
            Intent(context, SosActionReceiver::class.java).setAction(SosActionReceiver.ACTION_ACKNOWLEDGE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_itantra)
            .setColor(SOS_RED)
            .setContentTitle("SOS from ${sender.ifBlank { "your teammate" }}")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(ringing)
            .setAutoCancel(!ringing)
            .setOnlyAlertOnce(true)
        if (ringing) {
            builder.setFullScreenIntent(open, true)
                .addAction(R.drawable.ic_stat_itantra, "I'm coming", coming)
        }
        runCatching { notifications.notify(NOTIFICATION_ID, builder.build()) } // no-op without POST_NOTIFICATIONS
    }

    fun clearNotification() = notifications.cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "sos"
        const val NOTIFICATION_ID = 2001
        const val SOS_RED = 0xFFE53945.toInt()
    }
}
