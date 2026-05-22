package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import gd.app.musicplayer.core.datastore.ThemeSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveThemeSettingsUseCase @Inject constructor(
    private val themeSettingPreferenceStore: ThemeSettingPreferenceStore
) {
    operator fun invoke(): Flow<ThemeSettings> {
        return themeSettingPreferenceStore.settings
    }
}
