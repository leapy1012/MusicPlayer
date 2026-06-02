package gd.app.musicplayer.playback.shutdown

/**
 * Small coordinator that owns service-level shutdown policy before delegating to
 * the detailed ShutdownController implementation.
 */
class PlaybackShutdownCoordinator(
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun prepareForShutdown(options: ShutdownOptions)
        fun finalizeShutdown(options: ShutdownOptions)
        fun shutdownControllerOrNull(): ShutdownController?
    }

    fun shutdown(options: ShutdownOptions) {
        callbacks.prepareForShutdown(options)
        callbacks.shutdownControllerOrNull()?.shutdown(options)
        callbacks.finalizeShutdown(options)
    }
}
