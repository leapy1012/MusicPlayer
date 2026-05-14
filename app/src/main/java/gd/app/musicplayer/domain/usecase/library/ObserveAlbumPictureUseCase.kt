package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.domain.repository.ArtworkRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveAlbumPictureUseCase @Inject constructor(
    private val artworkRepo: ArtworkRepo
) {
    operator fun invoke(musicId: Long): Flow<String?> =
        artworkRepo.observeTrackArtwork(musicId)
}
