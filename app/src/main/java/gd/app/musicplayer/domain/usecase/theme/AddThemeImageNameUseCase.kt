package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import javax.inject.Inject

class AddThemeImageNameUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(fileName: String) = preferencesRepo.addThemeImageName(fileName)
}
