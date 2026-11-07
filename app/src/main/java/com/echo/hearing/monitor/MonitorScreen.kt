package com.echo.hearing.monitor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.echo.hearing.ui.theme.Amber500
import com.echo.hearing.ui.theme.Cyan400
import com.echo.hearing.ui.theme.Slate400
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MonitorScreen(vm: MonitorViewModel, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.onPermissionDenied()
    }
    val startOrAsk = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- always visible: route, level, start/stop ----
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DevicePicker("Microphone", s.inputs, s.inputId, !s.running, vm::selectInput)
                DevicePicker("Output (earbuds)", s.outputs, s.outputId, !s.running, vm::selectOutput)
                LabeledSlider(
                    "Volume trim: ${(s.monitorLevel * 100).roundToInt()}% (max 50%)",
                    s.monitorLevel, 0f..MonitorLimits.MAX_MONITOR_LEVEL, 49, vm::setMonitorLevel
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Processed", Modifier.width(88.dp), fontSize = 14.sp)
                    Switch(checked = s.processed, onCheckedChange = vm::setProcessed)
                    Text(
                        if (s.processed) "  ECHO processing on" else "  Baseline (bypass)",
                        color = Slate400, fontSize = 13.sp
                    )
                }
                if (s.running) {
                    Button(onClick = { vm.stop() }, Modifier.fillMaxWidth()) { Text("Stop monitor") }
                } else {
                    Button(onClick = { startOrAsk() }, Modifier.fillMaxWidth()) { Text("Start monitor") }
                }
                Text(s.status, color = if (s.running) Cyan400 else Slate400, fontSize = 13.sp)
                Text(
                    "Start with low phone and earbud volume. Prototype only; not a hearing aid. " +
                        "Digital limits are not calibrated to dB SPL.",
                    color = Amber500, fontSize = 12.sp
                )
            }
        }

        Section("Noise and speech processing") {
            ToggleRow("Noise gate (quiet-period hiss)", s.gateEnabled, vm::setGateEnabled)
            if (s.gateEnabled) {
                LabeledSlider(
                    "Threshold: ${s.gateThresholdDb.roundToInt()} dBFS",
                    s.gateThresholdDb, MonitorLimits.MIN_GATE_THRESHOLD_DB..MonitorLimits.MAX_GATE_THRESHOLD_DB,
                    35, vm::setGateThreshold
                )
            }
            ToggleRow("Adaptive noise reduction (experimental)", s.denoiserEnabled, vm::setDenoiserEnabled)
            if (s.denoiserEnabled) {
                LabeledSlider(
                    "Max attenuation: ${s.denoiserStrengthDb.roundToInt()} dB",
                    s.denoiserStrengthDb, 0f..MonitorLimits.MAX_DENOISER_DB, 17, vm::setDenoiserStrength
                )
            }
            ToggleRow("Speech compression (4-band)", s.compressionEnabled, vm::setCompressionEnabled)
            if (s.compressionEnabled) {
                LabeledSlider(
                    String.format(Locale.US, "Ratio: %.1f:1", s.compressionRatio),
                    s.compressionRatio, 1f..3f, 19
                ) { vm.setCompressionRatio((it * 10).roundToInt() / 10f) }
            }
        }

        Section("Hearing profile (per ear)") {
            Text(
                "Fitting gain offsets, ±6 dB per band. Use only gains from an audiogram-based fitting.",
                color = Slate400, fontSize = 12.sp
            )
            listOf("Left ear", "Right ear").forEachIndexed { ear, name ->
                Text(name, style = MaterialTheme.typography.titleSmall, color = Cyan400)
                MonitorLimits.EQ_CENTERS_HZ.forEachIndexed { band, hz ->
                    LabeledSlider(
                        "${freq(hz)}: ${String.format(Locale.US, "%+.1f", s.gains[ear][band])} dB",
                        s.gains[ear][band], -MonitorLimits.MAX_GAIN_DB..MonitorLimits.MAX_GAIN_DB, 23
                    ) { vm.setGain(ear, band, (it * 2).roundToInt() / 2f) }
                }
            }
        }

        Section("Audiogram and verification record") {
            Text(
                "Reference only: thresholds do not change any gain. Enter dB HL from a professional audiogram, " +
                    "or import the latest ECHO screening (uncalibrated).",
                color = Slate400, fontSize = 12.sp
            )
            OutlinedButton(onClick = vm::importLatestScreening, Modifier.fillMaxWidth()) {
                Text("Import latest screening")
            }
            if (s.importMessage.isNotEmpty()) Text(s.importMessage, fontSize = 12.sp, color = Cyan400)
            listOf("Left ear", "Right ear").forEachIndexed { ear, name ->
                Text(name + " (dB HL)", style = MaterialTheme.typography.titleSmall, color = Cyan400)
                MonitorLimits.EQ_CENTERS_HZ.toList().chunked(3).forEachIndexed { rowIdx, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEachIndexed { i, hz ->
                            val band = rowIdx * 3 + i
                            OutlinedTextField(
                                value = s.thresholds[ear][band],
                                onValueChange = { vm.setThreshold(ear, band, it) },
                                label = { Text(freq(hz)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(s.realEarVerified, vm::setRealEarVerified)
                Text("External real-ear measurement reviewed", fontSize = 13.sp)
            }
            OutlinedTextField(
                value = s.realEarNotes,
                onValueChange = vm::setRealEarNotes,
                label = { Text("Date, equipment, target, measured dB SPL") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Section("Speech clarity A/B log") {
            Text(
                "Read the same unseen word list in Processed and Baseline mode (toggle above), then log " +
                    "how many words were understood. Manual comparison, not a validated test.",
                color = Slate400, fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonitorLimits.ENVIRONMENTS.forEach { env ->
                    FilterChip(selected = s.environment == env, onClick = { vm.setEnvironment(env) }, label = { Text(env) })
                }
            }
            var correct by remember { mutableStateOf("") }
            var total by remember { mutableStateOf("10") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    correct, { correct = it.filter(Char::isDigit) }, label = { Text("Correct") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    total, { total = it.filter(Char::isDigit) }, label = { Text("List size") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { if (vm.saveTrial(correct, total)) correct = "" },
                Modifier.fillMaxWidth()
            ) { Text("Save ${if (s.processed) "processed" else "baseline"} score") }
            if (s.trialMessage.isNotEmpty()) Text(s.trialMessage, color = Amber500, fontSize = 12.sp)
            s.trials.take(10).forEach { Text(it, fontSize = 12.sp, color = Slate400) }
        }

        TextButton(onClick = vm::resetDefaults, Modifier.fillMaxWidth()) { Text("Reset to cautious defaults") }
    }
}

private fun freq(hz: Int) = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (open) "−" else "+", color = Cyan400, fontSize = 20.sp)
            }
            if (open) content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, fontSize = 13.sp, color = Slate400)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun DevicePicker(
    label: String,
    options: List<DeviceOption>,
    selectedId: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.id == selectedId }?.label ?: "Choose…"
    Column {
        Text(label, fontSize = 12.sp, color = Slate400)
        OutlinedButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(current, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o.label) }, onClick = { onSelect(o.id); open = false })
            }
        }
    }
}
