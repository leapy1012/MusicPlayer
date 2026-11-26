package gd.app.musicplayer.playback.service

import androidx.media3.common.PlaybackException
import gd.app.musicplayer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun MusicPlaybackService.recoverPlaybackError(error: PlaybackException) {
    val failed = queue.getOrNull(currentIndex) ?: return
    failedPlaybackIds.add(failed.id)
    playbackErrorJob?.cancel()
    val message = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> R.string.playback_file_unavailable
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> R.string.playback_format_unsupported
        else -> R.string.playback_track_failed
    }
    gd.app.musicplayer.core.common.util.ToastUtil.show(this, getString(message, failed.title))
    if (!playbackRequests.wantsPlayback && !player.playWhenReady) return
    val request = playbackRequests.play()
    playbackErrorJob = serviceScope.launch {
        delay(2_000L)
        if (!playbackRequests.isCurrent(request)) return@launch
        val next = (1..queue.size).map { (currentIndex + it) % queue.size }
            .firstOrNull { queue[it].id !in failedPlaybackIds }
        if (next == null) {
            pausePlayback(withFade = false)
            notificationController.stopForegroundDetached()
        } else playIndex(next, playWhenReady = true)
    }
}
