package gd.app.musicplayer.ui.editor

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import gd.app.musicplayer.domain.model.Music
import kotlin.math.max
import kotlin.math.min

class SoundPreviewPlayer(
    private val music: Music,
    private val dataSourceResolver: MediaPlayer.(Music) -> Unit
) : MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener {

    interface Listener {
        fun onPlayingChanged(isPlaying: Boolean)
        fun onProgressChanged(positionMs: Int)
    }

    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var listener: Listener? = null

    private var clipStartMs: Int = 0
    private var clipEndMs: Int = 0

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mediaPlayer = player ?: return

            if (!mediaPlayer.isPlaying) return

            var position = mediaPlayer.currentPosition

            if (position >= clipEndMs) {
                position = clipEndMs
                pause()
                seekTo(clipStartMs)
            } else {
                handler.postDelayed(this, PROGRESS_DELAY_MS)
            }

            listener?.onProgressChanged(position)
        }
    }

    init {
        createPlayer()
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun isReady(): Boolean {
        return player != null
    }

    fun isPlaying(): Boolean {
        return runCatching { player?.isPlaying == true }.getOrElse {
            releaseOnError()
            false
        }
    }

    fun duration(): Int {
        return runCatching { player?.duration ?: 0 }.getOrElse {
            releaseOnError()
            0
        }
    }

    fun currentPosition(): Int {
        return runCatching { player?.currentPosition ?: -1 }.getOrElse {
            releaseOnError()
            -1
        }
    }

    fun setClipStart(startMs: Int) {
        if (clipStartMs == startMs) return

        clipStartMs = startMs
        seekTo(startMs)
        listener?.onProgressChanged(startMs)
    }

    fun setClipEnd(endMs: Int) {
        clipEndMs = endMs
    }

    fun seekTo(positionMs: Int) {
        runCatching {
            player?.seekTo(positionMs)
        }.onFailure {
            releaseOnError()
        }
    }

    fun playFrom(positionMs: Int) {
        runCatching {
            val mediaPlayer = player ?: return

            handler.removeCallbacks(progressRunnable)

            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }

            mediaPlayer.seekTo(positionMs)
            mediaPlayer.start()

            listener?.onPlayingChanged(true)
            handler.post(progressRunnable)
        }.onFailure {
            releaseOnError()
        }
    }

    fun toggle() {
        runCatching {
            val mediaPlayer = player ?: return

            handler.removeCallbacks(progressRunnable)

            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                listener?.onPlayingChanged(false)
            } else {
                mediaPlayer.start()
                listener?.onPlayingChanged(true)
                handler.post(progressRunnable)
            }
        }.onFailure {
            releaseOnError()
        }
    }

    fun pause() {
        runCatching {
            handler.removeCallbacks(progressRunnable)

            val mediaPlayer = player ?: return

            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                listener?.onPlayingChanged(false)
            }
        }.onFailure {
            releaseOnError()
        }
    }

    fun seekForward() {
        runCatching {
            val mediaPlayer = player ?: return

            handler.removeCallbacks(progressRunnable)
            mediaPlayer.pause()

            val target = min(clipEndMs, mediaPlayer.currentPosition + SEEK_STEP_MS)
            mediaPlayer.seekTo(target)

            mediaPlayer.start()
            listener?.onPlayingChanged(true)
            handler.post(progressRunnable)
        }.onFailure {
            releaseOnError()
        }
    }

    fun seekBackward() {
        runCatching {
            val mediaPlayer = player ?: return

            handler.removeCallbacks(progressRunnable)
            mediaPlayer.pause()

            val target = max(clipStartMs, mediaPlayer.currentPosition - SEEK_STEP_MS)
            mediaPlayer.seekTo(target)

            mediaPlayer.start()
            listener?.onPlayingChanged(true)
            handler.post(progressRunnable)
        }.onFailure {
            releaseOnError()
        }
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)

        runCatching {
            player?.release()
        }

        player = null
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        pause()
        seekTo(clipStartMs)
        listener?.onPlayingChanged(false)
    }

    override fun onError(
        mediaPlayer: MediaPlayer,
        what: Int,
        extra: Int
    ): Boolean {
        releaseOnError()
        return true
    }

    private fun createPlayer() {
        runCatching {
            MediaPlayer().apply {
                setOnCompletionListener(this@SoundPreviewPlayer)
                setOnErrorListener(this@SoundPreviewPlayer)
                dataSourceResolver(music)
                prepare()
            }
        }.onSuccess {
            player = it
            clipEndMs = it.duration
        }.onFailure {
            releaseOnError()
        }
    }

    private fun releaseOnError() {
        release()
        listener?.onPlayingChanged(false)
    }

    private companion object {
        const val SEEK_STEP_MS = 2_000
        const val PROGRESS_DELAY_MS = 100L
    }
}
