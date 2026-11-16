package com.echo.hearing.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.echo.hearing.MainActivity
import com.echo.hearing.volume.AppNavigation

/**
 * Foreground service that keeps the hearing aid (mic → DSP → earbuds) running while the app is
 * in the background. It mixes with other media (no audio focus), stops for calls, shows an
 * ongoing notification with a Stop action,
 * and stops itself when the native audio engine stops.
 */
class MonitorService : Service() {

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var watchingCalls = false

    private fun inCall(): Boolean = audioManager.mode == AudioManager.MODE_IN_CALL ||
        audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

    // Android 12+: stop when a phone or video call starts. (Older versions: the call takes the
    // microphone and the native engine reports the stream error, which also stops the aid.)
    private val modeListener = AudioManager.OnModeChangedListener {
        if (inCall()) {
            stopReason = "Stopped because a call started"
            NativeAudioEngine.nativeStop()
            stopSelf()
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!NativeAudioEngine.nativeIsRunning()) {
                stopSelf()
                return
            }
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hearing aid", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the ECHO hearing aid is running"
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopReason = "Stopped from the notification"
            NativeAudioEngine.nativeStop()
            stopSelf()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)

        if (!NativeAudioEngine.nativeIsRunning()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // No audio focus on purpose: the hearing aid mixes with music and video instead of pausing
        // them. It only stops for phone and video calls, which need the microphone.
        if (!watchingCalls && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(this), modeListener)
            watchingCalls = true
        }
        if (inCall()) {
            stopReason = "Stopped because a call is active"
            NativeAudioEngine.nativeStop()
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_MS)
        // Not sticky: a restarted process has no audio engine running, so there is nothing to resume.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        if (watchingCalls && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.removeOnModeChangedListener(modeListener)
            watchingCalls = false
        }
        if (NativeAudioEngine.nativeIsRunning()) NativeAudioEngine.nativeStop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 20,
            Intent(this, MainActivity::class.java)
                .putExtra(AppNavigation.EXTRA_TAB, AppNavigation.TAB_AID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 21,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ECHO hearing aid is on")
            .setContentText("Microphone to earbuds with speech processing")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hearing_aid"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_STOP = "com.echo.hearing.monitor.STOP"
        private const val WATCHDOG_MS = 1000L

        /** Why the service stopped the engine, read once by the view model. */
        @Volatile
        private var stopReason: String? = null

        /** Devices the running engine uses, so a recreated screen shows the right ones. */
        @Volatile
        var activeInputId: Int? = null
            private set

        @Volatile
        var activeOutputId: Int? = null
            private set

        fun start(context: Context, inputId: Int, outputId: Int) {
            stopReason = null
            activeInputId = inputId
            activeOutputId = outputId
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            activeInputId = null
            activeOutputId = null
            context.stopService(Intent(context, MonitorService::class.java))
        }

        fun consumeStopReason(): String? = stopReason.also { stopReason = null }
    }
}
