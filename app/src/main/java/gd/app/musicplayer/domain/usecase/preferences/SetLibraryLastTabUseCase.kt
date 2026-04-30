package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import javax.inject.Inject

class SetLibraryLastTabUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(tabId: Int) = preferencesRepo.setLibraryLastTab(tabId)
}
