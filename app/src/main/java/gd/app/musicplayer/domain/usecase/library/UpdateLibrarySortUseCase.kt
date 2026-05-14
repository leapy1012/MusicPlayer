package gd.app.musicplayer.domain.usecase.library

import androidx.datastore.preferences.core.stringPreferencesKey
import gd.app.musicplayer.data.local.preference.SortPreferencesDataStore
import gd.app.musicplayer.domain.model.MusicSet
import javax.inject.Inject

class UpdateLibrarySortUseCase @Inject constructor(
    private val preference: SortPreferencesDataStore
) {
    suspend operator fun invoke(
        musicSet: MusicSet,
        sortStyle: String,
        descending: Boolean,
        isSelectionMode: Boolean = false
    ) {

        if (isSelectionMode) {
            if (musicSet is MusicSet.Folders) {
                preference.setSelectableFoldersSortStyle(sortStyle)
                preference.setSelectableFoldersSortReversed(descending)
            } else {
                preference.setSelectableTracksSortStyle(sortStyle)
                preference.setSelectableTracksSortReverse(descending)
            }
        } else {
            when (musicSet) {
                is MusicSet.Artists -> {
                    preference.setArtistsSortStyle(sortStyle)
                    preference.setArtistsSortReversed(descending)
                }

                is MusicSet.Albums -> {
                    preference.setAlbumsSortStyle(sortStyle)
                    preference.setAlbumsSortReversed(descending)
                }

                is MusicSet.Genres -> {
                    preference.setGenresSortStyle(sortStyle)
                    preference.setGenresSortReversed(descending)
                }

                is MusicSet.Folders -> {
                    preference.setFoldersSortStyle(sortStyle)
                    preference.setFoldersSortReversed(descending)
                }

                is MusicSet.Playlists -> {
                    preference.setPlaylistsSortStyle(sortStyle)
                    preference.setPlaylistsSortReversed(descending)
                }

                is MusicSet.Playlist -> {
                    preference.setPlaylistSortStyle(musicSet, sortStyle)
                    preference.setPlaylistSortReversed(musicSet, descending)
                }

                else -> {
                    preference.setSortStyle(musicSet, sortStyle)
                    preference.setSortDescending(musicSet, descending)
                }
            }
        }

    }
}
