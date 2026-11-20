package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

/** Original [w7.v.w1] — `preference_click_add_queue`. */
class GetClickAddQueueEnabledUseCase @Inject constructor(
    private val settingsPreference: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): Boolean = settingsPreference.getClickAddQueueEnabled()
}
