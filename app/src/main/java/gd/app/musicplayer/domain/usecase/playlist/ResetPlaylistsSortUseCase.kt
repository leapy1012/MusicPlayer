package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.local.preference.SortPreferencesDataStore
import javax.inject.Inject

class ResetPlaylistsSortUseCase @Inject constructor(
    private val sortPreferencesDataStore: SortPreferencesDataStore
) {
    suspend operator fun invoke() {
        sortPreferencesDataStore.setPlaylistsSortStyle("default")
        sortPreferencesDataStore.setPlaylistsSortReversed(false)
    }
}
