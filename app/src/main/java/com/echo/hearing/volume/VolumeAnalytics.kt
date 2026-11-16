package com.echo.hearing.volume

import java.util.Calendar
import java.util.TimeZone

/** Listening statistics for one time window (usually one calendar day). */
data class VolumeSummary(
    val from: Long,
    val to: Long,
    /** Time media was playing. */
    val listeningMs: Long = 0,
    /** Time-weighted average volume while playing, 0–100. */
    val avgPercent: Int = 0,
    /** Highest volume while playing, 0–100. */
    val peakPercent: Int = 0,
    /** Time playing at or above [VolumeAnalytics.HIGH_PERCENT]. */
    val highMs: Long = 0,
    /** Listening time per output. */
    val byOutput: Map<AudioOutput, Long> = emptyMap()
)

/** Consecutive playing segments merged into one listening session. */
data class ListeningSession(
    val start: Long,
    val end: Long,
    val avgPercent: Int,
    val peakPercent: Int,
    val output: AudioOutput
) {
    val durationMs: Long get() = end - start
}

object VolumeAnalytics {
    /** Phone volume at or above this percentage is reported as high. */
    const val HIGH_PERCENT = 80

    /** Playing segments separated by less than this are one session (track changes, short pauses). */
    const val SESSION_MERGE_GAP_MS = 2 * 60_000L

    private fun clip(s: VolumeSegment, from: Long, to: Long): VolumeSegment? {
        val start = maxOf(s.start, from)
        val end = minOf(s.end, to)
        return if (end > start) s.copy(start = start, end = end) else null
    }

    fun summarize(segments: List<VolumeSegment>, from: Long, to: Long): VolumeSummary {
        val playing = segments.filter { it.playing }.mapNotNull { clip(it, from, to) }
        val listening = playing.sumOf { it.durationMs }
        if (listening == 0L) return VolumeSummary(from, to)
        val weighted = playing.sumOf { it.percent.toDouble() * it.durationMs }
        return VolumeSummary(
            from = from,
            to = to,
            listeningMs = listening,
            avgPercent = Math.round(weighted / listening).toInt(),
            peakPercent = playing.maxOf { it.percent },
            highMs = playing.filter { it.percent >= HIGH_PERCENT }.sumOf { it.durationMs },
            byOutput = playing.groupBy { it.output }.mapValues { (_, v) -> v.sumOf { it.durationMs } }
        )
    }

    /** Start of the calendar day containing [time]. */
    fun dayStart(time: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun addDays(dayStart: Long, days: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis

    /** One summary per calendar day for the last [count] days, oldest first, ending today. */
    fun days(
        segments: List<VolumeSegment>,
        now: Long,
        count: Int,
        zone: TimeZone = TimeZone.getDefault()
    ): List<VolumeSummary> {
        val today = dayStart(now, zone)
        return (count - 1 downTo 0).map { back ->
            val from = addDays(today, -back, zone)
            summarize(segments, from, addDays(from, 1, zone))
        }
    }

    /** Volume bands for "how loud do I listen" breakdowns. */
    enum class Band(val label: String) {
        LOW("Below 50%"),
        MODERATE("50–79%"),
        HIGH("$HIGH_PERCENT% or more")
    }

    fun bandOf(percent: Int): Band = when {
        percent >= HIGH_PERCENT -> Band.HIGH
        percent >= 50 -> Band.MODERATE
        else -> Band.LOW
    }

    /** Listening time in each volume band between [from] and [to]. Every band is present. */
    fun listeningByBand(segments: List<VolumeSegment>, from: Long, to: Long): Map<Band, Long> {
        val byBand = segments.filter { it.playing }.mapNotNull { clip(it, from, to) }
            .groupBy { bandOf(it.percent) }
            .mapValues { (_, v) -> v.sumOf { it.durationMs } }
        return Band.entries.associateWith { byBand[it] ?: 0L }
    }

    /** Listening sessions, newest first. */
    fun sessions(segments: List<VolumeSegment>, mergeGapMs: Long = SESSION_MERGE_GAP_MS): List<ListeningSession> {
        val groups = mutableListOf<MutableList<VolumeSegment>>()
        for (s in segments.filter { it.playing && it.durationMs > 0 }.sortedBy { it.start }) {
            val last = groups.lastOrNull()
            if (last != null && s.start - last.last().end <= mergeGapMs) last.add(s) else groups.add(mutableListOf(s))
        }
        return groups.map { g ->
            val total = g.sumOf { it.durationMs }.coerceAtLeast(1)
            ListeningSession(
                start = g.first().start,
                end = g.maxOf { it.end },
                avgPercent = Math.round(g.sumOf { it.percent.toDouble() * it.durationMs } / total).toInt(),
                peakPercent = g.maxOf { it.percent },
                output = g.groupBy { it.output }.maxBy { (_, v) -> v.sumOf { it.durationMs } }.key
            )
        }.reversed()
    }
}
