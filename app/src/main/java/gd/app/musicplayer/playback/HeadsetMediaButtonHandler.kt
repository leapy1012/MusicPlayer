package gd.app.musicplayer.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import gd.app.musicplayer.util.PreferenceUtil

object HeadsetMediaButtonHandler {
    private const val MULTI_CLICK_WINDOW_MS = 350L

    private val handler = Handler(Looper.getMainLooper())
    private var clickCount = 0
    private var pendingContext: Context? = null

    private val flushRunnable = Runnable {
        val context = pendingContext?.applicationContext ?: return@Runnable
        val preferences = PreferenceUtil.getInstance(context)
        when {
            clickCount >= 3 && preferences.isHeadsetControlAllowed() ->
                PlaybackGateway.playPrevious(context)
            clickCount == 2 && preferences.isHeadsetControlAllowed() ->
                PlaybackGateway.playNext(context)
            clickCount >= 1 ->
                PlaybackGateway.togglePlayPause(context)
        }
        clickCount = 0
        pendingContext = null
    }

    fun handle(context: Context, keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                PlaybackGateway.play(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                PlaybackGateway.pause(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                PlaybackGateway.playNext(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                PlaybackGateway.playPrevious(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                PlaybackGateway.togglePlayPause(context)
                true
            }
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                pendingContext = context.applicationContext
                clickCount += 1
                handler.removeCallbacks(flushRunnable)
                handler.postDelayed(flushRunnable, MULTI_CLICK_WINDOW_MS)
                true
            }
            else -> false
        }
    }
}

