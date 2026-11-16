package com.echo.hearing.volume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.echo.hearing.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Records the phone's media volume in the background. Uses no microphone and plays no audio:
 * it samples the volume setting, whether media is playing and the output route, on every
 * change and every [TICK_MS], and writes the result to [VolumeLog].
 */
class VolumeTrackerService : Service() {

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var recorder: VolumeRecorder
    private lateinit var watcher: PhoneVolumeWatcher
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var lastNotificationText: String? = null
    private val highVolume = HighVolumeMonitor()
    private val background = Executors.newSingleThreadExecutor()

    private val tick = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = sample()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = sample()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = sample()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Volume tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while ECHO records your phone volume in the background"
                setShowBadge(false)
            }
        )
        recorder = VolumeRecorder(VolumeLog.sink(this))
        watcher = PhoneVolumeWatcher(this) { sample() }
        VolumeLog.recoverCurrent(this)
        VolumeLog.prune(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VolumeLog.setTrackingEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)

        if (!started) {
            started = true
            watcher.start()
            audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
            audioManager.registerAudioDeviceCallback(deviceCallback, handler)
            handler.post(tick)
        }
        _running.value = true
        // Restart automatically if the system kills the process.
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (started) {
            watcher.stop()
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            recorder.close(System.currentTimeMillis())
        }
        background.shutdown()
        _running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sample() {
        val volume = PhoneAudio.volume(audioManager)
        val playing = PhoneAudio.isPlaying(audioManager)
        val output = PhoneAudio.output(audioManager)
        val now = System.currentTimeMillis()
        recorder.onSample(now, volume, playing, output)
        highVolume.onSample(now, volume.percent, playing)?.let { minutes ->
            VolumeAlertNotifier.notifyHighVolume(this, minutes, volume.percent)
        }
        maybeCheckDrift(now)
        val text = notificationText(volume, playing, output)
        if (text != lastNotificationText) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    /** Every few hours, compare recent listening volume with the user's baseline, off the main thread. */
    private fun maybeCheckDrift(now: Long) {
        val prefs = getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(K_DRIFT_CHECKED, 0L) < DRIFT_CHECK_INTERVAL_MS) return
        prefs.edit().putLong(K_DRIFT_CHECKED, now).apply()
        val app = applicationContext
        background.execute {
            val status = VolumeDrift.evaluate(VolumeLog.readAll(app), now)
            if (status is DriftStatus.Rising && now - prefs.getLong(K_DRIFT_ALERTED, 0L) >= DRIFT_ALERT_COOLDOWN_MS) {
                prefs.edit().putLong(K_DRIFT_ALERTED, now).apply()
                VolumeAlertNotifier.notifyDrift(app, status)
            }
        }
    }

    private fun notificationText(volume: PhoneVolume, playing: Boolean, output: AudioOutput): String {
        val high = if (volume.percent >= VolumeAnalytics.HIGH_PERCENT) " (high)" else ""
        val state = if (playing) "playing on ${output.label}" else "nothing playing"
        return "Volume ${volume.percent}%$high · $state"
    }

    private fun buildNotification(
        text: String = notificationText(
            PhoneAudio.volume(audioManager), PhoneAudio.isPlaying(audioManager), PhoneAudio.output(audioManager)
        )
    ): Notification {
        lastNotificationText = text
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VolumeTrackerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Tracking phone volume")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop tracking", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        /** How often the tracker checks the volume between change events. */
        const val TICK_MS = 15_000L

        private const val CHANNEL_ID = "volume_tracking"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.echo.hearing.volume.STOP"
        private const val ALERT_PREFS = "volume_alerts"
        private const val K_DRIFT_CHECKED = "drift_checked_at"
        private const val K_DRIFT_ALERTED = "drift_alerted_at"
        private const val DRIFT_CHECK_INTERVAL_MS = 6 * 60 * 60_000L
        /** Do not repeat the hearing-test suggestion more than once every two weeks. */
        private const val DRIFT_ALERT_COOLDOWN_MS = 14 * 24 * 60 * 60_000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            VolumeLog.setTrackingEnabled(context, true)
            ContextCompat.startForegroundService(context, Intent(context, VolumeTrackerService::class.java))
        }

        fun stop(context: Context) {
            VolumeLog.setTrackingEnabled(context, false)
            context.stopService(Intent(context, VolumeTrackerService::class.java))
        }
    }
}

/** Resumes tracking after a reboot or an app update, if the user left it on. */
class VolumeTrackerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (VolumeLog.isTrackingEnabled(context)) {
                    runCatching {
                        ContextCompat.startForegroundService(context, Intent(context, VolumeTrackerService::class.java))
                    }
                }
            }
        }
    }
}
