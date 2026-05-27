package gd.app.musicplayer.playback.shutdown

/**
 * Small coordinator that owns service-level shutdown policy before delegating to
 * the detailed ShutdownController implementation.
 */
class PlaybackShutdownCoordinator(
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun clearIdleNotificationPolicy()
        fun disableStopAfterCurrentTrack()
        fun markNotificationAsVisibleAgain()
        fun shutdownControllerOrNull(): ShutdownController?
    }

    fun shutdown(options: ShutdownOptions) {
        callbacks.clearIdleNotificationPolicy()
        callbacks.disableStopAfterCurrentTrack()
        callbacks.markNotificationAsVisibleAgain()
        callbacks.shutdownControllerOrNull()?.shutdown(options)
    }
}
