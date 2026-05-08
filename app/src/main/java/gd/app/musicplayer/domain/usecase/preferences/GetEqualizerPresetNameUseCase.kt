package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.data.repository.EqualizerPresetRepository
import javax.inject.Inject

class GetEqualizerPresetNameUseCase @Inject constructor(
    private val repo: EqualizerPresetRepository
) {
//    operator fun invoke(): String = repo.getEqualizerPresetName()
}
