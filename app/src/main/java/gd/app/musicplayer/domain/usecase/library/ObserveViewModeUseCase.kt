package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.local.preference.ViewModePreferences
import gd.app.musicplayer.data.model.MusicSet
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveViewModeUseCase @Inject constructor(
    private val preference: ViewModePreferences
) {
    operator fun invoke(musicSet: MusicSet): Flow<Int> = preference.observeListViewMode(musicSet)
}
