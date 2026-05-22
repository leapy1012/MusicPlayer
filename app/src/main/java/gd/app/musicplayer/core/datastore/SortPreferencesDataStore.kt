package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SortPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private fun getSortStyleKey(
        musicSet: MusicSet,
        isSelectionMode: Boolean
    ): Preferences.Key<String> {
        return when {
            isSelectionMode && musicSet is MusicSet.Folders -> {
                stringPreferencesKey("pref_select_folder_sort_style")
            }

            isSelectionMode -> {
                stringPreferencesKey("pref_music_select_sort_style")
            }

            else -> {
                val type = when(musicSet) {
                    is MusicSet.Tracks -> MusicSet.ALL_TRACKS
                    is MusicSet.Artist -> MusicSet.ARTISTS
                    is MusicSet.Album -> MusicSet.ALBUMS
                    is MusicSet.Genre -> MusicSet.GENRES
                    is MusicSet.Folder -> MusicSet.FOLDERS
                    else -> musicSet.id
                }
                stringPreferencesKey("pref_sort_style${type}")
            }
        }
    }

    fun observeSortStyle(musicSet: MusicSet, isSelectionMode: Boolean): Flow<String> {
        val key = getSortStyleKey(
            musicSet = musicSet,
            isSelectionMode = isSelectionMode
        )

        return dataStore.data.map { preferences ->
            preferences[key] ?: "name"
        }.distinctUntilChanged()
    }


    private fun getSortDescendingKey(
        musicSet: MusicSet,
        isSelectionMode: Boolean
    ): Preferences.Key<Boolean> {
        return when {
            isSelectionMode && musicSet is MusicSet.Folders -> {
                booleanPreferencesKey("pref_select_folder_sort_reverse")
            }

            isSelectionMode -> {
                booleanPreferencesKey("pref_select_sort_reverse")
            }

            else -> {
                val type = when(musicSet) {
                    is MusicSet.Tracks -> MusicSet.ALL_TRACKS
                    is MusicSet.Artist -> MusicSet.ARTISTS
                    is MusicSet.Album -> MusicSet.ALBUMS
                    is MusicSet.Genre -> MusicSet.GENRES
                    is MusicSet.Folder -> MusicSet.FOLDERS
                    else -> musicSet.id
                }
                booleanPreferencesKey("pref_sort_reverse${type}")
            }
        }
    }

    fun observeSortDescending(musicSet: MusicSet, isSelectionMode: Boolean): Flow<Boolean> {

        val key = getSortDescendingKey(
            musicSet = musicSet,
            isSelectionMode = isSelectionMode
        )

        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.distinctUntilChanged()
    }

    fun observeAlbumsSortStyle() : Flow<String> {
        val key = stringPreferencesKey("pref_album_sort_style")

        return dataStore.data.map { preferences ->
            preferences[key] ?: "name"
        }.distinctUntilChanged()
    }

    fun observeAlbumsSortReversed(): Flow<Boolean> {
        val key = booleanPreferencesKey("pref_album_sort_reverse")

        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.distinctUntilChanged()
    }

    fun observeArtistsSortStyle() : Flow<String> {
        val key = stringPreferencesKey("pref_artist_sort_style")

        return dataStore.data.map { preferences ->
            preferences[key] ?: "name"
        }.distinctUntilChanged()
    }

    fun observeArtistsSortReversed(): Flow<Boolean> {
        val key = booleanPreferencesKey("pref_artist_sort_reverse")

        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.distinctUntilChanged()
    }

    fun observeGenresSortStyle() : Flow<String> {
        val key = stringPreferencesKey("genre_sort")

        return dataStore.data.map { preferences ->
            preferences[key] ?: "name"
        }.distinctUntilChanged()
    }

    fun observeGenresSortReversed(): Flow<Boolean> {
        val key = booleanPreferencesKey("genre_sort_reverse")

        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.distinctUntilChanged()
    }

    fun observeFoldersSortStyle() : Flow<String> {
        val key = stringPreferencesKey("pref_folder_sort_style")

        return dataStore.data.map { preferences ->
            preferences[key] ?: "name"
        }.distinctUntilChanged()
    }

    fun observeFoldersSortReversed(): Flow<Boolean> {
        val key = booleanPreferencesKey("pref_folder_sort_reverse")

        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.distinctUntilChanged()
    }

    fun observePlaylistsSortStyle() : Flow<String> {
        val key = stringPreferencesKey("playlist_sort")

        return dataStore.data.map { preferences ->
            preferences[key] ?: "default"
        }.distinctUntilChanged()
    }

    fun observePlaylistsSortReversed(): Flow<Boolean> {
        val key = booleanPreferencesKey("playlist_sort_reverse")

        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.distinctUntilChanged()
    }

    fun observePlaylistSortStyle(musicSet: MusicSet.Playlist) : Flow<String> {
        val key = stringPreferencesKey("pref_sort_style${musicSet.id}")
        return dataStore.data.map { preferences ->
            preferences[key] ?: "default"
        }.distinctUntilChanged()
    }

    fun observePlaylistSortReversed(musicSet: MusicSet.Playlist): Flow<Boolean> {
        val key = booleanPreferencesKey("pref_sort_reverse${musicSet.id}")

        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.distinctUntilChanged()
    }

    suspend fun setSortStyle(musicSet: MusicSet, style: String) {
        val type = when(musicSet) {
            is MusicSet.Tracks -> MusicSet.ALL_TRACKS
            is MusicSet.Artist -> MusicSet.ARTISTS
            is MusicSet.Album -> MusicSet.ALBUMS
            is MusicSet.Genre -> MusicSet.GENRES
            is MusicSet.Folder -> MusicSet.FOLDERS
            else -> musicSet.id
        }

        val key = stringPreferencesKey("pref_sort_style${type}")
        dataStore.edit { preferences ->
            preferences[key] = style
        }
    }

    suspend fun setSortDescending(musicSet: MusicSet, descending: Boolean) {
        val type = when(musicSet) {
            is MusicSet.Tracks -> MusicSet.ALL_TRACKS
            is MusicSet.Artist -> MusicSet.ARTISTS
            is MusicSet.Album -> MusicSet.ALBUMS
            is MusicSet.Genre -> MusicSet.GENRES
            is MusicSet.Folder -> MusicSet.FOLDERS
            else -> musicSet.id
        }
        val key = booleanPreferencesKey("pref_sort_reverse${type}")

        dataStore.edit { preferences ->
            preferences[key] = descending
        }
    }

    suspend fun setAlbumsSortStyle(style: String) {
        val key = stringPreferencesKey("pref_album_sort_style")
        dataStore.edit { preferences ->
            preferences[key] = style
        }
    }

    suspend fun setAlbumsSortReversed(reversed: Boolean) {
        val key = booleanPreferencesKey("pref_album_sort_reverse")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }

    suspend fun setArtistsSortStyle(style: String) {
        val key = stringPreferencesKey("pref_artist_sort_style")
        dataStore.edit { preferences ->
            preferences[key] = style
        }
    }

    suspend fun setArtistsSortReversed(reversed: Boolean) {
        val key = booleanPreferencesKey("pref_artist_sort_reverse")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }

    suspend fun setGenresSortStyle(style: String) {
        val key = stringPreferencesKey("genre_sort")
        dataStore.edit { preferences ->
            preferences[key] = style
        }
    }

    suspend fun setGenresSortReversed(reversed: Boolean) {
        val key = booleanPreferencesKey("genre_sort_reverse")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }

    suspend fun setFoldersSortStyle(style: String) {
        val key = stringPreferencesKey("pref_folder_sort_style")
        dataStore.edit { preferences ->
            preferences[key] = style
        }
    }

    suspend fun setFoldersSortReversed(reversed: Boolean) {
        val key = booleanPreferencesKey("pref_folder_sort_reverse")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }

    suspend fun setPlaylistsSortStyle(style: String) {
        val key = stringPreferencesKey("playlist_sort")
        dataStore.edit { preferences ->
            preferences[key] = style
        }
    }

    suspend fun setPlaylistsSortReversed(reversed: Boolean) {
        val key = booleanPreferencesKey("playlist_sort_reverse")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }

    suspend fun setPlaylistSortStyle(musicSet: MusicSet.Playlist, style: String) {
        val key = stringPreferencesKey("pref_sort_style${musicSet.id}")
        dataStore.edit { preferences ->
            preferences[key] = style
        }
    }

    suspend fun setPlaylistSortReversed(musicSet: MusicSet.Playlist, reversed: Boolean) {
        val key = booleanPreferencesKey("pref_sort_reverse${musicSet.id}")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }

    suspend fun setSelectableFoldersSortStyle(sortStyle: String) {
        val key = stringPreferencesKey("pref_select_folder_sort_style")
        dataStore.edit { preferences ->
            preferences[key] = sortStyle
        }
    }

    suspend fun setSelectableFoldersSortReversed(reversed: Boolean) {
        val key = booleanPreferencesKey("pref_select_folder_sort_reverse")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }

    suspend fun setSelectableTracksSortStyle(sortStyle: String) {
        val key = stringPreferencesKey("pref_music_select_sort_style")
        dataStore.edit { preferences ->
            preferences[key] = sortStyle
        }
    }

    suspend fun setSelectableTracksSortReverse(reversed: Boolean) {
        val key = booleanPreferencesKey("pref_select_sort_reverse")
        dataStore.edit { preferences ->
            preferences[key] = reversed
        }
    }
}
