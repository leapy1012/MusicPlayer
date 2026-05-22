package gd.app.musicplayer.playback.state

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.NotificationMediaSessionBridge
import gd.app.musicplayer.playback.PlaybackNotificationController
import gd.app.musicplayer.playback.PlaybackStatePublisher

class PlaybackStateOrchestrator(
    private val notificationSessionBridge: NotificationMediaSessionBridge,
    private val notificationController: PlaybackNotificationController,
    private val statePublisher: PlaybackStatePublisher,
    private val isEffectivelyPlayingProvider: () -> Boolean,
    private val syncCurrentIndexWithPlayer: () -> Unit,
    private val notificationDismissedByUserProvider: () -> Boolean,
    private val setNotificationDismissedByUser: (Boolean) -> Unit
) {

    fun publishAllRuntimeState(
        forceNotification: Boolean = false
    ) {
        publishPlaybackState(
            reason = PublishReason.PlayerEvent,
            forceNotification = forceNotification
        )
    }

    fun publishPlaybackState(
        reason: PublishReason,
        forceNotification: Boolean = false,
        forceWidgetUpdate: Boolean = forceNotification
    ) {
        if (
            reason == PublishReason.ProgressTick &&
            notificationDismissedByUserProvider() &&
            !isEffectivelyPlayingProvider()
        ) {
            return
        }

        syncCurrentIndexWithPlayer()

        if (reason != PublishReason.ProgressTick) {
            updateNotification(force = forceNotification)
            notificationSessionBridge.updatePlaybackState()
        }

        statePublisher.publish(
            forceWidgetUpdate = forceWidgetUpdate
        )
    }

    fun updateNotification(force: Boolean = false) {
        if (isEffectivelyPlayingProvider()) {
            setNotificationDismissedByUser(false)
        }

        notificationController.update(
            force = force,
            keepWhenPaused = !notificationDismissedByUserProvider()
        )
    }

    fun publishStateAfterShutdown(
        snapshot: PlaybackSnapshot? = null,
        notifyWidgets: Boolean = true
    ) {
        notificationSessionBridge.updatePlaybackState()

        if (snapshot == null) {
            publishPlaybackState(
                reason = PublishReason.Shutdown,
                forceNotification = true,
                forceWidgetUpdate = true
            )
            return
        }

        statePublisher.publishSnapshot(
            queue = snapshot.queue,
            currentIndex = snapshot.currentIndex,
            currentTrack = snapshot.currentTrack,
            isPlaying = false,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            audioSessionId = snapshot.audioSessionId,
            notifyWidgets = notifyWidgets
        )
    }

    fun publishRestored(
        index: Int,
        positionMs: Long,
        queue: List<Music>
    ) {
        statePublisher.publishRestored(
            index,
            positionMs,
            queue
        )
    }

    fun resetRuntimeState() {
        statePublisher.reset()
    }

    fun publishSnapshot(
        snapshot: PlaybackSnapshot,
        isPlaying: Boolean
    ) {
        statePublisher.publishSnapshot(
            queue = snapshot.queue,
            currentIndex = snapshot.currentIndex,
            currentTrack = snapshot.currentTrack,
            isPlaying = isPlaying,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            audioSessionId = snapshot.audioSessionId
        )
    }
}
