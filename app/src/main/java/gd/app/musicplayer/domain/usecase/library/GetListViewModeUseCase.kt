package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.LibraryRepo
import javax.inject.Inject

class GetListViewModeUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(musicSet: MusicSet): Int = libraryRepo.getListViewMode(musicSet)
}
