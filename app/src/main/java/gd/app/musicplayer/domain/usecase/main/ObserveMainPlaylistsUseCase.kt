package gd.app.musicplayer.domain.usecase.main

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.MainRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveMainPlaylistsUseCase @Inject constructor(
    private val mainRepo: MainRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Playlist>> = mainRepo.observePlaylists()
}
