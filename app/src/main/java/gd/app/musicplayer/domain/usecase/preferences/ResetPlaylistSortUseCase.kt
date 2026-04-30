package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.util.PreferenceUtil

class ResetPlaylistSortUseCase(
    private val preferenceUtil: PreferenceUtil
) {
    operator fun invoke() {
        preferenceUtil.setPlaylistSortStyle("default")
        preferenceUtil.setPlaylistSortReversed(false)
    }
}

