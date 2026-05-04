package gd.app.musicplayer.domain.usecase.selection

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repo.PlaylistRepo
import javax.inject.Inject

class ConfirmMusicSelectUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(request: MusicSelectConfirmRequest): MusicSelectConfirmResult {
        val targetPlaylistId = request.targetSet.id
        val songIds = request.selectedSongs.map(Music::id).distinct()
        if (songIds.isEmpty() || targetPlaylistId <= 0L) {
            return MusicSelectConfirmResult(insertedCount = 0, skippedCount = songIds.size)
        }

        val insertedCount = playlistRepo.addTracksToPlaylists(
            playlistIds = listOf(targetPlaylistId),
            tracks = request.selectedSongs
        )

        return MusicSelectConfirmResult(
            insertedCount = insertedCount,
            skippedCount = songIds.size - insertedCount
        )
    }
}
