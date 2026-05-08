package gd.app.musicplayer.domain.usecase.selection

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet

data class AddSelectedTracksToPlaylistRequest(
    val selectedTracks: List<Music>,
    val targetPlaylist: MusicSet.Playlist
)

data class AddSelectedTracksToPlaylistResult(
    val insertedCount: Int,
    val skippedCount: Int
)
