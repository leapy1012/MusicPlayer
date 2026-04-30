package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.repo.UserPreferencesRepo

class GetHiddenFoldersVisibleUseCase(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): Boolean = preferencesRepo.shouldShowHiddenFolders()
}

