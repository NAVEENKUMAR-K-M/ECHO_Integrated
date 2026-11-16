package com.echo.hearing.volume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

/** The phone's media volume setting (the volume buttons change it). */
data class PhoneVolume(val level: Int = 0, val max: Int = 0) {
    val percent: Int get() = if (max > 0) level * 100 / max else 0
}

/** Reads the phone's media volume, whether audio is playing, and where it is playing. */
internal object PhoneAudio {

    fun volume(am: AudioManager) = PhoneVolume(
        level = am.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    )

    fun isPlaying(am: AudioManager): Boolean = am.isMusicActive

    /** The output media is routed to. Falls back to the most likely connected output on Android 12 and older. */
    fun output(am: AudioManager): AudioOutput {
        val routed: List<AudioDeviceInfo>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                am.getAudioDevicesForAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                )
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        } else null
        val devices = routed ?: am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        return devices.map { typeOf(it.type) }.minByOrNull { it.ordinal } ?: AudioOutput.OTHER
    }

    private fun typeOf(type: Int): AudioOutput = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutput.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioOutput.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> AudioOutput.USB
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutput.SPEAKER
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) AudioOutput.BLUETOOTH else AudioOutput.OTHER
    }
}

/**
 * Calls [onChange] whenever the media volume may have changed (volume buttons, system slider,
 * Bluetooth headset). Uses the settings observer and the volume-changed broadcast together,
 * because per-device Bluetooth volume does not always update the settings table.
 */
internal class PhoneVolumeWatcher(context: Context, private val onChange: () -> Unit) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = onChange()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onChange()
    }

    fun start() {
        if (registered) return
        registered = true
        appContext.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        ContextCompat.registerReceiver(
            appContext, receiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        if (!registered) return
        registered = false
        appContext.contentResolver.unregisterContentObserver(observer)
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
