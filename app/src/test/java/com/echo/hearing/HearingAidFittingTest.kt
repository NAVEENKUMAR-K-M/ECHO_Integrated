package com.echo.hearing

import com.echo.hearing.domain.model.Ear
import com.echo.hearing.domain.model.ThresholdResult
import com.echo.hearing.monitor.HearingAidFitting
import org.junit.Assert.*
import org.junit.Test

class HearingAidFittingTest {

    private fun t(ear: Ear, hz: Int, db: Int) = ThresholdResult(ear, hz, db, 2, 4)

    // EQ bands: 250, 500, 1000, 2000, 4000, 6000 Hz

    @Test
    fun `no thresholds gives no fitting`() {
        assertNull(HearingAidFitting.fromThresholds(emptyList()))
    }

    @Test
    fun `equal thresholds give a flat fitting`() {
        val results = listOf(500, 1000, 2000, 4000).flatMap { listOf(t(Ear.LEFT, it, 20), t(Ear.RIGHT, it, 20)) }
        val g = HearingAidFitting.fromThresholds(results)!!
        assertTrue(g.flatten().all { it == 0f })
    }

    @Test
    fun `high-frequency loss gets half gain relative to the best threshold`() {
        val results = listOf(
            t(Ear.LEFT, 500, 20), t(Ear.LEFT, 1000, 20), t(Ear.LEFT, 2000, 26), t(Ear.LEFT, 4000, 30),
            t(Ear.RIGHT, 500, 20), t(Ear.RIGHT, 1000, 20), t(Ear.RIGHT, 2000, 20), t(Ear.RIGHT, 4000, 20)
        )
        val g = HearingAidFitting.fromThresholds(results)!!
        // 250 uses nearest (500); 6000 uses nearest (4000)
        assertEquals(listOf(0f, 0f, 0f, 3f, 5f, 5f), g[0])
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f, 0f), g[1])
    }

    @Test
    fun `gain is capped at the safety limit`() {
        val results = listOf(t(Ear.LEFT, 1000, 10), t(Ear.LEFT, 4000, 60))
        val g = HearingAidFitting.fromThresholds(results)!!
        assertEquals(6f, g[0][4])
        assertTrue(g.flatten().all { it in 0f..6f })
    }

    @Test
    fun `untested bands are interpolated on a log scale`() {
        val results = listOf(t(Ear.RIGHT, 1000, 10), t(Ear.RIGHT, 4000, 18))
        val g = HearingAidFitting.fromThresholds(results)!!
        // 2000 Hz is halfway between 1 and 4 kHz in log terms: threshold 14, gain (14-10)/2 = 2
        assertEquals(2f, g[1][3])
        // The left ear was not tested: no gain
        assertTrue(g[0].all { it == 0f })
    }
}
