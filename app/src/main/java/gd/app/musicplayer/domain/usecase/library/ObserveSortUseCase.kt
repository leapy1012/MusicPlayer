package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.core.datastore.SortPreferencesDataStore
import gd.app.musicplayer.domain.model.MusicSet
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveSortUseCase @Inject constructor(
    private val preference: SortPreferencesDataStore
) {
    operator fun invoke(musicSet: MusicSet, isSelectionMode: Boolean = false): Flow<Pair<String, Boolean>> {
        if (isSelectionMode) {
            return combine(
                preference.observeSortStyle(musicSet, true),
                preference.observeSortDescending(musicSet, true)
            ) { style, reversed -> style to reversed }
        }
        return when (musicSet) {
            is MusicSet.Artists -> combine(
                preference.observeArtistsSortStyle(),
                preference.observeArtistsSortReversed()
            ) { style, reversed -> style to reversed }

            is MusicSet.Albums -> combine(
                preference.observeAlbumsSortStyle(),
                preference.observeAlbumsSortReversed()
            ) { style, reversed -> style to reversed }

            is MusicSet.Genres -> combine(
                preference.observeGenresSortStyle(),
                preference.observeGenresSortReversed()
            ) { style, reversed -> style to reversed }

            is MusicSet.Folders -> combine(
                preference.observeFoldersSortStyle(),
                preference.observeFoldersSortReversed()
            ) { style, reversed -> style to reversed }

            is MusicSet.Playlists -> combine(
                preference.observePlaylistsSortStyle(),
                preference.observePlaylistsSortReversed()
            ) { style, reversed -> style to reversed }

            is MusicSet.Playlist -> combine(
                preference.observePlaylistSortStyle(musicSet),
                preference.observePlaylistSortReversed(musicSet)
            ) { style, reversed -> style to reversed }

            else -> combine(
                preference.observeSortStyle(musicSet, false),
                preference.observeSortDescending(musicSet, false)
            ) { style, reversed -> style to reversed }
        }
    }
}
