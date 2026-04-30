package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repo.HiddenRepo
import kotlinx.coroutines.flow.Flow

class ObserveVisibleSongsUseCase(
    private val hiddenRepo: HiddenRepo
) {
    operator fun invoke(): Flow<List<Music>> = hiddenRepo.observeVisibleSongs()
}

