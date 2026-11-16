package com.echo.hearing.volume

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VolumeUiState(
    val tracking: Boolean = false,
    val volume: PhoneVolume = PhoneVolume(),
    val playing: Boolean = false,
    val output: AudioOutput = AudioOutput.SPEAKER,
    val today: VolumeSummary = VolumeSummary(0, 0),
    /** Last 7 days, oldest first, ending today. */
    val week: List<VolumeSummary> = emptyList(),
    /** Last 30 days, oldest first, ending today. */
    val month: List<VolumeSummary> = emptyList(),
    /** Listening time per volume band over the last 30 days. */
    val monthBands: Map<VolumeAnalytics.Band, Long> = emptyMap(),
    /** Today's segments, for the timeline. */
    val todaySegments: List<VolumeSegment> = emptyList(),
    val sessions: List<ListeningSession> = emptyList(),
    val firstRecordAt: Long? = null,
    val drift: DriftStatus = DriftStatus.NotEnoughData(0),
    val loaded: Boolean = false
)

class VolumeViewModel(private val app: Application) : AndroidViewModel(app) {

    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _state = MutableStateFlow(VolumeUiState(tracking = VolumeTrackerService.running.value))
    val state: StateFlow<VolumeUiState> = _state.asStateFlow()

    private val watcher = PhoneVolumeWatcher(app) { readLive() }

    init {
        watcher.start()
        readLive()
        // Resume tracking if it was on but the service is not running (e.g. after a force stop).
        if (VolumeLog.isTrackingEnabled(app) && !VolumeTrackerService.running.value) {
            runCatching { VolumeTrackerService.start(app) }
        }
        viewModelScope.launch {
            VolumeTrackerService.running.collect { r -> _state.update { it.copy(tracking = r) } }
        }
        viewModelScope.launch {
            // Recompute analytics when the log changes, at most every few seconds.
            VolumeLog.revision.collectLatest {
                refresh()
                delay(REFRESH_THROTTLE_MS)
            }
        }
        viewModelScope.launch {
            // Playback state and output have no reliable change callback here; poll while the screen is alive.
            while (true) {
                delay(LIVE_POLL_MS)
                readLive()
            }
        }
    }

    override fun onCleared() {
        watcher.stop()
        super.onCleared()
    }

    fun startTracking() = VolumeTrackerService.start(app)

    fun stopTracking() = VolumeTrackerService.stop(app)

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { VolumeLog.clear(app) }
            refresh()
        }
    }

    private fun readLive() {
        _state.update {
            it.copy(
                volume = PhoneAudio.volume(audioManager),
                playing = PhoneAudio.isPlaying(audioManager),
                output = PhoneAudio.output(audioManager)
            )
        }
    }

    private suspend fun refresh() {
        val now = System.currentTimeMillis()
        val segments = withContext(Dispatchers.IO) { VolumeLog.readAll(app) }
        val update: (VolumeUiState) -> VolumeUiState = withContext(Dispatchers.Default) {
            val todayStart = VolumeAnalytics.dayStart(now)
            val month = VolumeAnalytics.days(segments, now, MONTH_DAYS)
            val week = month.takeLast(7)
            val today = week.last()
            val todaySegments = segments.filter { it.end > todayStart }
            val sessions = VolumeAnalytics.sessions(segments).take(MAX_SESSIONS)
            val bands = VolumeAnalytics.listeningByBand(segments, month.first().from, now)
            val drift = VolumeDrift.evaluate(segments, now)
            val first = segments.firstOrNull()?.start
            val result: (VolumeUiState) -> VolumeUiState = {
                it.copy(
                    today = today, week = week, month = month, monthBands = bands,
                    todaySegments = todaySegments, sessions = sessions,
                    firstRecordAt = first, drift = drift, loaded = true
                )
            }
            result
        }
        _state.update(update)
    }

    private companion object {
        const val REFRESH_THROTTLE_MS = 5_000L
        const val LIVE_POLL_MS = 3_000L
        const val MAX_SESSIONS = 30
        const val MONTH_DAYS = 30
    }
}
