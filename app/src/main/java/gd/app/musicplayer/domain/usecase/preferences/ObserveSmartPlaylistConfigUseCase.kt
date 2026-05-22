package gd.app.musicplayer.domain.usecase.preferences


import gd.app.musicplayer.core.datastore.PlaylistPreferenceDataStore
import gd.app.musicplayer.domain.model.SmartPlaylistConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSmartPlaylistConfigUseCase @Inject constructor(
    private val playlistPreferenceDataStore: PlaylistPreferenceDataStore
) {
    operator fun invoke(): Flow<SmartPlaylistConfig> =
        playlistPreferenceDataStore.observeSmartPlaylistConfig()
}
