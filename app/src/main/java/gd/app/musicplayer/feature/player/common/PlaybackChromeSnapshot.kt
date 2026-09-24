package gd.app.musicplayer.feature.player.common

import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.feature.player.full.PlaybackProgressUiState
import gd.app.musicplayer.feature.player.full.TrackUiState
import gd.app.musicplayer.playback.queue.MusicPlaybackState

/**
 * Sync UI snapshot from the process playback singleton — same idea as original
 * banner fragments reading [y6.y] immediately on create, without waiting on Flows.
 */
object PlaybackChromeSnapshot {

    fun trackUiState(state: MusicPlaybackState): TrackUiState {
        val music = state.currentTrack ?: return TrackUiState()
        return TrackUiState(
            musicId = music.id,
            title = music.title,
            artist = music.artist,
            album = music.album,
            artworkSource = music.albumArtSource(),
            isFavorite = music.isFavorite()
        )
    }

    fun progressUiState(state: MusicPlaybackState): PlaybackProgressUiState {
        return PlaybackProgressUiState(
            isPlaying = state.isPlaying,
            positionMs = state.positionMs,
            durationMs = state.durationMs
        )
    }
}
