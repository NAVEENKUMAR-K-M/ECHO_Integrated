package com.echo.hearing

import com.echo.hearing.volume.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class VolumeTrackingTest {

    private class FakeSink(var cleared: Long = 0L) : VolumeSink {
        val appended = mutableListOf<VolumeSegment>()
        var current: VolumeSegment? = null
        override fun append(segment: VolumeSegment) { appended += segment }
        override fun saveCurrent(segment: VolumeSegment?) { current = segment }
        override fun clearedAt(): Long = cleared
    }

    private val v7 = PhoneVolume(7, 15)
    private val v12 = PhoneVolume(12, 15)
    private val utc = TimeZone.getTimeZone("UTC")
    private val min = 60_000L

    private fun seg(start: Long, end: Long, level: Int, playing: Boolean = true, out: AudioOutput = AudioOutput.BLUETOOTH) =
        VolumeSegment(start, end, level, 100, playing, out)

    // ---- recorder ----

    @Test
    fun `unchanged samples extend one segment`() {
        val sink = FakeSink()
        val r = VolumeRecorder(sink)
        r.onSample(0, v7, true, AudioOutput.WIRED)
        r.onSample(15_000, v7, true, AudioOutput.WIRED)
        r.onSample(30_000, v7, true, AudioOutput.WIRED)
        assertTrue(sink.appended.isEmpty())
        assertEquals(0L, sink.current!!.start)
        assertEquals(30_000L, sink.current!!.end)
    }

    @Test
    fun `a volume change closes the segment at the change`() {
        val sink = FakeSink()
        val r = VolumeRecorder(sink)
        r.onSample(0, v7, true, AudioOutput.WIRED)
        r.onSample(20_000, v12, true, AudioOutput.WIRED)
        assertEquals(listOf(VolumeSegment(0, 20_000, 7, 15, true, AudioOutput.WIRED)), sink.appended)
        assertEquals(20_000L, sink.current!!.start)
        assertEquals(12, sink.current!!.level)
    }

    @Test
    fun `a long gap between samples is not counted`() {
        val sink = FakeSink()
        val r = VolumeRecorder(sink, maxGapMs = 60_000)
        r.onSample(0, v7, true, AudioOutput.SPEAKER)
        r.onSample(15_000, v7, true, AudioOutput.SPEAKER)
        r.onSample(3_600_000, v7, true, AudioOutput.SPEAKER) // phone slept for an hour
        assertEquals(15_000L, sink.appended.single().end)
        assertEquals(3_600_000L, sink.current!!.start)
    }

    @Test
    fun `segments from before a clear are dropped`() {
        val sink = FakeSink()
        val r = VolumeRecorder(sink)
        r.onSample(0, v7, true, AudioOutput.SPEAKER)
        sink.cleared = 10_000
        r.onSample(15_000, v7, true, AudioOutput.SPEAKER)
        assertTrue(sink.appended.isEmpty())
        assertEquals(15_000L, sink.current!!.start)
    }

    @Test
    fun `close writes the open segment`() {
        val sink = FakeSink()
        val r = VolumeRecorder(sink)
        r.onSample(0, v7, false, AudioOutput.SPEAKER)
        r.close(10_000)
        assertEquals(10_000L, sink.appended.single().end)
        assertNull(sink.current)
    }

    @Test
    fun `segment line round trips`() {
        val s = VolumeSegment(1, 2, 3, 15, true, AudioOutput.USB)
        assertEquals(s, VolumeSegment.parse(s.toLine()))
        assertNull(VolumeSegment.parse("garbage"))
        assertNull(VolumeSegment.parse("5,1,3,15,1,USB")) // end before start
    }

    // ---- analytics ----

    @Test
    fun `summary weights volume by time and ignores idle time`() {
        val segments = listOf(
            seg(0, 30 * min, 40),
            seg(30 * min, 40 * min, 90),
            seg(40 * min, 100 * min, 100, playing = false)
        )
        val s = VolumeAnalytics.summarize(segments, 0, 24 * 60 * min)
        assertEquals(40 * min, s.listeningMs)
        assertEquals(53, s.avgPercent) // (40*30 + 90*10) / 40 = 52.5
        assertEquals(90, s.peakPercent)
        assertEquals(10 * min, s.highMs)
        assertEquals(40 * min, s.byOutput[AudioOutput.BLUETOOTH])
    }

    @Test
    fun `summary clips segments to the window`() {
        val s = VolumeAnalytics.summarize(listOf(seg(0, 60 * min, 50)), 30 * min, 90 * min)
        assertEquals(30 * min, s.listeningMs)
    }

    @Test
    fun `days splits a segment across midnight`() {
        val day = 24 * 60 * min
        val segments = listOf(seg(day - 10 * min, day + 20 * min, 60))
        val days = VolumeAnalytics.days(segments, now = day + 60 * min, count = 2, zone = utc)
        assertEquals(2, days.size)
        assertEquals(10 * min, days[0].listeningMs)
        assertEquals(20 * min, days[1].listeningMs)
    }

    @Test
    fun `sessions merge short pauses and split long ones, newest first`() {
        val segments = listOf(
            seg(0, 10 * min, 50, out = AudioOutput.WIRED),
            seg(11 * min, 20 * min, 70, out = AudioOutput.WIRED), // 1 min pause: same session
            seg(60 * min, 65 * min, 30, out = AudioOutput.SPEAKER) // new session
        )
        val sessions = VolumeAnalytics.sessions(segments)
        assertEquals(2, sessions.size)
        assertEquals(60 * min, sessions[0].start)
        val first = sessions[1]
        assertEquals(0L, first.start)
        assertEquals(20 * min, first.end)
        assertEquals(70, first.peakPercent)
        assertEquals(AudioOutput.WIRED, first.output)
    }
}
