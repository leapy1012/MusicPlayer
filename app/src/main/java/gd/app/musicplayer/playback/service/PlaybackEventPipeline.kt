package gd.app.musicplayer.playback.service

import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PublishReason

internal sealed interface PlaybackEvent {
    data class RuntimeStateChanged(
        val forceNotification: Boolean = false
    ) : PlaybackEvent

    data class PlaybackStateChanged(
        val reason: PublishReason,
        val forceNotification: Boolean = false,
        val forceWidgetUpdate: Boolean = forceNotification
    ) : PlaybackEvent

    data class ShutdownStatePublished(
        val snapshot: PlaybackSnapshot? = null
    ) : PlaybackEvent

    data object QueueMetadataChanged : PlaybackEvent

    data class NotificationUpdateRequested(
        val force: Boolean = false
    ) : PlaybackEvent
}

internal class PlaybackEventDispatcher(
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun publishAllRuntimeState(forceNotification: Boolean)
        fun publishPlaybackState(
            reason: PublishReason,
            forceNotification: Boolean,
            forceWidgetUpdate: Boolean
        )
        fun publishStateAfterShutdown(snapshot: PlaybackSnapshot?)
        fun onQueueMetadataChanged()
        fun updateNotification(force: Boolean)
    }

    fun dispatch(event: PlaybackEvent) {
        when (event) {
            is PlaybackEvent.RuntimeStateChanged -> {
                callbacks.publishAllRuntimeState(event.forceNotification)
            }

            is PlaybackEvent.PlaybackStateChanged -> {
                callbacks.publishPlaybackState(
                    reason = event.reason,
                    forceNotification = event.forceNotification,
                    forceWidgetUpdate = event.forceWidgetUpdate
                )
            }

            is PlaybackEvent.ShutdownStatePublished -> {
                callbacks.publishStateAfterShutdown(event.snapshot)
            }

            PlaybackEvent.QueueMetadataChanged -> {
                callbacks.onQueueMetadataChanged()
            }

            is PlaybackEvent.NotificationUpdateRequested -> {
                callbacks.updateNotification(event.force)
            }
        }
    }
}
