package gd.app.musicplayer.domain.usecase.preferences


import gd.app.musicplayer.data.local.preference.PlaylistPreferenceDataStore
import gd.app.musicplayer.data.model.SmartPlaylistConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSmartPlaylistConfigUseCase @Inject constructor(
    private val playlistPreferenceDataStore: PlaylistPreferenceDataStore
) {
    operator fun invoke(): Flow<SmartPlaylistConfig> =
        playlistPreferenceDataStore.observeSmartPlaylistConfig()
}
