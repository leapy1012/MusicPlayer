package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.HiddenRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHiddenSongsUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo
) {
    operator fun invoke(): Flow<List<Music>> = hiddenRepo.observeHiddenSongs()
}
