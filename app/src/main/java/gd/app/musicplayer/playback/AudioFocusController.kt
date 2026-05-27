package gd.app.musicplayer.playback

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

class AudioFocusController(
    private val context: Context,
    private val isPlaying: () -> Boolean,
    private val pausePlayback: () -> Unit,
    private val resumePlayback: () -> Unit,
    private val isSimultaneousPlayEnabled: () -> Boolean
) {
    private var hasAudioFocus = false
    private var shouldResumeAfterTransientLoss = false
    private var transientLossAtMs = 0L

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (isSimultaneousPlayEnabled()) {
            return@OnAudioFocusChangeListener
        }

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                shouldResumeAfterTransientLoss = false
                transientLossAtMs = 0L
                pausePlayback()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                shouldResumeAfterTransientLoss = isPlaying()
                transientLossAtMs = if (shouldResumeAfterTransientLoss) SystemClock.elapsedRealtime() else 0L
                pausePlayback()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                val canResume = transientLossAtMs > 0L &&
                    SystemClock.elapsedRealtime() - transientLossAtMs <= AUDIO_FOCUS_AUTO_RESUME_WINDOW_MS

                if (shouldResumeAfterTransientLoss && canResume) {
                    shouldResumeAfterTransientLoss = false
                    transientLossAtMs = 0L
                    resumePlayback()
                } else {
                    shouldResumeAfterTransientLoss = false
                    transientLossAtMs = 0L
                }
            }
        }
    }

    fun request(): Boolean {
        if (isSimultaneousPlayEnabled()) {
            hasAudioFocus = false
            return true
        }

        val result = audioManager.requestAudioFocus(
            listener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    fun abandon() {
        if (!hasAudioFocus) return
        audioManager.abandonAudioFocus(listener)
        hasAudioFocus = false
    }

    companion object {
        private const val AUDIO_FOCUS_AUTO_RESUME_WINDOW_MS = 5 * 60 * 1000L
    }
}
