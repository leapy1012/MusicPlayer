package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.data.repo.ThemeSettings
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveThemeSettingsUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): Flow<ThemeSettings> = preferencesRepo.observeThemeSettings()
}
