package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.repo.LibraryRepo
import javax.inject.Inject

class ShouldShowHiddenFoldersEntryUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(): Boolean = libraryRepo.shouldShowHiddenFoldersEntry()
}
