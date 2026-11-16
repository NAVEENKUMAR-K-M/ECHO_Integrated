package com.echo.hearing

import com.echo.hearing.volume.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class VolumeAlertsTest {

    private val min = 60_000L
    private val day = 24 * 60 * min
    private val utc = TimeZone.getTimeZone("UTC")

    /** Samples every 15 s from [from] to [to] (inclusive) and returns the alert results that fired. */
    private fun HighVolumeMonitor.run(from: Long, to: Long, percent: Int, playing: Boolean = true): List<Long> {
        val fired = mutableListOf<Long>()
        var t = from
        while (t <= to) {
            onSample(t, percent, playing)?.let { fired += it }
            t += 15_000
        }
        return fired
    }

    // ---- high volume ----

    @Test
    fun `alerts after 10 minutes at high volume`() {
        val m = HighVolumeMonitor()
        assertTrue(m.run(0, 9 * min + 45_000, 85).isEmpty())
        assertEquals(listOf(10L), m.run(10 * min, 10 * min, 85))
    }

    @Test
    fun `does not alert below the high threshold or when nothing plays`() {
        assertTrue(HighVolumeMonitor().run(0, 60 * min, 79).isEmpty())
        assertTrue(HighVolumeMonitor().run(0, 60 * min, 100, playing = false).isEmpty())
    }

    @Test
    fun `a short dip does not restart the count but a long break does`() {
        val m = HighVolumeMonitor()
        m.run(0, 6 * min, 90)
        m.run(6 * min + 15_000, 7 * min, 50) // 1 minute lower: same stretch
        assertEquals(listOf(10L), m.run(7 * min + 15_000, 10 * min, 90))

        val n = HighVolumeMonitor()
        n.run(0, 8 * min, 90)
        n.run(8 * min + 15_000, 12 * min, 50) // 4 minute break: count restarts
        assertTrue(n.run(12 * min + 15_000, 21 * min, 90).isEmpty())
    }

    @Test
    fun `reminds every 30 minutes while it continues`() {
        val fired = HighVolumeMonitor().run(0, 75 * min, 90)
        assertEquals(listOf(10L, 40L, 70L), fired)
    }

    // ---- drift ----

    /** One hour of headphone listening per day at the given volume, for days [fromDay] until [toDay]. */
    private fun daily(fromDay: Int, toDay: Int, percent: (Int) -> Int, output: AudioOutput = AudioOutput.BLUETOOTH) =
        (fromDay until toDay).map { d ->
            VolumeSegment(d * day + 18 * 60 * min, d * day + 19 * 60 * min, percent(d), 100, true, output)
        }

    private val now = 40 * day + 20 * 60 * min // day 40, 20:00

    @Test
    fun `not enough history`() {
        val status = VolumeDrift.evaluate(daily(35, 41, { 50 }), now, utc)
        assertTrue(status is DriftStatus.NotEnoughData)
        assertEquals(5, (status as DriftStatus.NotEnoughData).daysTracked)
    }

    @Test
    fun `steady listening is stable`() {
        val status = VolumeDrift.evaluate(daily(5, 41, { 50 }), now, utc)
        assertTrue(status is DriftStatus.Stable)
    }

    @Test
    fun `a sustained rise suggests a hearing test`() {
        val status = VolumeDrift.evaluate(daily(5, 41, { d -> if (d >= 34) 70 else 50 }), now, utc)
        assertTrue(status is DriftStatus.Rising)
        status as DriftStatus.Rising
        assertEquals(50, status.baselineAvg)
        assertEquals(70, status.recentAvg)
        assertEquals(7, status.daysAbove)
    }

    @Test
    fun `one loud day is not a systematic rise`() {
        // Average still rises by more than 15 points, but only on one day.
        val status = VolumeDrift.evaluate(daily(5, 41, { d -> if (d == 40) 100 else 50 }), now, utc)
        assertFalse(status is DriftStatus.Rising)
    }

    @Test
    fun `switching from speaker to headphones is not a rise`() {
        val segments = daily(5, 34, { 30 }, AudioOutput.SPEAKER) + daily(34, 41, { 70 }, AudioOutput.WIRED)
        assertFalse(VolumeDrift.evaluate(segments, now, utc) is DriftStatus.Rising)
    }
}
