package gd.app.musicplayer.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import gd.app.musicplayer.playback.service.MusicPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class SleepTimerState(
    val status: Int = STATUS_IDLE,
    val durationMinutes: Int = 0,
    val endAtMs: Long = 0L,
    val remainingMs: Long = 0L,
    val stopAfterCurrentTrack: Boolean = false,
    val action: Int = ACTION_STOP_PLAYBACK
) {
    val isActive: Boolean
        get() = status != STATUS_IDLE

    val isPendingTrackEnd: Boolean
        get() = status == STATUS_PENDING_TRACK_END

    companion object {
        const val ACTION_STOP_PLAYBACK = 0
        const val ACTION_EXIT_PLAYER = 1

        const val STATUS_RUNNING = 0
        const val STATUS_PENDING_TRACK_END = 1
        const val STATUS_IDLE = 2
    }
}

object SleepTimerManager {
    private const val TICK_MS = 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    private var appContext: Context? = null
    private var ticker: Runnable? = null
    private var playbackActiveProvider: (() -> Boolean)? = null

    fun start(
        context: Context,
        durationMinutes: Int,
        action: Int,
        stopAfterCurrentTrack: Boolean
    ) {
        val clampedMinutes = durationMinutes.coerceAtLeast(1)
        val now = System.currentTimeMillis()
        val endAt = now + clampedMinutes * 60_000L
        appContext = context.applicationContext
        mutableState.value = SleepTimerState(
            status = SleepTimerState.STATUS_RUNNING,
            durationMinutes = clampedMinutes,
            endAtMs = endAt,
            remainingMs = clampedMinutes * 60_000L,
            stopAfterCurrentTrack = stopAfterCurrentTrack,
            action = action
        )
        SleepTimerFeedback.showScheduled(context, clampedMinutes * 60_000L)
        startTicker()
    }

    fun cancel() {
        stopTicker()
        appContext?.let { setStopAfterCurrentTrack(it, false) }
        mutableState.value = SleepTimerState()
        appContext?.let { SleepTimerFeedback.showScheduled(it, 0L) }
    }

    fun updateBehavior(
        action: Int,
        stopAfterCurrentTrack: Boolean
    ) {
        val current = mutableState.value

        if (!current.isActive) return

        if (
            current.status == SleepTimerState.STATUS_PENDING_TRACK_END &&
            current.stopAfterCurrentTrack &&
            !stopAfterCurrentTrack
        ) {
            appContext?.let { setStopAfterCurrentTrack(it, false) }
            mutableState.value = SleepTimerState()
            executeConfiguredAction(current.action)
            return
        }

        mutableState.value = current.copy(
            action = action,
            stopAfterCurrentTrack = stopAfterCurrentTrack
        )
    }

    fun finishPendingTrackEnd(executeAction: Boolean = true) {
        stopTicker()
        val current = mutableState.value
        mutableState.value = SleepTimerState()
        if (executeAction && current.status == SleepTimerState.STATUS_PENDING_TRACK_END) {
            executeConfiguredAction(current.action)
        }
    }

    fun setPlaybackActiveProvider(provider: (() -> Boolean)?) {
        playbackActiveProvider = provider
    }

    private fun startTicker() {
        stopTicker()
        ticker = object : Runnable {
            override fun run() {
                val current = mutableState.value
                if (!current.isActive) return

                val now = System.currentTimeMillis()
                val remaining = max(0L, current.endAtMs - now)
                if (remaining <= 0L) {
                    val nextState = fireAction(current)
                    mutableState.value = nextState
                    stopTicker()
                    return
                }

                mutableState.value = current.copy(remainingMs = remaining)
                mainHandler.postDelayed(this, TICK_MS)
            }
        }
        mainHandler.post(ticker!!)
    }

    private fun stopTicker() {
        ticker?.let(mainHandler::removeCallbacks)
        ticker = null
    }

    private fun fireAction(current: SleepTimerState): SleepTimerState {
        val context = appContext ?: return SleepTimerState()
        if (current.stopAfterCurrentTrack && playbackActiveProvider?.invoke() == true) {
            setStopAfterCurrentTrack(context, true)
            return current.copy(
                status = SleepTimerState.STATUS_PENDING_TRACK_END,
                remainingMs = 0L
            )
        }
        executeConfiguredAction(current.action)
        return SleepTimerState()
    }

    private fun executeConfiguredAction(timerAction: Int) {
        val context = appContext ?: return
        SleepTimerFeedback.showScheduled(context, 0L)
        when (timerAction) {
            SleepTimerState.ACTION_STOP_PLAYBACK,
            SleepTimerState.ACTION_EXIT_PLAYER -> {
                val intent = android.content.Intent(context, MusicPlaybackService::class.java).apply {
                    action = if (timerAction == SleepTimerState.ACTION_EXIT_PLAYER) {
                        MusicPlaybackService.ACTION_EXIT
                    } else {
                        MusicPlaybackService.ACTION_PAUSE
                    }
                }
                context.startService(intent)
            }
        }
    }

    private fun setStopAfterCurrentTrack(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        val intent = android.content.Intent(appContext, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_SET_STOP_AFTER_CURRENT_TRACK
            putExtra(MusicPlaybackService.EXTRA_STOP_AFTER_CURRENT_TRACK, enabled)
        }
        appContext.startService(intent)
    }
}
