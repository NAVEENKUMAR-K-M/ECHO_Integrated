package com.echo.hearing.volume

import java.util.Calendar
import java.util.TimeZone

/**
 * Detects continuous listening at high volume. Feed it every sample; it returns the number of
 * minutes of high-volume listening when an alert is due, otherwise null.
 *
 * High listening continues across short dips or pauses (under [resetGapMs]). The first alert
 * fires after [thresholdMs]; while it continues, a reminder fires every [repeatMs].
 */
class HighVolumeMonitor(
    private val highPercent: Int = VolumeAnalytics.HIGH_PERCENT,
    private val thresholdMs: Long = 10 * 60_000L,
    private val resetGapMs: Long = 2 * 60_000L,
    private val repeatMs: Long = 30 * 60_000L
) {
    private var highSince: Long? = null
    private var lastHighAt = 0L
    private var lastAlertAt: Long? = null

    fun onSample(now: Long, percent: Int, playing: Boolean): Long? {
        val high = playing && percent >= highPercent
        if (!high) {
            if (now - lastHighAt > resetGapMs) reset()
            return null
        }
        if (highSince == null || now - lastHighAt > resetGapMs) {
            highSince = now
            lastAlertAt = null
        }
        lastHighAt = now
        val since = highSince ?: return null
        val elapsed = now - since
        if (elapsed < thresholdMs) return null
        val last = lastAlertAt
        if (last != null && now - last < repeatMs) return null
        lastAlertAt = now
        return elapsed / 60_000L
    }

    private fun reset() {
        highSince = null
        lastAlertAt = null
    }
}

/** Result of comparing recent listening volume with the user's own earlier baseline. */
sealed interface DriftStatus {
    /** Not enough history yet to compare. [daysTracked] of roughly [VolumeDrift.DAYS_NEEDED]. */
    data class NotEnoughData(val daysTracked: Int) : DriftStatus

    /** Compared, no steady rise. */
    data class Stable(val output: AudioOutput, val baselineAvg: Int, val recentAvg: Int) : DriftStatus

    /** Listening volume has risen steadily on [output]. */
    data class Rising(
        val output: AudioOutput,
        val baselineAvg: Int,
        val recentAvg: Int,
        val daysAbove: Int
    ) : DriftStatus {
        val increase: Int get() = recentAvg - baselineAvg
    }
}

/**
 * Detects a steady rise in listening volume, which can be an early sign of hearing change.
 *
 * Compares the last [RECENT_DAYS] days with the [BASELINE_DAYS] days before them, per output
 * (headphones and the speaker naturally use different volumes). It reports [DriftStatus.Rising]
 * only when the average rose by at least [RISE_POINTS] percentage points and the rise shows on
 * at least [MIN_DAYS_ABOVE] separate recent days, so one loud evening does not trigger it.
 */
object VolumeDrift {
    const val RECENT_DAYS = 7
    const val BASELINE_DAYS = 28
    const val DAYS_NEEDED = RECENT_DAYS + BASELINE_DAYS
    const val RISE_POINTS = 15
    const val DAILY_MARGIN = 10
    const val MIN_DAYS_ABOVE = 4
    private const val MIN_DAY_LISTENING_MS = 5 * 60_000L
    private const val MIN_BASELINE_MS = 2 * 60 * 60_000L
    private const val MIN_BASELINE_DAYS = 5
    private const val MIN_RECENT_MS = 60 * 60_000L

    fun evaluate(segments: List<VolumeSegment>, now: Long, zone: TimeZone = TimeZone.getDefault()): DriftStatus {
        val first = segments.minOfOrNull { it.start }
        val daysTracked = if (first == null) 0 else ((now - first) / (24 * 60 * 60_000L)).toInt()
        val today = VolumeAnalytics.dayStart(now, zone)
        val recentFrom = addDays(today, -(RECENT_DAYS - 1), zone)
        val baseFrom = addDays(recentFrom, -BASELINE_DAYS, zone)

        val compared = mutableListOf<DriftStatus>()
        for (output in AudioOutput.entries) {
            val mine = segments.filter { it.playing && it.output == output }
            if (mine.isEmpty()) continue
            val base = VolumeAnalytics.summarize(mine, baseFrom, recentFrom)
            val baseDays = VolumeAnalytics.days(mine, recentFrom - 1, BASELINE_DAYS, zone)
                .count { it.listeningMs >= MIN_DAY_LISTENING_MS }
            val recent = VolumeAnalytics.summarize(mine, recentFrom, now)
            if (base.listeningMs < MIN_BASELINE_MS || baseDays < MIN_BASELINE_DAYS || recent.listeningMs < MIN_RECENT_MS) continue

            val daysAbove = VolumeAnalytics.days(mine, now, RECENT_DAYS, zone).count {
                it.listeningMs >= MIN_DAY_LISTENING_MS && it.avgPercent >= base.avgPercent + DAILY_MARGIN
            }
            compared += if (recent.avgPercent - base.avgPercent >= RISE_POINTS && daysAbove >= MIN_DAYS_ABOVE) {
                DriftStatus.Rising(output, base.avgPercent, recent.avgPercent, daysAbove)
            } else {
                DriftStatus.Stable(output, base.avgPercent, recent.avgPercent)
            }
        }
        return compared.filterIsInstance<DriftStatus.Rising>().maxByOrNull { it.increase }
            ?: compared.firstOrNull()
            ?: DriftStatus.NotEnoughData(daysTracked)
    }

    private fun addDays(dayStart: Long, days: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
}
