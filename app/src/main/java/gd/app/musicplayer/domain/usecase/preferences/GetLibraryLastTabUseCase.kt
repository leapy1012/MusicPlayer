package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import javax.inject.Inject

class GetLibraryLastTabUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): Int = preferencesRepo.getLibraryLastTab()
}
