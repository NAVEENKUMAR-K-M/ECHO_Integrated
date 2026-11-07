package com.echo.hearing.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.hearing.domain.model.Ear
import com.echo.hearing.domain.model.NoiseAssessment
import com.echo.hearing.domain.model.ScreeningOutcomeClassification
import com.echo.hearing.domain.model.ThresholdResult
import com.echo.hearing.domain.statemachine.SessionState
import com.echo.hearing.domain.threshold.StrategyType
import com.echo.hearing.ui.components.AudiogramView
import com.echo.hearing.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearingScreeningApp(
    viewModel: HearingScreeningViewModel,
    onRequestMicrophonePermission: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSimPanel by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "ECHO",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Cyan400
                            )
                        )
                        Text(
                            "Hearing screening",
                            style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                        )
                    }
                },
                actions = {
                    FilterChip(
                        selected = showSimPanel,
                        onClick = {
                            showSimPanel = !showSimPanel
                        },
                        label = {
                            Text(
                                if (uiState.isSimulationMode) "SIMULATION ACTIVE" else "DEV SIM",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber500.copy(alpha = 0.2f),
                            selectedLabelColor = Amber500
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate950
                )
            )
        },
        containerColor = Slate950
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Main content switcher depending on SessionState
                when (uiState.sessionState) {
                    SessionState.NOT_STARTED, SessionState.ONBOARDING_CONSENT -> {
                        OnboardingScreen(
                            onConsentAgreed = { agreed ->
                                viewModel.onConsentAgreed(agreed)
                            }
                        )
                    }

                    SessionState.DEVICE_READINESS,
                    SessionState.CALIBRATION_GATE,
                    SessionState.ENVIRONMENTAL_NOISE_CHECK,
                    SessionState.TEST_PREP -> {
                        // Request microphone permission when first entering device readiness
                        LaunchedEffect(uiState.sessionState) {
                            if (uiState.sessionState == SessionState.DEVICE_READINESS) {
                                onRequestMicrophonePermission()
                            }
                        }
                        PrecheckScreen(
                            uiState = uiState,
                            onRefreshDevice = { viewModel.refreshDeviceRoute() },
                            onContinue = { viewModel.onDevicePrecheckContinue() },
                            onSelectStrategy = { viewModel.selectStrategy(it) },
                            onStartPractice = { viewModel.onStartPractice() },
                            onStartScreening = { viewModel.onStartScreening() }
                        )
                    }

                    SessionState.PRACTICE_PRESENTATION,
                    SessionState.PRACTICE_FEEDBACK -> {
                        PracticeScreen(
                            uiState = uiState,
                            onPracticeHeardClicked = { viewModel.onPracticeHeardClicked() },
                            onPracticeNotHeardClicked = { viewModel.onPracticeNotHeardClicked() },
                            onTryAgain = { viewModel.onStartPractice() },
                            onContinueToScreening = { viewModel.onStartScreening() }
                        )
                    }

                    SessionState.STIMULUS_PRESENTATION,
                    SessionState.WAITING_FOR_RESPONSE,
                    SessionState.TRIAL_VALIDATION,
                    SessionState.AUTOMATIC_PAUSE_NOISE,
                    SessionState.AUTOMATIC_PAUSE_DEVICE,
                    SessionState.RECOVERY_RECHECK,
                    SessionState.THRESHOLD_ESTIMATION -> {
                        ScreeningActiveScreen(
                            uiState = uiState,
                            onUserHeardClicked = { viewModel.onUserHeardClicked() },
                            onUserNotHeardClicked = { viewModel.onUserNotHeardClicked() },
                            onPauseClicked = { viewModel.onPauseRequested() },
                            onResumeClicked = { viewModel.onResumeRequested() },
                            onCancelClicked = { viewModel.onCancelRequested() }
                        )
                    }

                    SessionState.RESULTS, SessionState.COMPLETED -> {
                        ResultsScreen(
                            uiState = uiState,
                            onRestartClicked = { viewModel.onRestartRequested() }
                        )
                    }

                    SessionState.CANCELLED, SessionState.ABORTED -> {
                        AbortedScreen(
                            uiState = uiState,
                            onTryAgain = { viewModel.onTryAgain() },
                            onResetConsent = { viewModel.onRestartRequested() }
                        )
                    }
                }
            }

            // Developer Simulation Bottom Drawer
            if (showSimPanel) {
                SimulationDrawer(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isSimulationActive = uiState.isSimulationMode,
                    onToggleSimulationMode = { viewModel.toggleSimulationMode() },
                    onClose = { showSimPanel = false },
                    onInjectNoise = { assessment -> viewModel.simulateNoiseSpike(assessment) },
                    onDisconnectDevice = { viewModel.simulateDeviceDisconnect() },
                    onSimulateHeard = { viewModel.onUserHeardClicked() }
                )
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    onConsentAgreed: (Boolean) -> Unit
) {
    var consentChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Welcome Header
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = Sky500,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Welcome to ECHO",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                )
                Text(
                    "Adaptive Pure-Tone Hearing Screening POC",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Slate400)
                )
            }
        }

        // Intended Use & Clinical Disclaimer Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke(Amber500.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Amber500)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Preliminary Screening Only",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Amber500
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "• ECHO is a self-administered research proof of concept, NOT a certified medical device.\n" +
                            "• Output levels are digital attenuation steps (dBFS), not calibrated dB HL.\n" +
                            "• This test does NOT replace comprehensive diagnostic audiometry performed by an audiologist or ENT doctor.\n" +
                            "• If you suspect acute hearing loss, ear pain, or sudden tinnitus, seek immediate medical care.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate100, lineHeight = 20.sp)
                )
            }
        }

        // What to Expect
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "What to Expect (5–8 Minutes)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Sky500
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "1. Connect wired or stereo Bluetooth headphones.\n" +
                            "2. Find a quiet room; active noise monitoring will pause the test if ambient sound is detected.\n" +
                            "3. Optional quick practice tone to verify volume and response button.\n" +
                            "4. Tones will be presented across low, mid, and high frequencies separately in left and right ears.\n" +
                            "5. Press 'I Hear It' as soon as you detect a tone, even if very faint.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400, lineHeight = 20.sp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Consent Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = consentChecked,
                onCheckedChange = { consentChecked = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = Sky500,
                    uncheckedColor = Slate400
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "I understand this is a non-diagnostic proof of concept using digital dBFS attenuation steps.",
                style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
            )
        }

        // Action Buttons
        Button(
            onClick = { onConsentAgreed(true) },
            enabled = consentChecked,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sky500)
        ) {
            Text("Agree & Continue", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PrecheckScreen(
    uiState: HearingScreeningUiState,
    onRefreshDevice: () -> Unit,
    onContinue: () -> Unit,
    onSelectStrategy: (StrategyType) -> Unit,
    onStartPractice: () -> Unit,
    onStartScreening: () -> Unit
) {
    val route = uiState.audioRoute
    val isHeadphone = uiState.isHeadphoneConnected
    val noise = uiState.noiseReading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Device & Screening Setup",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Slate100
            )
        )

        // Algorithm Strategy Selection Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Threshold Estimation Algorithm",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = Slate100)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.strategyType == StrategyType.MODIFIED_HUGHSON_WESTLAKE,
                        onClick = { onSelectStrategy(StrategyType.MODIFIED_HUGHSON_WESTLAKE) },
                        label = { Text("Hughson–Westlake (Clinical)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Sky500.copy(alpha = 0.25f),
                            selectedLabelColor = Sky500
                        )
                    )
                    FilterChip(
                        selected = uiState.strategyType == StrategyType.BINARY_SEARCH_REFINEMENT,
                        onClick = { onSelectStrategy(StrategyType.BINARY_SEARCH_REFINEMENT) },
                        label = { Text("Binary Search (Exp)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber500.copy(alpha = 0.25f),
                            selectedLabelColor = Amber500
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (uiState.strategyType == StrategyType.MODIFIED_HUGHSON_WESTLAKE) {
                        "Modified Hughson-Westlake: Down-10/Up-5 staircase requiring 2 ascending responses at threshold."
                    } else {
                        "Binary Search Refinement: Bracket interval halving with discrete grid snapping. Faster convergence."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400, fontSize = 11.sp)
                )
            }
        }

        // Precheck Warning / Incomplete Reason
        uiState.pauseReason?.let { reason ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = borderStroke(Rose500),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall.copy(color = Slate200)
                    )
                }
            }
        }

        // Audio Route Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio Output Route (Playback)", fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onRefreshDevice) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Route", tint = Sky500)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isHeadphone) Icons.Default.Headphones else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (isHeadphone) Emerald500 else Rose500,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Output: ${route?.deviceName ?: "Checking audio route..."}",
                            fontWeight = FontWeight.Medium,
                            color = Slate100
                        )
                        Text(
                            if (isHeadphone) "Supported Headphone Route (${route?.routeType?.displayName ?: "Headphones"})"
                            else "WARNING: Speaker active. Headphones required.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isHeadphone) Emerald500 else Rose500
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Calibration: Digital dBFS (uncalibrated consumer hardware)",
                            style = MaterialTheme.typography.bodySmall.copy(color = Slate400, fontSize = 11.sp)
                        )
                    }
                }
            }
        }

        // Noise Sensor Check Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ambient Noise Monitor (Environment)", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (color, label) = when (noise.assessment) {
                        NoiseAssessment.VALID -> Emerald500 to "Quiet Environment (Ready)"
                        NoiseAssessment.SUSPECT -> Amber500 to "Moderate Noise (Relocate if possible)"
                        NoiseAssessment.INVALID -> Rose500 to "Too Loud (Testing will pause)"
                        NoiseAssessment.UNAVAILABLE -> Slate400 to "Mic Permission Pending / Unavailable"
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = color))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (noise.assessment == NoiseAssessment.UNAVAILABLE) {
                        "Noise Level: Unavailable (Microphone permission or sample required)"
                    } else {
                        "Noise Level: ${String.format("%.1f", noise.levelDbFs)} dBFS (RMS)"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Input Sensor: Built-in Phone Microphone (transient room sensing)",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400, fontSize = 11.sp)
                )
            }
        }

        // Practice Round Card (Available in TEST_PREP)
        if (uiState.sessionState == SessionState.TEST_PREP) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(16.dp),
                border = borderStroke(Sky500.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Familiarization Practice Round",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Sky500)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Try a sample 1000 Hz tone to confirm your volume and test the response button before screening.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onStartPractice,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Try Practice Tone")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (uiState.sessionState == SessionState.DEVICE_READINESS) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sky500)
            ) {
                Text("Validate Environment & Continue", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStartScreening,
                enabled = isHeadphone && noise.assessment == NoiseAssessment.VALID,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
            ) {
                Text("Start Formal Screening", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PracticeScreen(
    uiState: HearingScreeningUiState,
    onPracticeHeardClicked: () -> Unit,
    onPracticeNotHeardClicked: () -> Unit,
    onTryAgain: () -> Unit,
    onContinueToScreening: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Practice Trial",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Cyan400)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "1000 Hz Demo Tone (Left Ear)",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Slate400)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Tone Indicator / Response Buttons
                if (uiState.sessionState == SessionState.PRACTICE_PRESENTATION) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(if (uiState.practiceTonePlaying) Sky500.copy(alpha = 0.25f) else Slate800)
                                .border(3.dp, if (uiState.practiceTonePlaying) Sky500 else Slate700, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Hearing,
                                contentDescription = if (uiState.practiceTonePlaying) "Tone Active" else "Listening",
                                tint = if (uiState.practiceTonePlaying) Sky500 else Slate400,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Text(
                            if (uiState.practiceTonePlaying) "DEMO TONE PLAYING..." else "RESPOND WHEN READY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (uiState.practiceTonePlaying) Sky500 else Slate400,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onPracticeHeardClicked,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan400)
                            ) {
                                Text("I HEAR IT", fontWeight = FontWeight.Bold, color = Slate950)
                            }
                            OutlinedButton(
                                onClick = onPracticeNotHeardClicked,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = borderStroke(Rose500.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose500)
                            ) {
                                Text("I CAN'T HEAR IT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Practice Feedback
                    Icon(
                        imageVector = if (uiState.practiceHeard == true) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (uiState.practiceHeard == true) Emerald500 else Amber500,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        uiState.practiceFeedbackMessage ?: "Practice tone completed.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Slate100,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.sessionState == SessionState.PRACTICE_FEEDBACK) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTryAgain,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Try Again")
                        }
                        Button(
                            onClick = onContinueToScreening,
                            enabled = uiState.practiceHeard == true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald500,
                                disabledContainerColor = Slate800,
                                disabledContentColor = Slate600
                            )
                        ) {
                            Text("Start Test", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreeningActiveScreen(
    uiState: HearingScreeningUiState,
    onUserHeardClicked: () -> Unit,
    onUserNotHeardClicked: () -> Unit,
    onPauseClicked: () -> Unit,
    onResumeClicked: () -> Unit,
    onCancelClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Target Header
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            uiState.currentEar.displayName.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.currentEar == Ear.LEFT) Cyan400 else Rose500
                            )
                        )
                        Text(
                            "${uiState.currentFrequencyHz} Hz Tone",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )
                        )
                    }

                    // Algorithm Badge
                    Badge(
                        containerColor = if (uiState.strategyType == StrategyType.MODIFIED_HUGHSON_WESTLAKE) Sky500.copy(alpha = 0.2f) else Amber500.copy(alpha = 0.2f)
                    ) {
                        Text(
                            if (uiState.strategyType == StrategyType.MODIFIED_HUGHSON_WESTLAKE) "Hughson-Westlake" else "Binary Search",
                            color = if (uiState.strategyType == StrategyType.MODIFIED_HUGHSON_WESTLAKE) Sky500 else Amber500,
                            modifier = Modifier.padding(4.dp),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (uiState.completedTargets.toFloat() / uiState.totalTargets.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Cyan400,
                    trackColor = Slate800
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Target ${uiState.completedTargets + 1} of ${uiState.totalTargets}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                )
            }
        }

        // Center Interaction Sphere / Tone State / Response Controls
        if (uiState.isNoisePaused) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(16.dp),
                border = borderStroke(Amber500),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Screening Paused",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Amber500)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        uiState.pauseReason ?: "Noise disturbance detected. Resuming when quiet...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Slate300, textAlign = TextAlign.Center)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Auto-resuming in ${uiState.recoveryCountdownSeconds}s of quiet...",
                        style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Sphere indicator
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isTonePlaying) Cyan400.copy(alpha = 0.15f) else Slate900)
                        .border(
                            3.dp,
                            if (uiState.isTonePlaying) Cyan400 else Slate800,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isTonePlaying) Icons.Default.Hearing else Icons.Default.TouchApp,
                        contentDescription = if (uiState.isTonePlaying) "Tone playing" else "Ready for response",
                        tint = if (uiState.isTonePlaying) Cyan400 else if (uiState.isWaitingForResponse) Sky500 else Slate600,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Text(
                    text = when {
                        uiState.isTonePlaying -> "PLAYING TONE STIMULUS..."
                        uiState.isWaitingForResponse -> "DID YOU HEAR THE TONE?"
                        else -> "PREPARING NEXT TRIAL..."
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (uiState.isTonePlaying) Cyan400 else if (uiState.isWaitingForResponse) Sky500 else Slate500,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                // Dual response controls: I HEAR IT & I CAN'T HEAR IT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onUserHeardClicked,
                        enabled = uiState.isWaitingForResponse,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan400,
                            disabledContainerColor = Slate800,
                            disabledContentColor = Slate600
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "I HEAR IT",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.isWaitingForResponse) Slate950 else Slate600,
                                fontSize = 15.sp
                            )
                            Text(
                                "Tone detected",
                                fontSize = 10.sp,
                                color = if (uiState.isWaitingForResponse) Slate900.copy(alpha = 0.8f) else Slate600
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onUserNotHeardClicked,
                        enabled = uiState.isWaitingForResponse,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = borderStroke(if (uiState.isWaitingForResponse) Rose500.copy(alpha = 0.8f) else Slate800),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Rose500,
                            disabledContentColor = Slate600
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "I CAN'T HEAR IT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (uiState.isWaitingForResponse) Rose500 else Slate600
                            )
                            Text(
                                "No tone heard",
                                fontSize = 10.sp,
                                color = if (uiState.isWaitingForResponse) Rose500.copy(alpha = 0.7f) else Slate600
                            )
                        }
                    }
                }
            }
        }

        // Live Environmental Noise Status Bar
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = when (uiState.noiseReading.assessment) {
                        NoiseAssessment.VALID -> Emerald500
                        NoiseAssessment.SUSPECT -> Amber500
                        NoiseAssessment.INVALID -> Rose500
                        NoiseAssessment.UNAVAILABLE -> Slate400
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Ambient: ${String.format("%.1f", uiState.noiseReading.levelDbFs)} dBFS",
                        style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                    )
                }
                Text(
                    uiState.noiseReading.assessment.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (uiState.noiseReading.assessment == NoiseAssessment.VALID) Emerald500 else Amber500,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Bottom Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = onPauseClicked,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Pause")
            }
            OutlinedButton(
                onClick = onCancelClicked,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose500)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun ResultsScreen(
    uiState: HearingScreeningUiState,
    onRestartClicked: () -> Unit
) {
    val outcome = uiState.screeningOutcome

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Screening Results",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Slate100
            )
        )

        // Overall Outcome Classification Banner
        val (bannerColor, bannerTitle, bannerDesc) = when (outcome?.overallClassification) {
            ScreeningOutcomeClassification.PASS -> Triple(
                Emerald500,
                "PRELIMINARY SCREENING PASS",
                "Responses recorded within provisional screening levels across tested frequencies in both ears."
            )
            ScreeningOutcomeClassification.REFER -> Triple(
                Amber500,
                "REFERRAL RECOMMENDED",
                "Elevated response levels or non-responses detected. Formal diagnostic audiometry recommended."
            )
            else -> Triple(
                Slate400,
                "SCREENING INCONCLUSIVE",
                "Incomplete target completion or test interruptions occurred during screening."
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke(bannerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    bannerTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = bannerColor)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    bannerDesc,
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate300, lineHeight = 18.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Algorithm: ${uiState.strategyType.displayName}",
                    style = MaterialTheme.typography.labelSmall.copy(color = Slate400)
                )
            }
        }

        // Audiogram Canvas Chart
        AudiogramView(
            thresholdResults = uiState.thresholdResults,
            modifier = Modifier.fillMaxWidth()
        )

        // Separate Ear Summary Cards (Left & Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Ear Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "LEFT EAR (AS)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        outcome?.leftEarSummary?.classification?.displayName ?: "Complete",
                        fontWeight = FontWeight.SemiBold,
                        color = Slate100,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val leftThresholds = uiState.thresholdResults.filter { it.ear == Ear.LEFT }
                    leftThresholds.forEach { t ->
                        Text(
                            "${t.frequencyHz}Hz: ${t.thresholdDb} dBFS",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
            }

            // Right Ear Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "RIGHT EAR (AD)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        outcome?.rightEarSummary?.classification?.displayName ?: "Complete",
                        fontWeight = FontWeight.SemiBold,
                        color = Slate100,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val rightThresholds = uiState.thresholdResults.filter { it.ear == Ear.RIGHT }
                    rightThresholds.forEach { t ->
                        Text(
                            "${t.frequencyHz}Hz: ${t.thresholdDb} dBFS",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
            }
        }

        // Quality and Noise Statistics Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Quality & Test Reliability Metrics", fontWeight = FontWeight.SemiBold, color = Slate100, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Total trials presented: ${uiState.trials.size}\n" +
                            "Contaminated trials retried: ${uiState.trials.count { it.outcome == com.echo.hearing.domain.model.TrialOutcome.CONTAMINATED_BY_NOISE }}\n" +
                            "Environmental pauses triggered: ${uiState.noisePauseCount}\n" +
                            "Quality flags: ${uiState.qualityFlags.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400, lineHeight = 20.sp)
                )
            }
        }

        // Audiology Referral & Clinical Disclaimer
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke(Amber500.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Amber500)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Clinical Limitations & Guidance",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Amber500
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• These numbers represent software digital attenuation steps (dBFS), NOT clinical Hearing Level (dB HL).\n" +
                            "• A normal screening does not guarantee absence of hearing loss, and elevated thresholds may occur due to headphone fit, ambient noise, or volume limits.\n" +
                            "• If you have concerns about hearing, tinnitus, or ear discomfort, see a licensed audiologist or physician.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate100, lineHeight = 19.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRestartClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sky500)
        ) {
            Text("Restart Screening", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AbortedScreen(
    uiState: HearingScreeningUiState,
    onTryAgain: () -> Unit,
    onResetConsent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cancel,
            contentDescription = null,
            tint = Rose500,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (uiState.sessionState == SessionState.CANCELLED) "Screening Cancelled" else "Precheck Incomplete",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Slate100
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            uiState.cancelReason
                ?: uiState.pauseReason
                ?: "The screening session was cancelled or could not proceed because device or noise requirements were not met.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Slate400,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onTryAgain,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sky500)
        ) {
            Text("Try Again (Return to Setup)", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onResetConsent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restart from Beginning (Re-read Terms)", color = Slate400, fontSize = 12.sp)
        }
    }
}

@Composable
fun SimulationDrawer(
    modifier: Modifier = Modifier,
    isSimulationActive: Boolean,
    onToggleSimulationMode: () -> Unit,
    onClose: () -> Unit,
    onInjectNoise: (NoiseAssessment) -> Unit,
    onDisconnectDevice: () -> Unit,
    onSimulateHeard: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(16.dp),
        border = borderStroke(if (isSimulationActive) Amber500 else Slate700)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Developer Test Harness",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSimulationActive) Amber500 else Slate300
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSimulationActive) "[ACTIVE]" else "[OFF]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSimulationActive) Amber500 else Slate500
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Simulation mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Override Physical Hardware with Synthetic Data",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400, fontSize = 11.sp),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isSimulationActive,
                    onCheckedChange = { onToggleSimulationMode() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onInjectNoise(NoiseAssessment.INVALID) },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                    enabled = isSimulationActive,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Inject Noise", fontSize = 11.sp)
                }
                Button(
                    onClick = { onInjectNoise(NoiseAssessment.VALID) },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    enabled = isSimulationActive,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Noise", fontSize = 11.sp)
                }
                Button(
                    onClick = onDisconnectDevice,
                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                    enabled = isSimulationActive,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Unplug", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun borderStroke(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
