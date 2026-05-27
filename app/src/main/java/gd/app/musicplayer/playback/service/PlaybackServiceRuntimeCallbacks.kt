package gd.app.musicplayer.playback.service

/** Thin adapter so runtime orchestration does not depend on service internals. */
class PlaybackServiceRuntimeCallbacks(
    private val service: MusicPlaybackService
) : PlaybackServiceRuntime.Callbacks {

    override fun handleEmptyStartCommand(startId: Int): Int {
        return service.handleEmptyStartCommand(startId)
    }

    override fun promoteToForegroundForPlaybackCommand() {
        service.promoteToForegroundForPlaybackCommand()
    }
}

