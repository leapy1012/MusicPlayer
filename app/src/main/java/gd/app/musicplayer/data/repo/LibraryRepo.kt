package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.LibraryDao
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.util.PreferenceKeys
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class LibraryRepo(
    private val libraryDao: LibraryDao,
    private val preferenceUtil: PreferenceUtil
) {
    data class SavedFolderScrollPosition(
        val position: Int,
        val offset: Int
    )

    fun observeTracks(
        musicSet: MusicSet,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>> {
        val query = LibraryQueryBuilder.buildTrackQuery(
            musicSet = musicSet,
            sortStyle = sortStyle,
            sortDescending = sortDescending,
            smartConfig = preferenceUtil.getSmartPlaylistConfig()
        )
        return libraryDao.observeTracksRaw(query)
    }

    fun observeTracks(musicSet: MusicSet): Flow<List<Music>> =
        observeTracks(
            musicSet = musicSet,
            sortStyle = preferenceUtil.getSortStyle(musicSet),
            sortDescending = preferenceUtil.isSortReversed(musicSet, false)
        )

    fun observeAlbumsByArtist(artist: String): Flow<List<MusicSet.Album>> =
        libraryDao.observeAlbumsByArtistRaw(
            LibraryQueryBuilder.buildAlbumsByArtistQuery(
                artist = artist,
                preferenceUtil = preferenceUtil
            )
        )

    fun observeMusicSets(type: MusicSet): Flow<List<MusicSet>> {
        return when (type) {
            is MusicSet.Artists -> libraryDao.observeArtistsRaw(
                LibraryQueryBuilder.buildArtistsQuery(preferenceUtil)
            )

            is MusicSet.Albums -> libraryDao.observeAlbumsRaw(
                LibraryQueryBuilder.buildAlbumsQuery(preferenceUtil)
            )

            is MusicSet.Genres -> libraryDao.observeGenresRaw(
                LibraryQueryBuilder.buildGenresQuery(preferenceUtil)
            )

            is MusicSet.Folders -> libraryDao.observeFoldersRaw(
                LibraryQueryBuilder.buildFoldersQuery(preferenceUtil)
            )

            else -> flowOf(emptyList())
        }
    }

    fun observePreferenceChanges(): Flow<Unit> =
        preferenceUtil.observePreferenceChanges()

    fun getSortStyle(musicSet: MusicSet): String =
        preferenceUtil.getSortStyle(musicSet)

    fun getListViewMode(musicSet: MusicSet): Int =
        if (musicSet is MusicSet.Folders) {
            0
        } else {
            preferenceUtil.getListViewMode(musicSet.id.toInt())
        }

    fun setListViewMode(musicSet: MusicSet, mode: Int) {
        if (musicSet is MusicSet.Folders) return
        preferenceUtil.setListViewMode(musicSet.id.toInt(), mode)
    }

    fun shouldShowHiddenFoldersEntry(): Boolean =
        preferenceUtil.getBooleanPreference(PreferenceKeys.KEY_SHOW_HIDDEN_FOLDERS, true)

    fun saveFolderScrollPosition(position: Int, offset: Int) {
        preferenceUtil.putIntPreference(KEY_FOLDER_SCROLL_POSITION, position)
        preferenceUtil.putIntPreference(KEY_FOLDER_SCROLL_OFFSET, offset)
    }

    fun getSavedFolderScrollPosition(): SavedFolderScrollPosition? {
        val position = preferenceUtil.getIntPreference(KEY_FOLDER_SCROLL_POSITION, -1)
        if (position < 0) return null
        return SavedFolderScrollPosition(
            position = position,
            offset = preferenceUtil.getIntPreference(KEY_FOLDER_SCROLL_OFFSET, 0)
        )
    }

    private companion object {
        const val KEY_FOLDER_SCROLL_POSITION = "folder_scroll_position"
        const val KEY_FOLDER_SCROLL_OFFSET = "folder_scroll_offset"
    }
}
