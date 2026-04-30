package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.data.repo.ThemeSettings
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import javax.inject.Inject

class GetThemeSettingsUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): ThemeSettings = preferencesRepo.getThemeSettings()
}
