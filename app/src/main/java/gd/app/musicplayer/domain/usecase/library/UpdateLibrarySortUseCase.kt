package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.core.datastore.SortPreferencesDataStore
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.LibraryRepo
import javax.inject.Inject

class UpdateLibrarySortUseCase @Inject constructor(
    private val preference: SortPreferencesDataStore,
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(
        musicSet: MusicSet,
        sortStyle: String,
        descending: Boolean,
        isSelectionMode: Boolean = false
    ) {
        // Original: picking Random runs u5.d.o0 once before prefs refresh.
        if (!isSelectionMode && sortStyle == SORT_RANDOM) {
            libraryRepo.shuffleRandomSortRanks(musicSet)
        }

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

    private companion object {
        const val SORT_RANDOM = "random"
    }
}
