package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.core.datastore.ViewModePreferences
import gd.app.musicplayer.domain.model.MusicSet
import javax.inject.Inject

class SetListViewModeUseCase @Inject constructor(
    private val preference: ViewModePreferences
) {
    suspend operator fun invoke(musicSet: MusicSet, mode: Int) =
        preference.setListViewMode(musicSet, mode)
}
