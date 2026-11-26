package gd.app.musicplayer.playback.state

import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot

/**
 * Single gateway for runtime state, notification, and widget publishing.
 *
 * MusicPlaybackService still supplies snapshots because it owns queue/player data,
 * but this class centralizes the publish policy so future changes happen here
 * instead of being scattered through the service.
 */
class PlaybackStateUpdateCoordinator(
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun stateOrchestratorOrNull(): PlaybackStateOrchestrator?
        fun shutdownWidgetSnapshot(snapshot: PlaybackSnapshot?): WidgetPlaybackSnapshot
        fun updateWidgetsBlocking(snapshot: WidgetPlaybackSnapshot)
    }

    fun publishAllRuntimeState(forceNotification: Boolean = false) {
        callbacks.stateOrchestratorOrNull()?.publishAllRuntimeState(
            forceNotification = forceNotification
        )
    }

    fun publishPlaybackState(
        reason: PublishReason,
        forceNotification: Boolean = false,
        forceWidgetUpdate: Boolean = forceNotification
    ) {
        callbacks.stateOrchestratorOrNull()?.publishPlaybackState(
            reason = reason,
            forceNotification = forceNotification,
            forceWidgetUpdate = forceWidgetUpdate
        )
    }

    fun updateNotification(force: Boolean = false) {
        callbacks.stateOrchestratorOrNull()?.updateNotification(force = force)
    }

    fun publishStateAfterShutdown(snapshot: PlaybackSnapshot? = null) {
        callbacks.stateOrchestratorOrNull()?.publishStateAfterShutdown(
            snapshot = snapshot,
            notifyWidgets = false
        )

        callbacks.updateWidgetsBlocking(
            callbacks.shutdownWidgetSnapshot(snapshot)
        )
    }
}
