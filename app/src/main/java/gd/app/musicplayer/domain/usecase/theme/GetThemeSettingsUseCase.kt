package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import gd.app.musicplayer.core.datastore.ThemeSettings
import javax.inject.Inject

class GetThemeSettingsUseCase @Inject constructor(
    private val repo: ThemeSettingPreferenceStore
) {
    suspend operator fun invoke(): ThemeSettings {
        return repo.getSettingsSnapshot()
    }
}
