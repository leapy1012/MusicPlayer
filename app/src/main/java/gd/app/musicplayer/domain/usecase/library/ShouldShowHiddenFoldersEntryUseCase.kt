package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.local.preference.SortPreferencesDataStore
import javax.inject.Inject

class ShouldShowHiddenFoldersEntryUseCase @Inject constructor(
    private val preference: SortPreferencesDataStore
) {
    operator fun invoke(): Boolean =
        true
        // preference.shouldShowHiddenFoldersEntry()
}
