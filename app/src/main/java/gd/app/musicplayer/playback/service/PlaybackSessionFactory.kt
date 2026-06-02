package gd.app.musicplayer.playback.service

import javax.inject.Inject

internal class PlaybackSessionFactory @Inject constructor() {

    fun create(service: MusicPlaybackService): PlaybackSession {
        return PlaybackSession(service = service)
    }
}
