package com.echo.hearing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.hearing.data.UserProfile
import com.echo.hearing.domain.model.HearingSessionRecord
import com.echo.hearing.monitor.MonitorUiState
import com.echo.hearing.volume.*
import com.echo.hearing.ui.components.*
import com.echo.hearing.ui.theme.*
import com.google.gson.GsonBuilder
import java.text.DateFormat
import java.util.Date

// ---- HOME TAB ----

@Composable
fun HomeScreen(
    profile: UserProfile,
    screeningState: HearingScreeningUiState,
    monitorState: MonitorUiState,
    volumeTracking: Boolean = false,
    initialTestPending: Boolean = false,
    onStartInitialTest: () -> Unit = {},
    onNavigateToTest: () -> Unit,
    onNavigateToMonitor: () -> Unit,
    onNavigateToAid: () -> Unit = {},
    onNavigateToReports: () -> Unit,
    onOpenScreeningHistory: () -> Unit
) {
    val latest = screeningState.savedSessions.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoBg)
    ) {
        EchoTopBar(
            title = "ECHO",
            actions = {
                EchoStatusPill(
                    label = if (volumeTracking) "Tracking volume" else "Ready",
                    icon = if (volumeTracking) Icons.Filled.GraphicEq else Icons.Outlined.CheckCircle,
                    containerColor = if (volumeTracking) EmeraldSoft else EchoSurfaceAlt,
                    borderColor = if (volumeTracking) Emerald500 else EchoPillBorder,
                    contentColor = if (volumeTracking) Emerald500 else EchoPrimary
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (profile.preferredName.isBlank()) "Welcome back" else "Welcome back, ${profile.preferredName}",
                    color = EchoNavy,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Personalized hearing clarity and daily listening monitoring.",
                    color = EchoTextMuted,
                    fontSize = 14.sp
                )
            }

            if (initialTestPending) {
                EchoCard(borderColor = EchoPillBorder, containerColor = EchoSurfaceAlt) {
                    Text("Initial Hearing Input Pending", color = EchoPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Take your initial pure-tone screening to capture your baseline hearing input and calibrate ECHO.",
                        color = EchoNavy,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    EchoPrimaryButton(
                        text = "Start Initial Hearing Test",
                        icon = Icons.Outlined.Hearing,
                        onClick = onStartInitialTest
                    )
                }
            }

            // Status Overview Card
            EchoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("System Status", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    EchoStatusPill(
                        label = if (screeningState.isHeadphoneConnected) "Headphones Ready" else "Check Headphones",
                        icon = Icons.Outlined.Headphones
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (latest != null) {
                        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(latest.startedAt))
                        "Latest screening ($date): ${latest.screeningOutcome?.overallClassification?.displayName ?: "Saved"} (${latest.thresholdResults.size} thresholds)"
                    } else {
                        "No screening saved yet. Run a preliminary pure-tone check to establish your baseline."
                    },
                    color = EchoTextMuted,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Volume tracking: ${if (volumeTracking) "On, recording in the background" else "Off"}",
                    color = if (volumeTracking) Emerald500 else EchoTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Three core cards matching Welcome screen
            HomeActionCard(
                icon = Icons.Outlined.Hearing,
                title = "Preliminary Sound Screening",
                description = "Modified Hughson–Westlake or Binary Search screening with ambient noise gating and audiogram.",
                buttonText = "Open Screening",
                onClick = onNavigateToTest
            )

            HomeActionCard(
                icon = Icons.Outlined.GraphicEq,
                title = "Phone Volume Monitor",
                description = "Tracks your phone volume and listening time in the background, with daily and weekly analytics.",
                buttonText = "Open Volume Monitor",
                onClick = onNavigateToMonitor
            )

            HomeActionCard(
                icon = Icons.Outlined.Headphones,
                title = if (monitorState.running) "Hearing Aid · On" else "Hearing Aid",
                description = "Hear your surroundings through your earbuds with speech processing, noise reduction and per-ear EQ. Keeps running in the background.",
                buttonText = if (monitorState.running) "Open Hearing Aid" else "Start Hearing Aid",
                onClick = onNavigateToAid
            )

            HomeActionCard(
                icon = Icons.Outlined.Assessment,
                title = "Listening Reports & History",
                description = "Weekly exposure summaries, speech clarity A/B scores, and saved screening comparisons.",
                buttonText = "View Reports",
                onClick = onNavigateToReports
            )

            EchoOutlinedPillButton(
                text = "View Screening History (${screeningState.savedSessions.size})",
                icon = Icons.Outlined.History,
                onClick = onOpenScreeningHistory
            )
        }
    }
}

@Composable
private fun HomeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    EchoCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EchoIconBadge(icon = icon, sizeDp = 44, iconSizeDp = 22)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = EchoNavy, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = EchoTextMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        EchoPrimaryButton(text = buttonText, onClick = onClick)
    }
}

// ---- REPORTS TAB (Image 1) ----

private enum class ReportsSubScreen {
    MAIN,
    WEEKLY_REPORT,
    EXPOSURE_TRENDS,
    INSIGHTS_TIPS,
    HISTORICAL_DATA
}

/** Reports tab. Everything here is computed from data recorded on this phone. */
@Composable
fun ReportsScreen(
    screeningState: HearingScreeningUiState,
    volume: VolumeUiState,
    onOpenScreeningHistory: () -> Unit,
    onOpenMonitor: () -> Unit
) {
    var subScreen by remember { mutableStateOf(ReportsSubScreen.MAIN) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoBg)
    ) {
        when (subScreen) {
            ReportsSubScreen.MAIN -> ReportsMainScreen(
                screeningState = screeningState,
                volume = volume,
                onOpenMonitor = onOpenMonitor,
                onWeeklyReport = { subScreen = ReportsSubScreen.WEEKLY_REPORT },
                onExposureTrends = { subScreen = ReportsSubScreen.EXPOSURE_TRENDS },
                onInsights = { subScreen = ReportsSubScreen.INSIGHTS_TIPS },
                onHistoricalData = { subScreen = ReportsSubScreen.HISTORICAL_DATA }
            )

            ReportsSubScreen.WEEKLY_REPORT -> WeeklyReportSubScreen(
                volume = volume,
                screeningCount = screeningState.savedSessions.size,
                onBack = { subScreen = ReportsSubScreen.MAIN }
            )

            ReportsSubScreen.EXPOSURE_TRENDS -> ExposureTrendsSubScreen(
                volume = volume,
                onBack = { subScreen = ReportsSubScreen.MAIN }
            )

            ReportsSubScreen.INSIGHTS_TIPS -> InsightsTipsSubScreen(
                volume = volume,
                screeningState = screeningState,
                onBack = { subScreen = ReportsSubScreen.MAIN }
            )

            ReportsSubScreen.HISTORICAL_DATA -> HistoricalDataSubScreen(
                sessions = screeningState.savedSessions,
                volume = volume,
                onOpenScreeningHistory = onOpenScreeningHistory,
                onBack = { subScreen = ReportsSubScreen.MAIN }
            )
        }
    }
}

private fun weekSummary(volume: VolumeUiState): VolumeSummary? {
    val week = volume.week
    if (week.isEmpty()) return null
    val listening = week.sumOf { it.listeningMs }
    if (listening == 0L) return VolumeSummary(week.first().from, week.last().to)
    return VolumeSummary(
        from = week.first().from,
        to = week.last().to,
        listeningMs = listening,
        avgPercent = Math.round(week.sumOf { it.avgPercent.toDouble() * it.listeningMs } / listening).toInt(),
        peakPercent = week.maxOf { it.peakPercent },
        highMs = week.sumOf { it.highMs }
    )
}

@Composable
private fun NoVolumeDataCard(volume: VolumeUiState, onOpenMonitor: (() -> Unit)?) {
    EchoCard(borderColor = EchoPillBorder, containerColor = EchoSurfaceAlt) {
        Text(
            if (volume.tracking) "Tracking is on. No listening recorded yet." else "Volume tracking is off.",
            color = EchoNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (volume.tracking) "Play music or video and your listening will show up here."
            else "Turn on tracking in the Monitor tab to record your listening volume.",
            color = EchoTextMuted, fontSize = 13.sp, lineHeight = 18.sp
        )
        if (onOpenMonitor != null && !volume.tracking) {
            Spacer(modifier = Modifier.height(10.dp))
            EchoPrimaryButton(text = "Open Monitor", icon = Icons.Outlined.GraphicEq, onClick = onOpenMonitor)
        }
    }
}

@Composable
private fun ReportsMainScreen(
    screeningState: HearingScreeningUiState,
    volume: VolumeUiState,
    onOpenMonitor: () -> Unit,
    onWeeklyReport: () -> Unit,
    onExposureTrends: () -> Unit,
    onInsights: () -> Unit,
    onHistoricalData: () -> Unit
) {
    val week = weekSummary(volume)
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Listening Reports",
            actions = {
                EchoStatusPill(
                    label = if (volume.tracking) "Live" else "Tracking off",
                    icon = if (volume.tracking) Icons.Filled.GraphicEq else Icons.Outlined.PauseCircle
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoCard {
                Text("This Week", color = EchoNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                if (week == null || week.listeningMs == 0L) {
                    Text("No listening recorded in the last 7 days.", color = EchoTextMuted, fontSize = 14.sp)
                } else {
                    Text(
                        "${formatDuration(week.listeningMs)} of listening at an average volume of ${week.avgPercent}% " +
                            "(peak ${week.peakPercent}%).",
                        color = EchoNavy, fontSize = 14.5.sp, lineHeight = 21.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val highShare = (week.highMs * 100 / week.listeningMs).toInt()
                    Text(
                        if (week.highMs == 0L) "None of it was at ${VolumeAnalytics.HIGH_PERCENT}% volume or more."
                        else "${formatDuration(week.highMs)} ($highShare%) was at ${VolumeAnalytics.HIGH_PERCENT}% volume or more.",
                        color = if (week.highMs == 0L) Emerald500 else Rose500,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Saved screenings: ${screeningState.savedSessions.size}",
                    color = EchoPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium
                )
            }

            if (volume.loaded && volume.firstRecordAt == null) NoVolumeDataCard(volume, onOpenMonitor)

            EchoOutlinedPillButton(text = "Weekly Report", icon = Icons.Outlined.ViewWeek, onClick = onWeeklyReport)
            EchoOutlinedPillButton(text = "Exposure Trends", icon = Icons.AutoMirrored.Filled.TrendingUp, onClick = onExposureTrends)
            EchoOutlinedPillButton(text = "Insights & Tips", icon = Icons.Outlined.Lightbulb, onClick = onInsights)
            EchoOutlinedPillButton(text = "Historical Data", icon = Icons.Outlined.History, onClick = onHistoricalData)
        }
    }
}

@Composable
private fun WeeklyReportSubScreen(
    volume: VolumeUiState,
    screeningCount: Int,
    onBack: () -> Unit
) {
    val dayFmt = remember { java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()) }
    val maxMs = (volume.week.maxOfOrNull { it.listeningMs } ?: 0L).coerceAtLeast(60_000L)
    val week = weekSummary(volume)
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Weekly Report", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoCard {
                Text("Listening time per day", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Last 7 days", color = EchoTextMuted, fontSize = 12.5.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(130.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    volume.week.forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (d.listeningMs > 0) formatDuration(d.listeningMs) else "",
                                color = EchoTextMuted, fontSize = 9.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height((90f * d.listeningMs / maxMs).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(EchoPrimary)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(dayFmt.format(Date(d.from)).take(3), color = EchoTextMuted, fontSize = 11.sp)
                        }
                    }
                }
                if (week == null || week.listeningMs == 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No listening recorded this week.", color = EchoTextMuted, fontSize = 13.sp)
                }
            }
            EchoCard {
                Text("Daily breakdown", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                volume.week.reversed().forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.from)), color = EchoNavy, fontSize = 13.sp)
                        Text(
                            if (d.listeningMs == 0L) "—"
                            else "${formatDuration(d.listeningMs)} · avg ${d.avgPercent}% · high ${formatDuration(d.highMs)}",
                            color = if (d.highMs > 0) Rose500 else EchoTextMuted, fontSize = 12.5.sp
                        )
                    }
                }
            }
            EchoCard {
                Text("Recorded on this phone", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Listening sessions this week: ${volume.sessions.count { it.end >= (volume.week.firstOrNull()?.from ?: 0L) }}", color = EchoTextMuted, fontSize = 13.5.sp)
                Text("• Saved screening records: $screeningCount", color = EchoTextMuted, fontSize = 13.5.sp)
            }
        }
    }
}

@Composable
private fun ExposureTrendsSubScreen(volume: VolumeUiState, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Exposure Trends", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val total = volume.monthBands.values.sum()
            EchoCard {
                Text("How loud you listen", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Share of listening time by volume, last 30 days", color = EchoTextMuted, fontSize = 12.5.sp)
                Spacer(modifier = Modifier.height(10.dp))
                if (total == 0L) {
                    Text("No listening recorded in the last 30 days.", color = EchoTextMuted, fontSize = 13.sp)
                } else {
                    VolumeAnalytics.Band.entries.forEachIndexed { i, band ->
                        if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                        val ms = volume.monthBands[band] ?: 0L
                        TrendBar(
                            "${band.label} · ${formatDuration(ms)}",
                            ms.toFloat() / total,
                            when (band) {
                                VolumeAnalytics.Band.LOW -> Emerald500
                                VolumeAnalytics.Band.MODERATE -> EchoPrimary
                                VolumeAnalytics.Band.HIGH -> Rose500
                            }
                        )
                    }
                }
            }

            // Weekly averages over the last 4 weeks, oldest first.
            val weeks = volume.month.takeLast(28).chunked(7).map { days ->
                val ms = days.sumOf { it.listeningMs }
                Triple(days.first().from, ms, if (ms == 0L) null else Math.round(days.sumOf { it.avgPercent.toDouble() * it.listeningMs } / ms).toInt())
            }
            EchoCard {
                Text("Average volume by week", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("A steady rise can be an early sign of hearing change", color = EchoTextMuted, fontSize = 12.5.sp)
                Spacer(modifier = Modifier.height(10.dp))
                weeks.forEach { (from, ms, avg) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Week of ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(from))}", color = EchoNavy, fontSize = 13.sp)
                        Text(
                            if (avg == null) "—" else "avg $avg% · ${formatDuration(ms)}",
                            color = if (avg != null && avg >= VolumeAnalytics.HIGH_PERCENT) Rose500 else EchoTextMuted,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(driftText(volume.drift), color = EchoNavy, fontSize = 13.sp, lineHeight = 18.sp)
            }

            val outputs = AudioOutput.entries.associateWith { o -> volume.month.sumOf { it.byOutput[o] ?: 0L } }.filterValues { it > 0 }
            if (outputs.isNotEmpty()) {
                val all = outputs.values.sum()
                EchoCard {
                    Text("Listening by output", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Last 30 days", color = EchoTextMuted, fontSize = 12.5.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    outputs.entries.sortedByDescending { it.value }.forEachIndexed { i, (o, ms) ->
                        if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                        TrendBar("${o.label} · ${formatDuration(ms)}", ms.toFloat() / all, EchoPrimary)
                    }
                }
            }
        }
    }
}

private fun driftText(drift: DriftStatus): String = when (drift) {
    is DriftStatus.Rising ->
        "Your volume on ${drift.output.label.lowercase()} rose from about ${drift.baselineAvg}% to ${drift.recentAvg}%. Consider taking a hearing test."
    is DriftStatus.Stable ->
        "Steady: about ${drift.recentAvg}% this week on ${drift.output.label.lowercase()}, against ${drift.baselineAvg}% before."
    is DriftStatus.NotEnoughData ->
        "Trend check needs about ${VolumeDrift.DAYS_NEEDED} days of tracking (${drift.daysTracked.coerceAtMost(VolumeDrift.DAYS_NEEDED)} so far)."
}

@Composable
private fun TrendBar(label: String, fraction: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = EchoNavy, fontSize = 13.sp)
            Text("${(fraction * 100).toInt()}%", color = EchoNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = EchoPrimarySoft,
            drawStopIndicator = {}
        )
    }
}

/** Insights generated from the user's own data, followed by general safe-listening tips. */
@Composable
private fun InsightsTipsSubScreen(volume: VolumeUiState, screeningState: HearingScreeningUiState, onBack: () -> Unit) {
    val insights = buildList {
        val week = weekSummary(volume)
        if (volume.drift is DriftStatus.Rising) add("Your listening volume is rising" to driftText(volume.drift))
        if (week != null && week.highMs > 0) {
            add(
                "High-volume listening" to
                    "You spent ${formatDuration(week.highMs)} at ${VolumeAnalytics.HIGH_PERCENT}% volume or more this week. " +
                    "Lowering it a few steps makes a big difference over time."
            )
        }
        val loudestOutput = volume.week.flatMap { it.byOutput.entries }.groupBy { it.key }
            .mapValues { (_, v) -> v.sumOf { it.value } }.maxByOrNull { it.value }?.key
        if (week != null && week.listeningMs > 0 && loudestOutput != null) {
            add("Most used output" to "Most of your listening this week was on ${loudestOutput.label.lowercase()}.")
        }
        val latest = screeningState.savedSessions.firstOrNull()
        if (latest == null) {
            add("No hearing screening yet" to "Take a screening in the Test tab to record a baseline you can compare against later.")
        } else {
            val days = ((System.currentTimeMillis() - latest.startedAt) / (24 * 60 * 60 * 1000L)).toInt()
            add("Last screening" to "Your last screening was $days day(s) ago.")
        }
    }
    val tips = listOf(
        "60/60 guideline" to "Listen at no more than about 60% volume for no more than about 60 minutes at a time, then take a break.",
        "Noise-cancelling headphones" to "They block background noise, so you don't need to turn the volume up as much.",
        "Quiet screening room" to "Run hearing screenings somewhere quiet so background noise doesn't affect the result."
    )
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Insights & Tips", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("From your data", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (insights.isEmpty()) {
                Text("No insights yet. Turn on tracking in the Monitor tab.", color = EchoTextMuted, fontSize = 13.sp)
            }
            insights.forEach { (t, d) ->
                EchoCard {
                    Text(t, color = EchoNavy, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(d, color = EchoTextMuted, fontSize = 13.5.sp, lineHeight = 19.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Safe listening tips", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            tips.forEach { (t, d) ->
                EchoCard {
                    Text(t, color = EchoNavy, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(d, color = EchoTextMuted, fontSize = 13.5.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun HistoricalDataSubScreen(
    sessions: List<HearingSessionRecord>,
    volume: VolumeUiState,
    onOpenScreeningHistory: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Historical Data", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoCard {
                Text("Screening Sessions (${sessions.size})", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                if (sessions.isEmpty()) {
                    Text("No saved screenings yet.", color = EchoTextMuted, fontSize = 13.sp)
                } else {
                    sessions.take(5).forEach { rec ->
                        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(rec.startedAt))
                        Text("• $date — ${rec.screeningOutcome?.overallClassification?.displayName ?: "Saved"}", color = EchoTextMuted, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                EchoOutlinedPillButton(text = "Open Full Screening History", onClick = onOpenScreeningHistory)
            }

            EchoCard {
                val days = volume.month.filter { it.listeningMs > 0 }.reversed()
                Text("Daily Listening (${days.size} days)", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Last 30 days", color = EchoTextMuted, fontSize = 12.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                if (days.isEmpty()) {
                    Text("No listening recorded yet.", color = EchoTextMuted, fontSize = 13.sp)
                } else {
                    days.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.from)), color = EchoNavy, fontSize = 13.sp)
                            Text(
                                "${formatDuration(d.listeningMs)} · avg ${d.avgPercent}% · peak ${d.peakPercent}%",
                                color = if (d.peakPercent >= VolumeAnalytics.HIGH_PERCENT) Rose500 else EchoTextMuted,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- PROFILE TAB (Image 10) ----

private enum class ProfileSubScreen {
    MAIN,
    EDIT_PROFILE,
    CONNECTED_DEVICES,
    NOTIFICATIONS,
    PRIVACY_DATA,
    EXPORT_DATA,
    ABOUT_ECHO,
    HELP_FAQ
}

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onUpdateProfile: (UserProfile) -> Unit,
    screeningState: HearingScreeningUiState,
    monitorState: MonitorUiState,
    volume: VolumeUiState,
    onRefreshDeviceRoute: () -> Unit,
    onRestartOnboarding: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    var subScreen by remember { mutableStateOf(ProfileSubScreen.MAIN) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoBg)
    ) {
        when (subScreen) {
            ProfileSubScreen.MAIN -> ProfileMainScreen(
                profile = profile,
                onEditProfile = { subScreen = ProfileSubScreen.EDIT_PROFILE },
                onConnectedDevices = { subScreen = ProfileSubScreen.CONNECTED_DEVICES },
                onNotifications = { subScreen = ProfileSubScreen.NOTIFICATIONS },
                onPrivacy = { subScreen = ProfileSubScreen.PRIVACY_DATA },
                onExport = { subScreen = ProfileSubScreen.EXPORT_DATA },
                onAbout = { subScreen = ProfileSubScreen.ABOUT_ECHO },
                onHelp = { subScreen = ProfileSubScreen.HELP_FAQ },
                onSignOut = onSignOut
            )

            ProfileSubScreen.EDIT_PROFILE -> EditProfileSubScreen(
                profile = profile,
                onSave = {
                    onUpdateProfile(it)
                    subScreen = ProfileSubScreen.MAIN
                },
                onBack = { subScreen = ProfileSubScreen.MAIN }
            )

            ProfileSubScreen.CONNECTED_DEVICES -> ConnectedDevicesSubScreen(
                screeningState = screeningState,
                monitorState = monitorState,
                volume = volume,
                onRefresh = onRefreshDeviceRoute,
                onBack = { subScreen = ProfileSubScreen.MAIN }
            )

            ProfileSubScreen.NOTIFICATIONS -> NotificationsSubScreen(
                profile = profile,
                onUpdateProfile = onUpdateProfile,
                onBack = { subScreen = ProfileSubScreen.MAIN }
            )

            ProfileSubScreen.PRIVACY_DATA -> PrivacyDataSubScreen(
                onRestartOnboarding = onRestartOnboarding,
                onBack = { subScreen = ProfileSubScreen.MAIN }
            )

            ProfileSubScreen.EXPORT_DATA -> ExportDataSubScreen(
                profile = profile,
                sessions = screeningState.savedSessions,
                volume = volume,
                onBack = { subScreen = ProfileSubScreen.MAIN }
            )

            ProfileSubScreen.ABOUT_ECHO -> AboutEchoSubScreen(
                onBack = { subScreen = ProfileSubScreen.MAIN }
            )

            ProfileSubScreen.HELP_FAQ -> HelpFaqSubScreen(
                onBack = { subScreen = ProfileSubScreen.MAIN }
            )
        }
    }
}

@Composable
private fun ProfileMainScreen(
    profile: UserProfile,
    onEditProfile: () -> Unit,
    onConnectedDevices: () -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    onExport: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Profile & Settings")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile Card (Image 10)
            EchoCard(modifier = Modifier.clickable(onClick = onEditProfile)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EchoIconBadge(
                        icon = Icons.Filled.Person,
                        sizeDp = 58,
                        iconSizeDp = 30,
                        isCircle = true,
                        containerColor = EchoPrimarySoft,
                        tint = EchoPrimary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.preferredName.ifBlank { "Add your name" },
                            color = EchoNavy,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Standard Listening Profile",
                            color = EchoTextMuted,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "${profile.ageRange} · ${profile.preferredLanguage} · ${profile.accountMode}",
                            color = EchoTextLight,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile", tint = EchoPrimary)
                }
            }

            // Six signature pill buttons from Image 10
            EchoOutlinedPillButton(
                text = "Connected Devices",
                icon = Icons.Outlined.Headphones,
                onClick = onConnectedDevices
            )
            EchoOutlinedPillButton(
                text = "Notifications",
                icon = Icons.Outlined.Notifications,
                onClick = onNotifications
            )
            EchoOutlinedPillButton(
                text = "Privacy & Data",
                icon = Icons.Outlined.Lock,
                onClick = onPrivacy
            )
            EchoOutlinedPillButton(
                text = "Export Data",
                icon = Icons.Outlined.Download,
                onClick = onExport
            )
            EchoOutlinedPillButton(
                text = "About ECHO",
                icon = Icons.Outlined.Info,
                onClick = onAbout
            )
            EchoOutlinedPillButton(
                text = "Help & FAQ",
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                onClick = onHelp
            )
            EchoOutlinedPillButton(
                text = "Sign Out / Switch Account",
                icon = Icons.AutoMirrored.Outlined.Logout,
                onClick = onSignOut
            )
        }
    }
}

@Composable
private fun EditProfileSubScreen(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(profile.preferredName) }
    var ageRange by remember { mutableStateOf(profile.ageRange) }
    var language by remember { mutableStateOf(profile.preferredLanguage) }

    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Edit Profile", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preferred Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Age Range", color = EchoNavy, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("18–35", "36–50", "51–65", "65+").forEach { opt ->
                    EchoChoiceChip(label = opt, selected = ageRange == opt, onClick = { ageRange = opt })
                }
            }
            Text("Preferred Language", color = EchoNavy, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("English", "Spanish", "French", "German").forEach { opt ->
                    EchoChoiceChip(label = opt, selected = language == opt, onClick = { language = opt })
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            EchoPrimaryButton(
                text = "Save Profile",
                onClick = { onSave(profile.copy(preferredName = name.trim(), ageRange = ageRange, preferredLanguage = language)) }
            )
        }
    }
}

@Composable
private fun ConnectedDevicesSubScreen(
    screeningState: HearingScreeningUiState,
    monitorState: MonitorUiState,
    volume: VolumeUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Connected Devices", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoCard {
                Text("Active Screening Route", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    screeningState.audioRoute?.deviceName ?: "No route detected",
                    color = EchoPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (screeningState.isHeadphoneConnected) "Supported headphone route" else "Speaker active (headphones required for screening)",
                    color = EchoTextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                EchoOutlinedPillButton(text = "Refresh Audio Route", icon = Icons.Default.Refresh, onClick = onRefresh)
            }
            EchoCard {
                Text("Media Output Now", color = EchoNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(volume.output.label, color = EchoPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Volume ${volume.volume.percent}% · ${if (volume.playing) "playing" else "nothing playing"}",
                    color = EchoTextMuted,
                    fontSize = 13.sp
                )
            }
            EchoCard {
                Text("Audio Outputs Connected (${monitorState.outputs.size})", color = EchoNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                monitorState.outputs.forEach { Text("• ${it.label}", color = EchoTextMuted, fontSize = 12.5.sp) }
            }
        }
    }
}

@Composable
private fun NotificationsSubScreen(
    profile: UserProfile,
    onUpdateProfile: (UserProfile) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Notifications", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Screening & Listening Alerts", color = EchoNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Reminder cadence: ${profile.reminderFrequency}", color = EchoTextMuted, fontSize = 13.sp)
                    }
                    Switch(
                        checked = profile.notificationsEnabled,
                        onCheckedChange = { onUpdateProfile(profile.copy(notificationsEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EchoPrimary)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyDataSubScreen(
    onRestartOnboarding: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Privacy & Data", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoCard {
                Text("Offline-First Privacy Guarantee", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• Microphone audio for the screening noise check is processed in memory only and never saved.\n" +
                        "• Volume tracking records only your phone's volume setting, whether media is playing, and the output type. It does not use the microphone or know what you listen to.\n" +
                        "• Screening sessions and volume history are stored only in this app's private storage on the phone. Nothing is sent to a server.",
                    color = EchoTextMuted,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp
                )
            }
            EchoOutlinedPillButton(
                text = "Re-open Welcome & Health Notice",
                icon = Icons.Outlined.Replay,
                onClick = onRestartOnboarding
            )
        }
    }
}

@Composable
private fun ExportDataSubScreen(
    profile: UserProfile,
    sessions: List<HearingSessionRecord>,
    volume: VolumeUiState,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val jsonPreview = remember(profile, sessions, volume.month, volume.sessions) {
        GsonBuilder().setPrettyPrinting().create().toJson(
            mapOf(
                "profile" to profile,
                "screeningSessions" to sessions,
                "dailyListening" to volume.month.filter { it.listeningMs > 0 }.map { d ->
                    mapOf(
                        "date" to DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.from)),
                        "listeningMinutes" to d.listeningMs / 60_000,
                        "averageVolumePercent" to d.avgPercent,
                        "peakVolumePercent" to d.peakPercent,
                        "highVolumeMinutes" to d.highMs / 60_000
                    )
                },
                "listeningSessions" to volume.sessions.map { s ->
                    mapOf(
                        "start" to DateFormat.getDateTimeInstance().format(Date(s.start)),
                        "minutes" to s.durationMs / 60_000,
                        "averageVolumePercent" to s.avgPercent,
                        "peakVolumePercent" to s.peakPercent,
                        "output" to s.output.label
                    )
                }
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Export Data", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoPrimaryButton(
                text = if (copied) "Copied JSON to Clipboard" else "Copy Export JSON",
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    clipboard.setText(AnnotatedString(jsonPreview))
                    copied = true
                }
            )
            EchoCard {
                Text("JSON Export Preview", color = EchoNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = jsonPreview.take(3000),
                    color = EchoTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun AboutEchoSubScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "About ECHO", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EchoCard {
                Text("ECHO Integrated POC", color = EchoNavy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Combines pure-tone hearing screening (Modified Hughson–Westlake and Binary Search, with ambient noise gating) a live hearing aid (microphone to earbuds with noise gate, denoiser, 4-band compression and per-ear EQ), and background phone-volume tracking with listening analytics, high-volume alerts and hearing-test suggestions.\n\n" +
                        "Engineering prototype only — not a medical device or calibrated hearing aid.",
                    color = EchoTextMuted,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun HelpFaqSubScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(title = "Help & FAQ", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EchoCard {
                Text("Why are results reported in dBFS instead of dB HL?", color = EchoNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Consumer phones and earbuds lack IEC 60318-1/4 artificial-ear calibration, so ECHO honestly reports digital attenuation steps (dBFS).",
                    color = EchoTextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
            EchoCard {
                Text("What does the volume percentage mean?", color = EchoNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "It is your phone's media volume setting (0–100%), not a measured sound level. The same percentage can be louder on some earbuds than others, so ECHO compares your volume per output type.",
                    color = EchoTextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

