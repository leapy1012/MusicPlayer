package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.SortPreferenceOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObservePlaylistSortUseCase(
    private val preferenceUtil: PreferenceUtil
) {
    operator fun invoke(): Flow<Pair<String, Boolean>> =
        preferenceUtil.observePreferenceChanges(
            SortPreferenceOps.KEY_PLAYLIST_SORT_STYLE,
            SortPreferenceOps.KEY_PLAYLIST_SORT_REVERSE,
            SortPreferenceOps.KEY_SELECTED_SORT_REVERSE
        ).map { preferenceUtil.getPlaylistSortStyle() to preferenceUtil.isPlaylistSortReversed() }
}

