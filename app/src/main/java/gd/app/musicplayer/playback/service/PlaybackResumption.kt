package gd.app.musicplayer.playback.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.playback.restore.RestoredPlayback
import gd.app.musicplayer.playback.state.PublishReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loads storage candidates without preparing a player, requesting focus or starting playback. */
internal suspend fun MusicPlaybackService.loadPlaybackCandidate(): RestoredPlayback? =
    resumeLoader.load(includeLibraryFallback = true)

internal suspend fun MusicPlaybackService.recentPlaybackItem(): MediaItem? {
    val candidate = loadPlaybackCandidate() ?: return null
    val item = candidate.queue[candidate.index].toMediaItemOrNull() ?: return null
    return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
        .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
}

internal suspend fun MusicPlaybackService.ensurePlaybackReady(): Boolean {
    if (playbackShuttingDown) throw CancellationException("Playback service is shutting down")
    ensurePlaybackRestored()
    if (queue.isEmpty()) {
        val candidate = loadPlaybackCandidate()
        if (candidate != null && queue.isEmpty()) {
            setQueueState(candidate.queue, candidate.index)
            prepareRestoredPlayerState(currentIndex, candidate.positionMs)
            queueManager.save()
            restoreManager.markRestored()
        }
    }
    if (queue.isEmpty()) {
        publishPlaybackState(PublishReason.Restore, forceNotification = true)
        gd.app.musicplayer.core.common.util.ToastUtil.show(this,
            gd.app.musicplayer.R.string.playback_no_playable_music)
        notificationController.stopForegroundAndRemove()
        return false
    }
    return true
}

internal suspend fun MusicPlaybackService.resolvePlaybackResumption(): MediaSession.MediaItemsWithStartPosition {
    val request = playbackRequests.play()
    if (!ensurePlaybackReady()) throw UnsupportedOperationException("No playable music available.")
    if (!playbackRequests.isCurrent(request)) throw CancellationException("Playback was cancelled")
    val item = queue.getOrNull(currentIndex)?.toMediaItemOrNull()
        ?: throw UnsupportedOperationException("No playable music available.")
    return MediaSession.MediaItemsWithStartPosition(listOf(item), 0, resolveCurrentSnapshotPositionMs())
}

internal fun MusicPlaybackService.cancelPendingPlayback() {
    playbackRequests.cancel()
    resumeJob?.cancel()
    resumeJob = null
    playbackErrorJob?.cancel()
    playbackErrorJob = null
}

internal fun MusicPlaybackService.launchPlaybackRequest(action: suspend MusicPlaybackService.() -> Unit) {
    if (playbackShuttingDown) return
    cancelPendingPlayback()
    failedPlaybackIds.clear()
    val request = playbackRequests.play()
    resumeJob = serviceScope.launch {
        try {
            ensurePlaybackRestored()
            if (playbackRequests.isCurrent(request)) action()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            android.util.Log.e("MusicPlayback", "Could not start playback", error)
            pausePlayback(withFade = false)
            notificationController.stopForegroundAndRemove()
            gd.app.musicplayer.core.common.util.ToastUtil.show(this@launchPlaybackRequest,
                gd.app.musicplayer.R.string.playback_no_playable_music)
        }
    }
}
