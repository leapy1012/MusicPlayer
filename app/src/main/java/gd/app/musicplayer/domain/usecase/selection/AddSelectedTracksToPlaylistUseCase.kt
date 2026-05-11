package gd.app.musicplayer.domain.usecase.selection

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaylistRepo
import javax.inject.Inject

class AddSelectedTracksToPlaylistUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(request: AddSelectedTracksToPlaylistRequest): AddSelectedTracksToPlaylistResult {
        val targetPlaylistId = request.targetPlaylist.id
        val trackIds = request.selectedTracks.map(Music::id).distinct()
        if (trackIds.isEmpty() || targetPlaylistId <= 0L) {
            return AddSelectedTracksToPlaylistResult(insertedCount = 0, skippedCount = trackIds.size)
        }

        val insertedCount = playlistRepo.addTracksToPlaylists(
            playlistIds = listOf(targetPlaylistId),
            tracks = request.selectedTracks
        )

        return AddSelectedTracksToPlaylistResult(
            insertedCount = insertedCount,
            skippedCount = trackIds.size - insertedCount
        )
    }
}
