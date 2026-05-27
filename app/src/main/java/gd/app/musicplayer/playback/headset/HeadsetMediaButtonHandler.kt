package gd.app.musicplayer.playback.headset
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Singleton
class HeadsetMediaButtonHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackController: PlaybackController,
    settingPreferencesDataStore: SettingPreferencesDataStore,
    @ApplicationScope applicationScope: CoroutineScope
) {

    private val handler = Handler(Looper.getMainLooper())

    private var clickCount: Int = 0

    @Volatile
    private var headsetControlAllowed: Boolean = true

    private val flushRunnable = Runnable {
        flushClicks()
    }

    init {
        settingPreferencesDataStore.observeSettingPreferences()
            .map { preferences ->
                preferences.headset.headsetControlAllowed
            }
            .distinctUntilChanged()
            .onEach { allowed ->
                headsetControlAllowed = allowed
            }
            .launchIn(applicationScope)
    }

    fun handle(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playbackController.play()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                playbackController.pause()
                true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (headsetControlAllowed) {
                    playbackController.playNext()
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (headsetControlAllowed) {
                    playbackController.playPrevious()
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                playbackController.togglePlayPause()
                true
            }

            KeyEvent.KEYCODE_HEADSETHOOK -> {
                clickCount += 1
                handler.removeCallbacks(flushRunnable)
                handler.postDelayed(flushRunnable, MULTI_CLICK_WINDOW_MS)
                true
            }

            else -> false
        }
    }

    fun clearPendingClicks() {
        handler.removeCallbacks(flushRunnable)
        clickCount = 0
    }

    private fun flushClicks() {
        val count = clickCount
        clickCount = 0

        when {
            count >= 3 && headsetControlAllowed -> {
                playbackController.playPrevious()
            }

            count == 2 && headsetControlAllowed -> {
                playbackController.playNext()
            }

            count >= 1 -> {
                playbackController.togglePlayPause()
            }
        }
    }

    private companion object {
        const val MULTI_CLICK_WINDOW_MS = 350L
    }
}
