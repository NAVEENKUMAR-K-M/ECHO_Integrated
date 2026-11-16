package com.echo.hearing.volume

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Where media was playing. Order is priority when several outputs are connected. */
enum class AudioOutput(val label: String) {
    BLUETOOTH("Bluetooth"),
    WIRED("Wired headphones"),
    USB("USB audio"),
    SPEAKER("Phone speaker"),
    OTHER("Other")
}

/**
 * A stretch of time where the phone's volume, playback state and output did not change.
 * [end] is the last moment the tracker confirmed this state.
 */
data class VolumeSegment(
    val start: Long,
    val end: Long,
    val level: Int,
    val max: Int,
    val playing: Boolean,
    val output: AudioOutput
) {
    val durationMs: Long get() = (end - start).coerceAtLeast(0)
    val percent: Int get() = if (max > 0) level * 100 / max else 0

    fun sameState(other: VolumeSegment) =
        level == other.level && max == other.max && playing == other.playing && output == other.output

    fun toLine() = "$start,$end,$level,$max,${if (playing) 1 else 0},${output.name}"

    companion object {
        fun parse(line: String): VolumeSegment? {
            val f = line.trim().split(',')
            if (f.size != 6) return null
            return runCatching {
                VolumeSegment(
                    start = f[0].toLong(),
                    end = f[1].toLong(),
                    level = f[2].toInt(),
                    max = f[3].toInt(),
                    playing = f[4] == "1",
                    output = AudioOutput.valueOf(f[5])
                )
            }.getOrNull()?.takeIf { it.end >= it.start && it.max >= 0 }
        }
    }
}

/** What the recorder writes to. Implemented by [VolumeLog] on the phone and by fakes in tests. */
interface VolumeSink {
    fun append(segment: VolumeSegment)
    fun saveCurrent(segment: VolumeSegment?)
    /** Segments starting before this time were cleared by the user and must not be written. */
    fun clearedAt(): Long
}

/**
 * Turns volume samples into segments. Consecutive samples with the same state extend one
 * segment; any change closes it and starts a new one. If samples stop arriving for longer
 * than [maxGapMs] (the phone slept), the segment is closed at the last sample so the gap
 * is not counted.
 */
class VolumeRecorder(private val sink: VolumeSink, private val maxGapMs: Long = DEFAULT_MAX_GAP_MS) {
    private var current: VolumeSegment? = null
    private var lastSampleAt = 0L

    fun onSample(now: Long, volume: PhoneVolume, playing: Boolean, output: AudioOutput) {
        var cur = current
        if (cur != null && cur.start < sink.clearedAt()) cur = null
        if (cur != null && now - lastSampleAt > maxGapMs) {
            sink.append(cur.copy(end = lastSampleAt))
            cur = null
        }
        val sample = VolumeSegment(now, now, volume.level, volume.max, playing, output)
        current = when {
            cur == null -> sample
            cur.sameState(sample) -> cur.copy(end = now)
            else -> {
                sink.append(cur.copy(end = now))
                sample
            }
        }
        lastSampleAt = now
        sink.saveCurrent(current)
    }

    fun close(now: Long) {
        val cur = current ?: return
        if (cur.start >= sink.clearedAt()) {
            val end = if (now - lastSampleAt > maxGapMs) lastSampleAt else now
            sink.append(cur.copy(end = end))
        }
        current = null
        sink.saveCurrent(null)
    }

    companion object {
        const val DEFAULT_MAX_GAP_MS = 60_000L
    }
}

/**
 * On-device store: closed segments are appended to `volume_log.csv`; the open segment is
 * rewritten to `volume_current.csv` on every sample so a killed process loses almost nothing.
 */
object VolumeLog {
    const val RETENTION_DAYS = 90
    private const val LOG_FILE = "volume_log.csv"
    private const val CURRENT_FILE = "volume_current.csv"
    private const val PREFS = "volume_tracking"
    private const val K_CLEARED_AT = "cleared_at"

    private val lock = Any()
    private val _revision = MutableStateFlow(0L)
    /** Bumped on every write, so screens in the same process can refresh. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Volatile
    private var clearedAtCache: Long? = null

    private fun log(c: Context) = File(c.filesDir, LOG_FILE)
    private fun current(c: Context) = File(c.filesDir, CURRENT_FILE)

    fun sink(context: Context): VolumeSink {
        val app = context.applicationContext
        return object : VolumeSink {
            override fun append(segment: VolumeSegment) = this@VolumeLog.append(app, segment)
            override fun saveCurrent(segment: VolumeSegment?) = this@VolumeLog.saveCurrent(app, segment)
            override fun clearedAt(): Long = this@VolumeLog.clearedAt(app)
        }
    }

    fun append(context: Context, segment: VolumeSegment) {
        if (segment.durationMs <= 0L) return
        synchronized(lock) { log(context).appendText(segment.toLine() + "\n") }
        _revision.value++
    }

    fun saveCurrent(context: Context, segment: VolumeSegment?) {
        synchronized(lock) {
            if (segment == null) current(context).delete() else current(context).writeText(segment.toLine())
        }
        _revision.value++
    }

    /** Moves an open segment left behind by a killed process into the log. */
    fun recoverCurrent(context: Context) {
        val leftover = synchronized(lock) {
            val f = current(context)
            val seg = if (f.exists()) VolumeSegment.parse(f.readText()) else null
            f.delete()
            seg
        }
        if (leftover != null && leftover.start >= clearedAt(context)) append(context, leftover)
    }

    /** Every stored segment, including the open one, oldest first. */
    fun readAll(context: Context): List<VolumeSegment> = synchronized(lock) {
        val closed = log(context).takeIf { it.exists() }?.useLines { lines ->
            lines.mapNotNull(VolumeSegment::parse).toList()
        }.orEmpty()
        val open = current(context).takeIf { it.exists() }?.readText()?.let(VolumeSegment::parse)
        (closed + listOfNotNull(open)).sortedBy { it.start }
    }

    fun prune(context: Context, now: Long = System.currentTimeMillis()) {
        val cutoff = now - RETENTION_DAYS * 24L * 60 * 60 * 1000
        synchronized(lock) {
            val f = log(context)
            if (!f.exists()) return
            val all = f.useLines { it.mapNotNull(VolumeSegment::parse).toList() }
            val kept = all.filter { it.end >= cutoff }
            if (kept.size != all.size) f.writeText(kept.joinToString("") { it.toLine() + "\n" })
        }
    }

    fun clear(context: Context, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            log(context).delete()
            current(context).delete()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(K_CLEARED_AT, now).apply()
            clearedAtCache = now
        }
        _revision.value++
    }

    fun clearedAt(context: Context): Long = clearedAtCache
        ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_CLEARED_AT, 0L).also { clearedAtCache = it }

    fun isTrackingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ENABLED, false)

    fun setTrackingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    private const val K_ENABLED = "enabled"
}
