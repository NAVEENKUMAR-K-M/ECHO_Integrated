package com.echo.hearing.volume

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echo.hearing.ui.components.*
import com.echo.hearing.ui.theme.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Monitor tab: records the phone's media volume in the background and shows listening analytics.
 * No microphone or audio output is used.
 */
@Composable
fun VolumeMonitorScreen(
    vm: VolumeViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onTakeHearingTest: () -> Unit = {}
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    // Notifications are optional: tracking still runs without them, the status notification is just hidden.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.startTracking()
    }
    val start = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.startTracking()
    }

    Column(modifier = modifier.fillMaxSize().background(EchoBg)) {
        EchoTopBar(
            title = "Volume Monitor",
            actions = {
                EchoStatusPill(
                    label = if (s.tracking) "Tracking" else "Paused",
                    icon = if (s.tracking) Icons.Filled.GraphicEq else Icons.Outlined.PauseCircle,
                    containerColor = if (s.tracking) EmeraldSoft else EchoSurfaceAlt,
                    borderColor = if (s.tracking) Emerald500 else EchoPillBorder,
                    contentColor = if (s.tracking) Emerald500 else EchoPrimary
                )
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CurrentVolumeCard(s, onStart = start, onStop = vm::stopTracking)
            HearingCheckCard(s.drift, onTakeHearingTest)
            TodayCard(s)
            TimelineCard(s)
            WeekCard(s)
            OutputCard(s.week)
            SessionsCard(s.sessions)

            EchoOutlinedPillButton(
                text = "Clear volume history",
                icon = Icons.Outlined.DeleteOutline,
                onClick = { confirmClear = true },
                enabled = s.firstRecordAt != null
            )
            Text(
                "Volume is the phone's media volume setting (0–100%), not a measured sound level in dB. " +
                    "Records stay on this phone and are kept for ${VolumeLog.RETENTION_DAYS} days. " +
                    "No microphone is used.",
                color = EchoTextMuted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear volume history?") },
            text = { Text("This deletes every recorded volume entry on this phone. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearHistory() }) {
                    Text("Clear", color = Rose500)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

// ---- cards ---------------------------------------------------------------------------------------

@Composable
private fun CurrentVolumeCard(s: VolumeUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val high = s.volume.percent >= VolumeAnalytics.HIGH_PERCENT
    EchoCard {
        Text("Phone volume now", color = EchoTextMuted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.volume.percent}%", color = EchoNavy, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(10.dp))
            Text(
                "step ${s.volume.level} of ${s.volume.max}",
                color = EchoTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 9.dp)
            )
        }
        LinearProgressIndicator(
            progress = { s.volume.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (high) Rose500 else EchoPrimary,
            trackColor = EchoPrimarySoft,
            drawStopIndicator = {}
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (s.playing) Icons.Filled.PlayCircle else Icons.Outlined.PauseCircle,
                contentDescription = null,
                tint = if (s.playing) Emerald500 else EchoTextLight,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (s.playing) "Playing on ${s.output.label}" else "Nothing playing · ${s.output.label}",
                color = EchoNavy,
                fontSize = 13.sp
            )
        }
        if (high) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("High volume (${VolumeAnalytics.HIGH_PERCENT}% or more)", color = Rose500, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (s.tracking) {
            EchoPrimaryButton(text = "Stop tracking", icon = Icons.Default.Stop, containerColor = Rose500, onClick = onStop)
            Spacer(Modifier.height(6.dp))
            Text(
                "Recording in the background, even when ECHO is closed. Stop it here or from the notification.",
                color = EchoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
        } else {
            EchoPrimaryButton(text = "Start tracking", icon = Icons.Default.PlayArrow, onClick = onStart)
            Spacer(Modifier.height(6.dp))
            Text(
                "Records your phone volume and listening time in the background. It also resumes after a restart.",
                color = EchoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
    }
}

/** Compares recent listening volume with the user's baseline and explains the alerts. */
@Composable
private fun HearingCheckCard(drift: DriftStatus, onTakeHearingTest: () -> Unit) {
    val rising = drift as? DriftStatus.Rising
    EchoCard(
        borderColor = if (rising != null) Rose500 else EchoBorder,
        containerColor = if (rising != null) RoseSoft else EchoSurface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (rising != null) Icons.Filled.Warning else Icons.Outlined.Hearing,
                contentDescription = null,
                tint = if (rising != null) Rose500 else EchoPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (rising != null) "Consider a hearing test" else "Listening volume trend",
                color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        val body = when (drift) {
            is DriftStatus.Rising ->
                "Your listening volume on ${drift.output.label.lowercase()} rose from about ${drift.baselineAvg}% " +
                    "to ${drift.recentAvg}% this week, and it was higher on ${drift.daysAbove} separate days. " +
                    "A steady rise like this can be an early sign of hearing change."
            is DriftStatus.Stable ->
                "Steady. On ${drift.output.label.lowercase()} you listened at about ${drift.recentAvg}% this week, " +
                    "compared with ${drift.baselineAvg}% over the 4 weeks before."
            is DriftStatus.NotEnoughData ->
                "ECHO compares your last week of listening with the 4 weeks before it, and suggests a hearing " +
                    "test if your volume rises steadily. This needs about ${VolumeDrift.DAYS_NEEDED} days of tracking " +
                    "(${drift.daysTracked.coerceAtMost(VolumeDrift.DAYS_NEEDED)} so far)."
        }
        Text(body, color = EchoNavy, fontSize = 13.sp, lineHeight = 18.sp)
        if (rising != null) {
            Spacer(Modifier.height(12.dp))
            EchoPrimaryButton(text = "Take hearing test", icon = Icons.Outlined.Hearing, onClick = onTakeHearingTest)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "You also get a notification after 10 minutes of listening at ${VolumeAnalytics.HIGH_PERCENT}% volume or more.",
            color = EchoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
        )
    }
}

@Composable
private fun TodayCard(s: VolumeUiState) {
    val t = s.today
    EchoCard {
        Text("Today", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Listening time", formatDuration(t.listeningMs), Modifier.weight(1f))
            StatTile("Average volume", if (t.listeningMs > 0) "${t.avgPercent}%" else "–", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Peak volume", if (t.listeningMs > 0) "${t.peakPercent}%" else "–", Modifier.weight(1f))
            StatTile(
                "At ${VolumeAnalytics.HIGH_PERCENT}% or more",
                formatDuration(t.highMs),
                Modifier.weight(1f),
                alert = t.highMs > 0
            )
        }
        if (!s.tracking && t.listeningMs == 0L) {
            Spacer(Modifier.height(10.dp))
            Text("Start tracking to fill in today's numbers.", color = EchoTextMuted, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, alert: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (alert) RoseSoft else EchoSurfaceMuted)
            .padding(12.dp)
    ) {
        Text(label, color = EchoTextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (alert) {
                Icon(Icons.Filled.Warning, contentDescription = "High", tint = Rose500, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(value, color = EchoNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Today's volume over 24 hours: solid where media played, faint where nothing played. Touch to inspect. */
@Composable
private fun TimelineCard(s: VolumeUiState) {
    val dayStart = remember(s.today.from) { s.today.from }
    val dayMs = 24 * 60 * 60 * 1000L
    var probe by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    val stroke = with(density) { 2.dp.toPx() }
    val faint = with(density) { 1.5.dp.toPx() }
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    EchoCard {
        Text("Today's volume", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        val probeText = probe?.let { fx ->
            val t = dayStart + (fx * dayMs).toLong()
            val seg = s.todaySegments.lastOrNull { it.start <= t && it.end >= t }
            when {
                seg == null -> "${timeFmt.format(Date(t))} · not recorded"
                seg.playing -> "${timeFmt.format(Date(t))} · ${seg.percent}% · playing on ${seg.output.label}"
                else -> "${timeFmt.format(Date(t))} · ${seg.percent}% · nothing playing"
            }
        }
        Text(
            probeText ?: "Touch the chart to see the volume at a time.",
            color = if (probeText != null) EchoNavy else EchoTextMuted,
            fontSize = 12.5.sp
        )
        Spacer(Modifier.height(10.dp))

        val gridColor = EchoBorder
        val idleColor = EchoTextLight.copy(alpha = 0.55f)
        val lineColor = EchoPrimary
        val highColor = Rose500
        val crossColor = EchoNavy.copy(alpha = 0.5f)
        Row {
            // Y axis labels
            Column(
                modifier = Modifier.height(150.dp).padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text("100%", color = EchoTextLight, fontSize = 10.sp)
                Text("50%", color = EchoTextLight, fontSize = 10.sp)
                Text("0%", color = EchoTextLight, fontSize = 10.sp)
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { probe = (it.x / size.width).coerceIn(0f, 1f) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = {}) { change, _ ->
                            probe = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                fun x(t: Long) = ((t - dayStart).toFloat() / dayMs).coerceIn(0f, 1f) * w
                fun y(p: Int) = h - (p / 100f) * h

                // Recessive grid: 0, 50, 100 and 6-hour verticals
                for (p in listOf(0, 50, 100)) drawLine(gridColor, Offset(0f, y(p)), Offset(w, y(p)), 1f)
                for (hr in listOf(6, 12, 18)) {
                    val gx = w * hr / 24f
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 1f)
                }
                // High-volume reference line
                drawLine(
                    highColor.copy(alpha = 0.7f), Offset(0f, y(VolumeAnalytics.HIGH_PERCENT)), Offset(w, y(VolumeAnalytics.HIGH_PERCENT)),
                    1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )

                // Idle stretches: faint
                for (seg in s.todaySegments.filter { !it.playing }) {
                    drawLine(idleColor, Offset(x(seg.start), y(seg.percent)), Offset(x(seg.end), y(seg.percent)), faint)
                }
                // Playing stretches: a stepped line, broken where nothing was playing
                var path: Path? = null
                var lastEnd = 0L
                var lastY = 0f
                for (seg in s.todaySegments.filter { it.playing }.sortedBy { it.start }) {
                    val sy = y(seg.percent)
                    val p = path
                    if (p != null && seg.start - lastEnd <= VolumeRecorder.DEFAULT_MAX_GAP_MS) {
                        p.lineTo(x(seg.start), lastY)
                        p.lineTo(x(seg.start), sy)
                    } else {
                        p?.let { drawPath(it, lineColor, style = Stroke(stroke, cap = StrokeCap.Round)) }
                        path = Path().apply { moveTo(x(seg.start), sy) }
                    }
                    path!!.lineTo(x(seg.end), sy)
                    lastEnd = seg.end
                    lastY = sy
                }
                path?.let { drawPath(it, lineColor, style = Stroke(stroke, cap = StrokeCap.Round)) }

                probe?.let { fx ->
                    drawLine(crossColor, Offset(fx * w, 0f), Offset(fx * w, h), 1.5f)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach { Text(it, color = EchoTextLight, fontSize = 10.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch(EchoPrimary, solid = true); Text(" Playing", color = EchoTextMuted, fontSize = 11.5.sp)
            Spacer(Modifier.width(12.dp))
            LegendSwatch(EchoTextLight, solid = true); Text(" Nothing playing", color = EchoTextMuted, fontSize = 11.5.sp)
            Spacer(Modifier.width(12.dp))
            LegendSwatch(Rose500, solid = false); Text(" High (${VolumeAnalytics.HIGH_PERCENT}%)", color = EchoTextMuted, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, solid: Boolean) {
    Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
            pathEffect = if (solid) null else PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
        )
    }
}

/** Listening time per day for the last 7 days. Tap a bar to see that day. */
@Composable
private fun WeekCard(s: VolumeUiState) {
    if (s.week.isEmpty()) return
    var selected by remember(s.week.size) { mutableIntStateOf(s.week.lastIndex) }
    val day = s.week.getOrNull(selected) ?: s.week.last()
    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val longFmt = remember { SimpleDateFormat("EEEE d MMM", Locale.getDefault()) }
    val maxMs = (s.week.maxOf { it.listeningMs }).coerceAtLeast(60_000L)

    EchoCard {
        Text("Last 7 days", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Listening time per day", color = EchoTextMuted, fontSize = 12.5.sp)
        Spacer(Modifier.height(12.dp))

        val barColor = EchoPrimary
        val dimColor = EchoPrimary.copy(alpha = 0.45f)
        val baseColor = EchoBorder
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(s.week.size) {
                    detectTapGestures { pos ->
                        selected = (pos.x / (size.width / s.week.size)).toInt().coerceIn(0, s.week.lastIndex)
                    }
                }
        ) {
            val slot = size.width / s.week.size
            val barW = slot * 0.5f
            val radius = 4.dp.toPx()
            drawLine(baseColor, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            s.week.forEachIndexed { i, d ->
                val bh = (d.listeningMs.toFloat() / maxMs) * (size.height - 4.dp.toPx())
                if (bh <= 0f) return@forEachIndexed
                val left = i * slot + (slot - barW) / 2
                val top = size.height - bh
                val color = if (i == selected) barColor else dimColor
                // Rounded top, square base anchored to the baseline
                drawRoundRect(color, Offset(left, top), Size(barW, bh), CornerRadius(radius, radius))
                if (bh > radius) drawRect(color, Offset(left, size.height - radius), Size(barW, radius))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            s.week.forEachIndexed { i, d ->
                Text(
                    dayFmt.format(Date(d.from)).take(3),
                    color = if (i == selected) EchoNavy else EchoTextLight,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = EchoBorder)
        Spacer(Modifier.height(10.dp))
        Text(longFmt.format(Date(day.from)), color = EchoNavy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (day.listeningMs == 0L) {
            Text("No listening recorded.", color = EchoTextMuted, fontSize = 13.sp)
        } else {
            DetailLine("Listening time", formatDuration(day.listeningMs))
            DetailLine("Average volume", "${day.avgPercent}%")
            DetailLine("Peak volume", "${day.peakPercent}%")
            DetailLine("At ${VolumeAnalytics.HIGH_PERCENT}% or more", formatDuration(day.highMs))
        }
        Spacer(Modifier.height(8.dp))
        val weekListening = s.week.sumOf { it.listeningMs }
        val weekHigh = s.week.sumOf { it.highMs }
        Text(
            "7-day total: ${formatDuration(weekListening)} listening, ${formatDuration(weekHigh)} at high volume.",
            color = EchoTextMuted, fontSize = 12.5.sp
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = EchoTextMuted, fontSize = 13.sp)
        Text(value, color = EchoNavy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Where the last 7 days of listening happened. */
@Composable
private fun OutputCard(week: List<VolumeSummary>) {
    val totals = AudioOutput.entries.associateWith { o -> week.sumOf { it.byOutput[o] ?: 0L } }.filterValues { it > 0 }
    if (totals.isEmpty()) return
    val all = totals.values.sum().coerceAtLeast(1)
    EchoCard {
        Text("Listening by output", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Last 7 days", color = EchoTextMuted, fontSize = 12.5.sp)
        Spacer(Modifier.height(10.dp))
        totals.entries.sortedByDescending { it.value }.forEach { (output, ms) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(output.label, color = EchoNavy, fontSize = 13.sp)
                Text("${formatDuration(ms)} · ${ms * 100 / all}%", color = EchoTextMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { ms.toFloat() / all },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = EchoPrimary,
                trackColor = EchoPrimarySoft,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SessionsCard(sessions: List<ListeningSession>) {
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    EchoCard {
        Text("Listening sessions", color = EchoNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (sessions.isEmpty()) {
            Text("No listening recorded yet. Sessions appear here when media plays while tracking is on.", color = EchoTextMuted, fontSize = 13.sp)
            return@EchoCard
        }
        sessions.forEachIndexed { i, x ->
            if (i > 0) HorizontalDivider(color = EchoBorder, modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${dateFmt.format(Date(x.start))} · ${timeFmt.format(Date(x.start))}–${timeFmt.format(Date(x.end))}",
                        color = EchoNavy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${formatDuration(x.durationMs)} · ${x.output.label}",
                        color = EchoTextMuted, fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("avg ${x.avgPercent}%", color = EchoNavy, fontSize = 13.sp)
                    val high = x.peakPercent >= VolumeAnalytics.HIGH_PERCENT
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (high) Icon(Icons.Filled.Warning, contentDescription = "High", tint = Rose500, modifier = Modifier.size(12.dp))
                        Text(" peak ${x.peakPercent}%", color = if (high) Rose500 else EchoTextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Short listening duration such as "1h 20m", "12m" or "<1m". */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMin = ms / 60_000
    if (totalMin == 0L) return "<1m"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
