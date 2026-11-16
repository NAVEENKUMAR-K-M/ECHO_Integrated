package com.echo.hearing.volume

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.echo.hearing.MainActivity

/** Hearing-safety notifications, on their own channel so they make a sound unlike the tracking notice. */
internal object VolumeAlertNotifier {
    private const val CHANNEL_ID = "hearing_alerts"
    private const val HIGH_VOLUME_ID = 43
    private const val DRIFT_ID = 44

    private fun manager(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hearing alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warnings about long listening at high volume and rising listening volume"
            }
        )
        return nm
    }

    /** Opens the app on [tab] (an [AppNavigation] tab name). */
    fun openAppIntent(context: Context, tab: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(AppNavigation.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun notifyHighVolume(context: Context, minutes: Long, percent: Int) {
        val text = "You have been listening at $percent% volume for $minutes minutes. " +
            "Listening this loud for long periods may affect your hearing. Consider turning it down or taking a break."
        manager(context).notify(
            HIGH_VOLUME_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("High volume for $minutes minutes")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openAppIntent(context, AppNavigation.TAB_MONITOR, 10))
                .setAutoCancel(true)
                .build()
        )
    }

    fun notifyDrift(context: Context, drift: DriftStatus.Rising) {
        val text = "Your listening volume on ${drift.output.label.lowercase()} has risen from about " +
            "${drift.baselineAvg}% to ${drift.recentAvg}% over the past week. A steady rise like this can be an " +
            "early sign of hearing change. Consider taking a hearing test."
        manager(context).notify(
            DRIFT_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Time for a hearing check")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(openAppIntent(context, AppNavigation.TAB_TEST, 11))
                .addAction(0, "Take hearing test", openAppIntent(context, AppNavigation.TAB_TEST, 12))
                .setAutoCancel(true)
                .build()
        )
    }
}
