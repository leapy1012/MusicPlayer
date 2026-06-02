package gd.app.musicplayer.playback.service

internal class MusicPlaybackServiceRuntimeActions(
    private val service: MusicPlaybackService
) : PlaybackServiceRuntime.Callbacks {

    override fun handleEmptyStartCommand(startId: Int): Int {
        return service.handleEmptyStartCommand(startId)
    }

    override fun promoteToForegroundForPlaybackCommand() {
        service.promoteToForegroundForPlaybackCommand()
    }
}
