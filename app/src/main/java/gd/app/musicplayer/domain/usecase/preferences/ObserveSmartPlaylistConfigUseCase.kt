package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.SmartPlaylistPreferenceOps
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveSmartPlaylistConfigUseCase @Inject constructor(
    private val preferenceUtil: PreferenceUtil
) {
    operator fun invoke(): Flow<SmartPlaylistPreferenceOps.SmartPlaylistConfig> =
        preferenceUtil.observePreferenceChanges(
            SmartPlaylistPreferenceOps.KEY_PLAYLIST_TRACK_LIMIT_TIME,
            SmartPlaylistPreferenceOps.KEY_PLAYLIST_TRACK_LIMIT
        ).map { preferenceUtil.getSmartPlaylistConfig() }.distinctUntilChanged()
}
