package com.echo.hearing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.hearing.data.UserProfileStore
import com.echo.hearing.domain.statemachine.SessionState
import com.echo.hearing.volume.AppNavigation
import com.echo.hearing.volume.VolumeMonitorScreen
import com.echo.hearing.volume.VolumeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echo.hearing.monitor.MonitorScreen
import com.echo.hearing.monitor.MonitorViewModel
import com.echo.hearing.ui.theme.*

private enum class EchoTab(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    TEST("Test", Icons.Filled.Hearing, Icons.Outlined.Hearing),
    HOME("Home", Icons.Filled.GridView, Icons.Outlined.GridView),
    MONITOR("Monitor", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    AID("Aid", Icons.Filled.Headphones, Icons.Outlined.Headphones),
    REPORTS("Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * Top-level shell: onboarding flow followed by the 6-tab ECHO workspace
 * (Test, Home, Monitor, Aid, Reports, Profile).
 * Screening and the hearing aid share the microphone and output route, so opening the
 * Test tab stops the hearing aid. Otherwise it keeps running in a background service.
 */
@Composable
fun EchoApp(
    screeningViewModel: HearingScreeningViewModel,
    monitorViewModel: MonitorViewModel,
    onRequestMicrophonePermission: () -> Unit
) {
    val context = LocalContext.current
    val profileStore = remember { UserProfileStore(context) }
    var profile by remember { mutableStateOf(profileStore.load()) }
    var showOnboarding by rememberSaveable { mutableStateOf(!profile.onboardingCompleted) }
    // Reopening the app while the hearing aid runs in the background lands on its tab.
    var tab by rememberSaveable { mutableStateOf(if (monitorViewModel.state.value.running) EchoTab.AID else EchoTab.TEST) }
    var openHistorySignal by remember { mutableIntStateOf(0) }
    var launchInitialScreeningSignal by remember { mutableIntStateOf(0) }
    var openAidSettingsOnce by remember { mutableStateOf(false) }

    val screeningState by screeningViewModel.uiState.collectAsState()
    val monitorState by monitorViewModel.state.collectAsState()
    // One shared source of live volume data for the Monitor, Reports, Home and Profile tabs.
    val volumeViewModel: VolumeViewModel = viewModel()
    val volumeState by volumeViewModel.state.collectAsState()
    val volumeTracking = volumeState.tracking

    // Notifications can ask for a tab (high-volume alert -> Monitor, hearing-test suggestion -> Test).
    val requestedTab by AppNavigation.requestedTab.collectAsState()
    LaunchedEffect(requestedTab) {
        when (requestedTab) {
            AppNavigation.TAB_TEST -> tab = EchoTab.TEST
            AppNavigation.TAB_MONITOR -> tab = EchoTab.MONITOR
            AppNavigation.TAB_AID -> tab = EchoTab.AID
        }
        if (requestedTab != null) AppNavigation.consumed()
    }
    // Screening and the hearing aid share the microphone: switching to the Test tab, by any route, stops the aid.
    var previousTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab == EchoTab.TEST && previousTab != EchoTab.TEST) {
            monitorViewModel.stop("Stopped so the screening can use the microphone")
        }
        previousTab = tab
    }
    var initialTestPending by rememberSaveable {
        mutableStateOf(!profile.initialScreeningCompleted && screeningState.savedSessions.isEmpty())
    }

    LaunchedEffect(screeningState.sessionState) {
        if (screeningState.sessionState == SessionState.RESULTS && !profile.initialScreeningCompleted) {
            val completedInitial = profile.copy(initialScreeningCompleted = true)
            profile = completedInitial
            profileStore.save(completedInitial)
            initialTestPending = false
        }
    }

    // Every new screening automatically fits the hearing aid (once per screening, so later manual
    // EQ adjustments are kept).
    val latestScreeningId = screeningState.savedSessions
        .filter { it.thresholdResults.isNotEmpty() }
        .maxByOrNull { it.startedAt }?.sessionId
    LaunchedEffect(latestScreeningId) {
        if (latestScreeningId != null) monitorViewModel.importLatestScreening(onlyIfNew = true)
    }


    if (showOnboarding) {
        val needsInitial = !profile.initialScreeningCompleted && screeningState.savedSessions.isEmpty()
        OnboardingFlow(
            profile = profile,
            needsInitialTest = needsInitial,
            onUpdateProfile = { updated ->
                profile = updated
                profileStore.save(updated)
            },
            onConsentAgreed = { screeningViewModel.onConsentAgreed(true) },
            onRequestMicrophonePermission = onRequestMicrophonePermission,
            onCompleteOnboarding = {
                val shouldRunInitial = !profile.initialScreeningCompleted && screeningState.savedSessions.isEmpty()
                val completed = profile.copy(
                    onboardingCompleted = true,
                    useSixFrequencies = if (shouldRunInitial) true else profile.useSixFrequencies
                )
                profile = completed
                profileStore.save(completed)
                showOnboarding = false
                tab = EchoTab.TEST
                if (shouldRunInitial) {
                    initialTestPending = true
                    screeningViewModel.setUseSixFrequencies(true)
                    launchInitialScreeningSignal++
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = EchoBg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column(modifier = Modifier.background(Color.White)) {
                HorizontalDivider(color = EchoBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    EchoTab.entries.forEach { t ->
                        val selected = tab == t
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                openAidSettingsOnce = false
                                tab = t
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) t.selectedIcon else t.unselectedIcon,
                                    contentDescription = t.label
                                )
                            },
                            label = {
                                Text(
                                    text = t.label,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EchoPrimary,
                                selectedTextColor = EchoPrimary,
                                unselectedIconColor = EchoTextMuted,
                                unselectedTextColor = EchoTextMuted,
                                indicatorColor = EchoPrimarySoft
                            )
                        )
                    }
                }
            }
        }
    ) { padding: PaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                EchoTab.TEST -> HearingScreeningApp(
                    viewModel = screeningViewModel,
                    profile = profile,
                    initialTestPending = initialTestPending,
                    launchInitialScreeningSignal = launchInitialScreeningSignal,
                    onUpdateProfile = { updated ->
                        profile = updated
                        profileStore.save(updated)
                    },
                    onRequestMicrophonePermission = onRequestMicrophonePermission,
                    onImportToMonitor = {
                        monitorViewModel.importLatestScreening()
                        openAidSettingsOnce = true
                        tab = EchoTab.AID
                    },
                    onSkipInitialTest = {
                        initialTestPending = false
                        tab = EchoTab.HOME
                    },
                    onNavigateToHome = { tab = EchoTab.HOME },
                    openHistorySignal = openHistorySignal
                )

                EchoTab.HOME -> HomeScreen(
                    profile = profile,
                    screeningState = screeningState,
                    monitorState = monitorState,
                    volumeTracking = volumeTracking,
                    initialTestPending = initialTestPending || (!profile.initialScreeningCompleted && screeningState.savedSessions.isEmpty()),
                    onStartInitialTest = {
                        tab = EchoTab.TEST
                        launchInitialScreeningSignal++
                    },
                    onNavigateToTest = { tab = EchoTab.TEST },
                    onNavigateToMonitor = { tab = EchoTab.MONITOR },
                    onNavigateToAid = { tab = EchoTab.AID },
                    onNavigateToReports = { tab = EchoTab.REPORTS },
                    onOpenScreeningHistory = {
                        openHistorySignal++
                        tab = EchoTab.TEST
                    }
                )

                EchoTab.MONITOR -> VolumeMonitorScreen(vm = volumeViewModel, onTakeHearingTest = { tab = EchoTab.TEST })

                EchoTab.AID -> MonitorScreen(vm = monitorViewModel, openSettingsInitially = openAidSettingsOnce)

                EchoTab.REPORTS -> ReportsScreen(
                    screeningState = screeningState,
                    volume = volumeState,
                    onOpenMonitor = { tab = EchoTab.MONITOR },
                    onOpenScreeningHistory = {
                        openHistorySignal++
                        tab = EchoTab.TEST
                    }
                )

                EchoTab.PROFILE -> ProfileScreen(
                    profile = profile,
                    onUpdateProfile = { updated ->
                        profile = updated
                        profileStore.save(updated)
                    },
                    screeningState = screeningState,
                    monitorState = monitorState,
                    volume = volumeState,
                    onRefreshDeviceRoute = { screeningViewModel.refreshDeviceRoute() },
                    onRestartOnboarding = {
                        monitorViewModel.stop("Stopped")
                        profileStore.resetOnboarding()
                        profile = profile.copy(onboardingCompleted = false)
                        showOnboarding = true
                    },
                    onSignOut = {
                        monitorViewModel.stop("Stopped")
                        profileStore.signOut()
                        profile = profile.copy(
                            onboardingCompleted = false,
                            initialScreeningCompleted = false,
                            accountMode = "Guest"
                        )
                        initialTestPending = true
                        showOnboarding = true
                    }
                )
            }
        }
    }
}
