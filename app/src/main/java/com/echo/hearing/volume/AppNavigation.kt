package com.echo.hearing.volume

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lets a notification open a specific tab. MainActivity feeds intents in; the app shell consumes them. */
object AppNavigation {
    const val EXTRA_TAB = "com.echo.hearing.OPEN_TAB"
    const val TAB_TEST = "TEST"
    const val TAB_MONITOR = "MONITOR"
    const val TAB_AID = "AID"

    private val _requestedTab = MutableStateFlow<String?>(null)
    val requestedTab: StateFlow<String?> = _requestedTab.asStateFlow()

    fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TAB)?.let { _requestedTab.value = it }
        intent?.removeExtra(EXTRA_TAB)
    }

    fun consumed() {
        _requestedTab.value = null
    }
}
