package com.echo.hearing.monitor

import com.echo.hearing.domain.model.Ear
import com.echo.hearing.domain.model.ThresholdResult
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Turns hearing-screening thresholds into hearing-aid EQ gains.
 *
 * Phone screening levels are not calibrated to dB HL, so only the *differences* between
 * frequencies and ears are meaningful. The fitting therefore uses the half-gain rule relative
 * to the listener's own best threshold: each band gets half of how much worse it is than the
 * best-heard frequency, rounded to 1 dB and capped at [MonitorLimits.MAX_GAIN_DB]. Equal
 * thresholds give a flat (all 0 dB) fitting. Untested bands are interpolated on a
 * log-frequency scale, or take the nearest tested frequency beyond the tested range.
 */
object HearingAidFitting {
    const val GAIN_FRACTION = 0.5f

    /** Returns gains as [ear][band] (ear 0 = left, 1 = right), or null if there are no thresholds. */
    fun fromThresholds(
        results: List<ThresholdResult>,
        bandsHz: IntArray = MonitorLimits.EQ_CENTERS_HZ,
        maxGainDb: Float = MonitorLimits.MAX_GAIN_DB
    ): List<List<Float>>? {
        if (results.isEmpty()) return null
        val best = results.minOf { it.thresholdDb }
        return listOf(Ear.LEFT, Ear.RIGHT).map { ear ->
            val points = results.filter { it.ear == ear }
                .groupBy { it.frequencyHz }
                .map { (hz, rs) -> hz to rs.map { it.thresholdDb }.average().toFloat() }
                .sortedBy { it.first }
            bandsHz.map { hz ->
                if (points.isEmpty()) 0f
                else {
                    val loss = thresholdAt(points, hz) - best
                    (loss * GAIN_FRACTION).roundToInt().toFloat().coerceIn(0f, maxGainDb)
                }
            }
        }
    }

    private fun thresholdAt(points: List<Pair<Int, Float>>, hz: Int): Float {
        if (hz <= points.first().first) return points.first().second
        if (hz >= points.last().first) return points.last().second
        val upper = points.indexOfFirst { it.first >= hz }
        val (f1, t1) = points[upper - 1]
        val (f2, t2) = points[upper]
        if (f2 == hz) return t2
        val x = (ln(hz.toFloat()) - ln(f1.toFloat())) / (ln(f2.toFloat()) - ln(f1.toFloat()))
        return t1 + (t2 - t1) * x
    }
}
