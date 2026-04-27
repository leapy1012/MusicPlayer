package gd.app.musicplayer.util

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet

interface SortPreferenceOps : PreferenceAccess {

    fun getListViewMode(tabId: Int): Int =
        getIntPreference("preference_view_as$tabId", 0)

    fun setListViewMode(tabId: Int, mode: Int) {
        putIntPreference("preference_view_as$tabId", mode)
    }

    fun clearMusicSelectionSortState(pageId: Int) {
        removePreferences(
            keySortStyle(pageId, true),
            keySortStyle(pageId, false),
            keySortReverse(pageId, true),
            keySortReverse(pageId, false)
        )
    }

    fun isAlbumSortReversed(): Boolean =
        getBooleanPreference(KEY_ALBUM_SORT_REVERSE, false)

    fun setAlbumSortReversed(reversed: Boolean) {
        putBooleanPreference(KEY_ALBUM_SORT_REVERSE, reversed)
    }

    fun getAlbumSortStyle(): String =
        getStringPreference(KEY_ALBUM_SORT_STYLE, "name")

    fun setAlbumSortStyle(style: String) {
        putStringPreference(KEY_ALBUM_SORT_STYLE, style)
    }

    fun isGenreSortReversed(): Boolean =
        getBooleanPreference(KEY_GENRE_SORT_REVERSE, false)

    fun setGenreSortReversed(reversed: Boolean) {
        putBooleanPreference(KEY_GENRE_SORT_REVERSE, reversed)
    }

    fun getGenreSortStyle(): String =
        getStringPreference(KEY_GENRE_SORT_STYLE,"name")

    fun setGenreSortStyle(style: String) {
        putStringPreference(KEY_GENRE_SORT_STYLE, style)
    }

    fun isArtistSortReversed(): Boolean =
        getBooleanPreference(KEY_ARTIST_SORT_REVERSE, false)

    fun setArtistSortReversed(reversed: Boolean) {
        putBooleanPreference(KEY_ARTIST_SORT_REVERSE, reversed)
    }

    fun getArtistSortStyle(): String =
        getStringPreference(KEY_ARTIST_SORT_STYLE, "name")

    fun setArtistSortStyle(style: String) {
        putStringPreference(KEY_ARTIST_SORT_STYLE, style)
    }

    fun isPlaylistSortReversed(): Boolean =
        getBooleanPreference(KEY_PLAYLIST_SORT_REVERSE, false)

    fun setPlaylistSortReversed(reversed: Boolean) {
        putBooleanPreference(KEY_PLAYLIST_SORT_REVERSE, reversed)
    }

    fun getPlaylistSortStyle(): String =
        getStringPreference(KEY_PLAYLIST_SORT_STYLE, "default")

    fun setPlaylistSortStyle(style: String) {
        putStringPreference(KEY_PLAYLIST_SORT_STYLE, style)
    }

    fun isFolderSortReversed(selectionMode: Boolean = false): Boolean =
        getBooleanPreference(keyFolderSortReverse(selectionMode), false)

    fun setFolderSortReversed(reversed: Boolean, selectionMode: Boolean) {
        putBooleanPreference(keyFolderSortReverse(selectionMode), reversed)
    }

    fun getFolderSortStyle(selectionMode: Boolean): String =
        getStringPreference(
            if (selectionMode) KEY_SELECTED_FOLDER_SORT_STYLE else KEY_FOLDER_SORT_STYLE,
            "name"
        )

    fun setFolderSortStyle(style: String, selectionMode: Boolean) {
        putStringPreference(
            if (selectionMode) KEY_SELECTED_FOLDER_SORT_STYLE else KEY_FOLDER_SORT_STYLE,
            style
        )
    }

//    fun isSortReversed(musicSetId: Int): Boolean =
//        isSortReversed(musicSetId, false)

    fun isSortReversed(musicSet: MusicSet, selectionMode: Boolean): Boolean {

        val tabId = when(musicSet) {
            is MusicSet.Folder -> MusicSet.FOLDERS_ID.toInt()
            is MusicSet.Genre -> MusicSet.GENRES_ID.toInt()
            is MusicSet.Artist -> MusicSet.ARTISTS_ID.toInt()
            is MusicSet.Album -> MusicSet.ALBUMS_ID.toInt()
            is MusicSet.Tracks -> MusicSet.TRACKS_ID.toInt()
            else -> musicSet.id.toInt()
        }

        return getBooleanPreference(keySortReverse(tabId, selectionMode), false)
    }

    fun setSortReversed(musicSet: MusicSet, reversed: Boolean, selectionMode: Boolean) {
        val tabId = when(musicSet) {
            is MusicSet.Folder -> MusicSet.FOLDERS_ID.toInt()
            is MusicSet.Genre -> MusicSet.GENRES_ID.toInt()
            is MusicSet.Artist -> MusicSet.ARTISTS_ID.toInt()
            is MusicSet.Album -> MusicSet.ALBUMS_ID.toInt()
            is MusicSet.Tracks -> MusicSet.TRACKS_ID.toInt()
            else -> musicSet.id.toInt()
        }
        putBooleanPreference(keySortReverse(tabId, selectionMode), reversed)
    }

    fun getSortStyle(musicSet: MusicSet): String =
        getSortStyle(musicSet, false)

    fun getSortStyle(musicSet: MusicSet, selectionMode: Boolean): String {
        val tabId = when(musicSet) {
            is MusicSet.Folder -> MusicSet.FOLDERS_ID.toInt()
            is MusicSet.Genre -> MusicSet.GENRES_ID.toInt()
            is MusicSet.Artist -> MusicSet.ARTISTS_ID.toInt()
            is MusicSet.Album -> MusicSet.ALBUMS_ID.toInt()
            is MusicSet.Tracks -> MusicSet.TRACKS_ID.toInt()
            else -> musicSet.id.toInt()
        }

        val defaultValue = when {
            musicSet is MusicSet.RecentlyAdded -> "date"
            else -> "name"
        }

        return getStringPreference(keySortStyle(tabId, selectionMode), defaultValue)
    }

    fun setSortStyle(musicSet: MusicSet, style: String) {
        setSortStyle(musicSet, style, false)
    }

    fun setSortStyle(musicSet: MusicSet, style: String, selectionMode: Boolean) {
        val tabId = when(musicSet) {
            is MusicSet.Folder -> MusicSet.FOLDERS_ID.toInt()
            is MusicSet.Genre -> MusicSet.GENRES_ID.toInt()
            is MusicSet.Artist -> MusicSet.ARTISTS_ID.toInt()
            is MusicSet.Album -> MusicSet.ALBUMS_ID.toInt()
            is MusicSet.Tracks -> MusicSet.TRACKS_ID.toInt()
            else -> musicSet.id.toInt()
        }
        putStringPreference(keySortStyle(tabId, selectionMode), style)
    }

    fun getCurrentTabId(): Int =
        getIntPreference(KEY_TAB_ID, 0)

    fun setCurrentTabId(tabId: Int) {
        putIntPreference(KEY_TAB_ID, tabId)
    }

    fun getCurrentTabTag(): String? =
        getNullableStringPreference(KEY_TAB)

    fun setCurrentTabTag(tag: String) {
        putStringPreference(KEY_TAB, tag)
    }

    fun isShowShuffleButtonEnabled(tabId: Int): Boolean =
        getBooleanPreference("preference_show_shuffle_button_$tabId", true)

    fun setShowShuffleButtonEnabled(tabId: Int, enabled: Boolean) {
        putBooleanPreference("preference_show_shuffle_button_$tabId", enabled)
    }

    private fun keyFolderSortReverse(selectionMode: Boolean): String =
        if (selectionMode) KEY_SELECTED_FOLDER_SORT_REVERSE else KEY_FOLDER_SORT_REVERSE

    private fun keySortReverse(tabId: Int, selectionMode: Boolean): String =
        if (selectionMode) KEY_SELECTED_SORT_REVERSE else "pref_sort_reverse$tabId"

    private fun keySortStyle(tabId: Int, selectionMode: Boolean): String =
        if (selectionMode) KEY_SELECTED_MUSIC_SORT_STYLE else "pref_sort_style$tabId"

    companion object {
        const val KEY_SELECTED_FOLDER_SORT_REVERSE = "pref_select_folder_sort_reverse"
        const val KEY_FOLDER_SORT_REVERSE = "pref_folder_sort_reverse"
        const val KEY_SELECTED_FOLDER_SORT_STYLE = "pref_select_folder_sort_style"
        const val KEY_FOLDER_SORT_STYLE = "pref_folder_sort_style"
        const val KEY_ALBUM_SORT_REVERSE = "pref_album_sort_reverse"
        const val KEY_ALBUM_SORT_STYLE = "pref_album_sort_style"
        const val KEY_GENRE_SORT_STYLE = "genre_sort"
        const val KEY_GENRE_SORT_REVERSE = "genre_sort_reverse"
        const val KEY_ARTIST_SORT_REVERSE = "pref_artist_sort_reverse"
        const val KEY_ARTIST_SORT_STYLE = "pref_artist_sort_style"
        const val KEY_PLAYLIST_SORT_REVERSE = "playlist_sort_reverse"
        const val KEY_PLAYLIST_SORT_STYLE = "playlist_sort"
        const val KEY_SELECTED_SORT_REVERSE = "pref_select_sort_reverse"
        const val KEY_SELECTED_MUSIC_SORT_STYLE = "pref_music_select_sort_style"
        const val KEY_TAB_ID = "preference_tab_id"
        const val KEY_TAB = "preference_tab"
    }
}
