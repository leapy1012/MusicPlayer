package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class UpdatePlaylistAddPositionUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(position: Int) = repository.updatePlaylistAddPosition(position)
}
