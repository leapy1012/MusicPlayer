package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.data.local.preference.ThemeSettingPreferenceStore
import gd.app.musicplayer.data.local.preference.ThemeSettings
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetThemeSettingsUseCase @Inject constructor(
    private val repo: ThemeSettingPreferenceStore
) {
    suspend operator fun invoke(): ThemeSettings {
        return repo.getSettingsSnapshot()
    }
}
