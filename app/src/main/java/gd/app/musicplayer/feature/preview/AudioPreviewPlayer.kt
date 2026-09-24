package gd.app.musicplayer.feature.preview

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Isolated [MediaPlayer] for external-file preview. Does not touch the main playback queue.
 */
internal class AudioPreviewPlayer(
    context: Context,
    private val uri: Uri
) : MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener,
    AudioManager.OnAudioFocusChangeListener {

    interface Listener {
        fun onPlayingChanged(isPlaying: Boolean)
        fun onProgressChanged(positionMs: Int, durationMs: Int)
        fun onPrepared(durationMs: Int)
        fun onCompletion()
        fun onError()
    }

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var listener: Listener? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var userSeeking = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mediaPlayer = player ?: return
            if (!mediaPlayer.isPlaying || userSeeking) return

            listener?.onProgressChanged(mediaPlayer.currentPosition, mediaPlayer.duration)
            handler.postDelayed(this, PROGRESS_DELAY_MS)
        }
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun prepare() {
        releasePlayerOnly()

        val mediaPlayer = MediaPlayer()
        runCatching {
            mediaPlayer.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSourceForUri(this, uri)
                setOnCompletionListener(this@AudioPreviewPlayer)
                setOnErrorListener(this@AudioPreviewPlayer)
                setOnPreparedListener { prepared ->
                    player = prepared
                    listener?.onPrepared(prepared.duration.coerceAtLeast(0))
                    play()
                }
                prepareAsync()
            }
            player = mediaPlayer
        }.onFailure {
            runCatching { mediaPlayer.release() }
            player = null
            listener?.onError()
        }
    }

    fun currentUri(): Uri = uri

    fun isPlaying(): Boolean {
        return runCatching { player?.isPlaying == true }.getOrDefault(false)
    }

    fun duration(): Int {
        return runCatching { player?.duration ?: 0 }.getOrDefault(0)
    }

    fun toggle() {
        if (isPlaying()) pause() else play()
    }

    fun play() {
        runCatching {
            val mediaPlayer = player ?: return
            if (!requestAudioFocus()) return

            mediaPlayer.start()
            listener?.onPlayingChanged(true)
            handler.removeCallbacks(progressRunnable)
            handler.post(progressRunnable)
        }.onFailure {
            listener?.onError()
        }
    }

    fun pause() {
        runCatching {
            handler.removeCallbacks(progressRunnable)
            val mediaPlayer = player ?: return
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
            listener?.onPlayingChanged(false)
        }
    }

    fun seekTo(positionMs: Int) {
        runCatching {
            player?.seekTo(positionMs.coerceAtLeast(0))
        }
    }

    fun beginUserSeek() {
        userSeeking = true
        handler.removeCallbacks(progressRunnable)
    }

    fun endUserSeek(positionMs: Int) {
        userSeeking = false
        seekTo(positionMs)
        if (isPlaying()) {
            handler.post(progressRunnable)
        } else {
            listener?.onProgressChanged(positionMs, duration())
        }
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)
        abandonAudioFocus()
        releasePlayerOnly()
        listener = null
    }

    override fun onCompletion(mp: MediaPlayer) {
        handler.removeCallbacks(progressRunnable)
        listener?.onPlayingChanged(false)
        listener?.onProgressChanged(0, duration())
        runCatching { mp.seekTo(0) }
        listener?.onCompletion()
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        handler.removeCallbacks(progressRunnable)
        listener?.onError()
        return true
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pause()
            AudioManager.AUDIOFOCUS_GAIN -> Unit
        }
    }

    private fun setDataSourceForUri(mediaPlayer: MediaPlayer, uri: Uri) {
        val path = resolveLocalPath(uri)
        if (path != null) {
            runCatching {
                mediaPlayer.setDataSource(path)
                return
            }
        }
        mediaPlayer.setDataSource(appContext, uri)
    }

    private fun resolveLocalPath(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path?.takeIf { File(it).canRead() }
        }
        val segments = uri.pathSegments.orEmpty()
        val externalIndex = segments.indexOfFirst { it.equals("external", ignoreCase = true) }
        if (externalIndex >= 0 && externalIndex < segments.lastIndex) {
            val relative = segments.drop(externalIndex + 1).joinToString("/")
            if (relative.isNotBlank()) {
                val candidate = "/storage/emulated/0/$relative"
                if (File(candidate).canRead()) return candidate
            }
        }
        return null
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }

        focusRequest = null
        hasAudioFocus = false
    }

    private fun releasePlayerOnly() {
        runCatching {
            player?.reset()
            player?.release()
        }
        player = null
    }

    private companion object {
        const val PROGRESS_DELAY_MS = 200L
    }
}
