package gd.app.musicplayer.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class SleepTimerState(
    val isActive: Boolean = false,
    val durationMinutes: Int = 0,
    val endAtMs: Long = 0L,
    val remainingMs: Long = 0L,
    val stopAfterCurrentTrack: Boolean = false,
    val action: Int = ACTION_STOP_PLAYBACK
) {
    companion object {
        const val ACTION_STOP_PLAYBACK = 0
        const val ACTION_EXIT_PLAYER = 1
    }
}

object SleepTimerManager {
    private const val TICK_MS = 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    private var appContext: Context? = null
    private var ticker: Runnable? = null

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
            isActive = true,
            durationMinutes = clampedMinutes,
            endAtMs = endAt,
            remainingMs = clampedMinutes * 60_000L,
            stopAfterCurrentTrack = stopAfterCurrentTrack,
            action = action
        )
        startTicker()
    }

    fun cancel() {
        stopTicker()
        appContext?.let { setStopAfterCurrentTrack(it, false) }
        mutableState.value = SleepTimerState()
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
                    fireAction(current)
                    mutableState.value = SleepTimerState()
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

    private fun fireAction(current: SleepTimerState) {
        val context = appContext ?: return
        if (current.stopAfterCurrentTrack) {
            setStopAfterCurrentTrack(context, true)
            return
        }
        when (current.action) {
            SleepTimerState.ACTION_STOP_PLAYBACK,
            SleepTimerState.ACTION_EXIT_PLAYER -> {
                val intent = android.content.Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_STOP
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
