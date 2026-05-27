package gd.app.musicplayer.playback.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.AppForegroundTracker
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.datastore.DesktopLyricPreferenceStore
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.core.datastore.SettingPreferences
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.core.datastore.StatusBarLyricPreferenceStore
import gd.app.musicplayer.core.datastore.TrackLyricPreferenceStore
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.domain.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.library.ObserveAlbumPictureUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.artwork.ArtworkLoader
import gd.app.musicplayer.playback.effects.AudioEffectsManager
import gd.app.musicplayer.playback.AudioFocusController
import gd.app.musicplayer.playback.headset.HeadsetMediaButtonHandler
import gd.app.musicplayer.playback.notification.NotificationMediaSessionBridge
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.notification.PlaybackNotificationController
import gd.app.musicplayer.playback.command.PlaybackCommandPayloadStore
import gd.app.musicplayer.playback.command.IndexActionData
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import gd.app.musicplayer.playback.state.PlaybackStatePublisher
import gd.app.musicplayer.playback.PlaybackStatsTracker
import gd.app.musicplayer.playback.effects.PlaybackTuningController
import gd.app.musicplayer.playback.headset.ScreenOffLockReceiver
import gd.app.musicplayer.playback.effects.StereoBalanceAudioProcessor
import gd.app.musicplayer.playback.timer.SleepTimerManager
import gd.app.musicplayer.playback.effects.VolumeFader
import gd.app.musicplayer.ui.shell.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import gd.app.musicplayer.playback.artwork.CurrentArtworkController
import gd.app.musicplayer.playback.favorite.CurrentFavoriteController
import gd.app.musicplayer.playback.notification.NotificationCloseCallbacks
import gd.app.musicplayer.playback.notification.NotificationCloseController
import gd.app.musicplayer.playback.player.MusicPlayerFactory
import gd.app.musicplayer.playback.player.PlaybackEngine
import gd.app.musicplayer.playback.player.PlayerEventHandler
import gd.app.musicplayer.playback.progress.PlaybackProgressTicker
import gd.app.musicplayer.playback.queue.PlaybackQueueManager
import gd.app.musicplayer.playback.queue.PlayerQueueController
import gd.app.musicplayer.playback.queue.QueueActionController
import gd.app.musicplayer.playback.queue.QueueMutationCallbacks
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PlaybackSnapshotManager
import gd.app.musicplayer.playback.restore.PlaybackRestoreManager
import gd.app.musicplayer.playback.restore.RestoreStatus
import gd.app.musicplayer.playback.shutdown.ShutdownCallbacks
import gd.app.musicplayer.playback.shutdown.ShutdownController
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.shutdown.PlaybackShutdownCoordinator
import gd.app.musicplayer.playback.state.PlaybackStateOrchestrator
import gd.app.musicplayer.playback.state.PlaybackStateUpdateCoordinator
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.transition.TimedTransitionController
import gd.app.musicplayer.playback.desktop.DesktopLyricsOverlayController
import gd.app.musicplayer.playback.statusbar.StatusBarLyricsOverlayController
import gd.app.musicplayer.feature.widget.provider.WidgetUpdateCoordinator
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot
import kotlinx.coroutines.NonCancellable


internal fun MusicPlaybackService.updateStopAfterCurrentTrackMode(enabled: Boolean) {
    stopAfterCurrentTrack = enabled
    if (isPlayerInitialized()) {
        player.pauseAtEndOfMediaItems = enabled
    }
}

internal fun MusicPlaybackService.resumeWithDefaultQueue() {
    if (defaultQueueRestoreJob?.isActive == true) return

    defaultQueueRestoreJob = serviceScope.launch {
        val tracks = withContext(dispatchers.io) {
            runCatching {
                observeTracksUseCase(MusicSet.Tracks).first()
            }.getOrDefault(emptyList())
        }

        if (tracks.isEmpty()) {
            pendingResumeAfterDefaultQueue = false
            playbackRuntimeStateStore.initializeIfNeeded()
            publishPlaybackState(
                reason = PublishReason.Restore,
                forceNotification = true
            )
            return@launch
        }

        val playableTracks = filterPlayableQueue(tracks)

        if (playableTracks.isEmpty()) {
            pendingResumeAfterDefaultQueue = false
            playbackRuntimeStateStore.initializeIfNeeded()
            publishPlaybackState(
                reason = PublishReason.Restore,
                forceNotification = true
            )
            return@launch
        }

        withContext(dispatchers.io) {
            playbackQueueRepo.replaceQueue(playableTracks)
            playbackStatePreferenceStore.setMusicProgress(
                trackId = playableTracks.first().id,
                progressMs = 0,
                currentIndex = 0
            )
        }

        setQueueState(
            newQueue = playableTracks,
            requestedIndex = 0
        )

        restoreManager.markRestored()

        notificationSessionBridge.updateQueue()

        val shouldAutoResume = pendingResumeAfterDefaultQueue
        pendingResumeAfterDefaultQueue = false

        if (!audioFocusController.request()) {
            return@launch
        }

        setPlayerQueue(
            queue = queue,
            startIndex = 0,
            startPositionMs = 0L,
            playWhenReady = shouldAutoResume
        )

        applyVolumeForPlaybackStart(playWhenReady = shouldAutoResume)
        refreshArtworkAndSession(force = true)
        publishAllRuntimeState(forceNotification = true)
    }
}

internal fun MusicPlaybackService.handlePlayFromQueue(
    incomingQueue: List<Music>,
    incomingIndex: Int
) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.playFromQueue(
            incomingQueue = incomingQueue,
            incomingIndex = incomingIndex
        )
    }
}

internal fun MusicPlaybackService.handleEnqueue(incomingQueue: List<Music>) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.enqueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.handlePlayNextQueue(incomingQueue: List<Music>) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.playNextQueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.removeQueueItem(index: Int) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.removeQueueItem(index)
    }
}

internal fun MusicPlaybackService.moveQueueItem(
    fromIndex: Int,
    toIndex: Int
) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.moveQueueItem(
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }
}

internal fun MusicPlaybackService.replaceQueue(
    newQueue: List<Music>,
    requestedIndex: Int
) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.replaceQueue(
            newQueue = newQueue,
            requestedIndex = requestedIndex
        )
    }
}

internal fun MusicPlaybackService.playIndexFromCommand(index: Int) {
    serviceScope.launch {
        ensurePlaybackRestored()

        playIndex(
            index = index,
            playWhenReady = true
        )
    }
}

internal fun MusicPlaybackService.togglePlayPause() {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (isEffectivelyPlaying()) {
            pausePlaybackInternal()
        } else {
            resumePlaybackInternal()
        }
    }
}

internal fun MusicPlaybackService.resumePlayback() {
    if (resumeJob?.isActive == true) return

    resumeJob = serviceScope.launch {
        ensurePlaybackRestored()
        resumePlaybackInternal()
    }
}

internal fun MusicPlaybackService.pausePlayback(
    withFade: Boolean = playbackTuningController.isPlayPauseFadeEnabled()
) {
    pausePlaybackInternal(withFade)
}

internal fun MusicPlaybackService.pauseAndPersistForNotificationClose() {
    if (!isNotificationCloseControllerInitialized()) return

    notificationCloseController.pauseAndCloseNotification()
}

internal fun MusicPlaybackService.exitService() {
    shutdownPlayback(ShutdownOptions.ExitService)
}

internal fun MusicPlaybackService.clearQueueKeepingNotification() {
    keepIdleNotification = true
    notificationDismissedByUser = false
    shutdownPlayback(ShutdownOptions.ClearQueueKeepingNotification)
    notificationController.stopForegroundDetached()
    notificationController.update(force = true)
}

internal fun MusicPlaybackService.shutdownPlayback(options: ShutdownOptions) {
    if (isShutdownCoordinatorInitialized()) {
        shutdownCoordinator.shutdown(options)
        return
    }

    keepIdleNotification = false
    updateStopAfterCurrentTrackMode(false)
    notificationDismissedByUser = false

    if (isShutdownControllerInitialized()) {
        shutdownController.shutdown(options)
    }
}

internal fun MusicPlaybackService.handleNotificationFavoriteToggle() {
    toggleCurrentFavorite()
}

internal fun MusicPlaybackService.toggleCurrentFavorite() {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (isFavoriteControllerInitialized()) {
            favoriteController.toggleCurrent()
        }
    }
}

internal fun MusicPlaybackService.setCurrentFavorite(isFavorite: Boolean) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (isFavoriteControllerInitialized()) {
            favoriteController.setCurrentFavorite(isFavorite)
        }
    }
}

internal fun MusicPlaybackService.refreshArtworkAndSession(force: Boolean = false) {
    if (isArtworkControllerInitialized()) {
        artworkController.refresh(force = force)
        return
    }

    updateNotification(force = true)
}

internal fun MusicPlaybackService.updateEditedTrackMetadata(
    music: Music
) {
    val changed = queueManager.updateTrackMetadata(music)
    if (!changed) return

    queueManager.save()
    notificationSessionBridge.updateQueue()
    refreshArtworkAndSession(force = true)
    persistSessionFromCurrentStateAsync()
    publishPlaybackState(
        reason = PublishReason.QueueChanged,
        forceNotification = true,
        forceWidgetUpdate = true
    )
}

internal fun MusicPlaybackService.updateEditedTracksMetadata(
    music: List<Music>
) {
    val changed = queueManager.updateTracksMetadata(music)
    if (!changed) return

    queueManager.save()
    notificationSessionBridge.updateQueue()
    refreshArtworkAndSession(force = true)
    persistSessionFromCurrentStateAsync()
    publishPlaybackState(
        reason = PublishReason.QueueChanged,
        forceNotification = true,
        forceWidgetUpdate = true
    )
}

internal fun MusicPlaybackService.filterPlayableQueue(queue: List<Music>): List<Music> {
    return if (isPlayerQueueControllerInitialized()) {
        playerQueueController.filterPlayable(queue)
    } else {
        queue
    }
}

internal fun MusicPlaybackService.remapRequestedIndex(
    originalQueue: List<Music>,
    playableQueue: List<Music>,
    requestedIndex: Int
): Int {
    if (playableQueue.isEmpty()) return 0

    if (requestedIndex in playableQueue.indices) {
        val originalAtIndex = originalQueue.getOrNull(requestedIndex)
        val playableAtIndex = playableQueue[requestedIndex]
        if (originalAtIndex == playableAtIndex) return requestedIndex
    }

    val requestedTrackId = originalQueue.getOrNull(requestedIndex)?.id
    if (requestedTrackId != null) {
        val targetOccurrence = originalQueue
            .asSequence()
            .take(requestedIndex + 1)
            .count { music -> music.id == requestedTrackId }

        if (targetOccurrence > 0) {
            var seen = 0
            playableQueue.forEachIndexed { index, music ->
                if (music.id == requestedTrackId) {
                    seen += 1
                    if (seen == targetOccurrence) {
                        return index
                    }
                }
            }
        }
    }

    return requestedIndex.coerceIn(
        0,
        playableQueue.lastIndex
    )
}

internal fun MusicPlaybackService.clearArtworkState() {
    if (isArtworkControllerInitialized()) {
        artworkController.clear()
    } else if (isArtworkLoaderInitialized()) {
        artworkLoader.clear()
    }
}


