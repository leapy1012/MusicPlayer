package gd.app.musicplayer.playback.service

import gd.app.musicplayer.domain.model.Music

internal class PlaybackStartupState {
    @Volatile
    var pendingResumeAfterDefaultQueue: Boolean = false

    @Volatile
    var cachedDefaultTracks: List<Music> = emptyList()

    @Volatile
    var cachedPlayableDefaultTracks: List<Music> = emptyList()

    @Volatile
    var deferForcedStartupUiUpdates: Boolean = false

    @Volatile
    var pendingQueueSessionSyncAfterStartupPlay: Boolean = false

    fun reset() {
        pendingResumeAfterDefaultQueue = false
        cachedDefaultTracks = emptyList()
        cachedPlayableDefaultTracks = emptyList()
        deferForcedStartupUiUpdates = false
        pendingQueueSessionSyncAfterStartupPlay = false
    }
}
