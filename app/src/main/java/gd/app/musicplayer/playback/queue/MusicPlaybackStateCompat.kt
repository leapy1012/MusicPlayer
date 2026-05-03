package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.data.model.Music

typealias PlaybackState = MusicPlaybackState

val MusicPlaybackState.currentTrack: Music?
    get() = currentMusic

val MusicPlaybackState.hasTrack: Boolean
    get() = currentMusic != null

val MusicPlaybackState.audioSessionId: Int
    get() = -1
