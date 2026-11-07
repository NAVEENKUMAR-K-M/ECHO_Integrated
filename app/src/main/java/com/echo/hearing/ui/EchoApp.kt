package com.echo.hearing.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.echo.hearing.monitor.MonitorScreen
import com.echo.hearing.monitor.MonitorViewModel
import com.echo.hearing.ui.theme.Cyan400
import com.echo.hearing.ui.theme.Slate400
import com.echo.hearing.ui.theme.Slate950

private enum class Tab(val label: String) { SCREENING("Screening"), MONITOR("Live monitor") }

/**
 * Top-level shell: two tabs. Screening and live monitoring both use the microphone and the
 * output route, so leaving the Monitor tab (or the app) always stops the live loop.
 */
@Composable
fun EchoApp(
    screeningViewModel: HearingScreeningViewModel,
    monitorViewModel: MonitorViewModel,
    onRequestMicrophonePermission: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(Tab.SCREENING) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) monitorViewModel.stop("Stopped when the app left the foreground")
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Slate950,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Slate950) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (t != Tab.MONITOR) monitorViewModel.stop("Stopped")
                            tab = t
                        },
                        icon = {
                            Icon(
                                if (t == Tab.SCREENING) Icons.Filled.Hearing else Icons.Filled.GraphicEq,
                                contentDescription = null
                            )
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan400,
                            selectedTextColor = Cyan400,
                            unselectedIconColor = Slate400,
                            unselectedTextColor = Slate400,
                            indicatorColor = Slate950
                        )
                    )
                }
            }
        }
    ) { padding: PaddingValues ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.SCREENING -> HearingScreeningApp(
                    viewModel = screeningViewModel,
                    onRequestMicrophonePermission = onRequestMicrophonePermission
                )
                Tab.MONITOR -> MonitorScreen(monitorViewModel, Modifier.statusBarsPadding())
            }
        }
    }
}
