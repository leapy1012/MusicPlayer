package gd.app.musicplayer.playback.progress

import android.os.Handler
import android.os.Looper

class PlaybackProgressTicker(
    private val tickIntervalMs: Long,
    private val callbacks: Callbacks,
    val handler: Handler = Handler(Looper.getMainLooper())
) {

    interface Callbacks {
        fun onProgressTick()
    }

    private val tickerRunnable = object : Runnable {
        override fun run() {
            callbacks.onProgressTick()
            handler.postDelayed(this, tickIntervalMs)
        }
    }

    fun start() {
        handler.removeCallbacks(tickerRunnable)
        handler.post(tickerRunnable)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
    }

    fun stopTickerOnly() {
        handler.removeCallbacks(tickerRunnable)
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
    }

    fun postDelayed(
        block: Runnable,
        delayMs: Long
    ) {
        handler.postDelayed(block, delayMs)
    }
}