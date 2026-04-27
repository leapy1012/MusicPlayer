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
                MusicPlaybackController.playPrevious(context)
            clickCount == 2 && preferences.isHeadsetControlAllowed() ->
                MusicPlaybackController.playNext(context)
            clickCount >= 1 ->
                MusicPlaybackController.togglePlayPause(context)
        }
        clickCount = 0
        pendingContext = null
    }

    fun handle(context: Context, keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                MusicPlaybackController.play(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                MusicPlaybackController.pause(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                MusicPlaybackController.playNext(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                MusicPlaybackController.playPrevious(context)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                MusicPlaybackController.togglePlayPause(context)
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
