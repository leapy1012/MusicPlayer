package gd.app.musicplayer.ui.feature.menu

import android.content.Context
import android.view.View
import android.widget.Toast
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ContextMenuItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.core.ui.extension.appContainer

class SortByContextMenu(
    private val context: Context,
    private val musicSet: MusicSet,
    private val selectionMode: Boolean = false,
    private val onSortChanged: ((String, Boolean) -> Unit)? = null
) : BaseContextMenu(context) {
    private val preferenceUtil: PreferenceUtil = context.appContainer.preferenceUtil

    override fun buildItems(): List<ContextMenuItem> {
        return listOf(
            ContextMenuItem(
                id = ID_TITLE,
                titleRes = R.string.sort_by,
                enabled = false
            )
        ) + musicSet.buildSortItems(preferenceUtil)
    }

    override fun onItemClicked(item: ContextMenuItem, anchor: View) {
        if (!applySort(preferenceUtil, item.id)) {
            dismiss()
            Toast.makeText(context, R.string.feature_not_implemented, Toast.LENGTH_SHORT).show()
            return
        }

        dismiss()
        onSortChanged?.invoke(
            currentSortStyle(preferenceUtil),
            isCurrentSortReversed(preferenceUtil)
        )
    }

    private fun MusicSet.buildSortItems(preferenceUtil: PreferenceUtil): List<ContextMenuItem> =
        when (this) {
            is MusicSet.TrackCollection -> trackSortItems(preferenceUtil, this)
            is MusicSet.Artists -> artistSortItems(preferenceUtil)
            is MusicSet.Albums -> albumSortItems(preferenceUtil)
            is MusicSet.Genres -> genreSortItems(preferenceUtil)
            is MusicSet.Playlists -> playlistSortItems(preferenceUtil)
            is MusicSet.Folders -> folderSortItems(preferenceUtil)
            else -> emptyList()
        }

    private fun trackSortItems(preferenceUtil: PreferenceUtil, musicSet: MusicSet): List<ContextMenuItem> {
        val selected = preferenceUtil.getSortStyle(musicSet, selectionMode)
        val reversed = preferenceUtil.isSortReversed(musicSet, selectionMode)
        return listOf(
            item(ID_A_Z, R.string.sort_title, selected == "name" && !reversed),
            item(ID_Z_A, R.string.sort_title_reverse, selected == "name" && reversed),
            item(ID_YEAR, R.string.sort_year, selected == "year"),
            item(ID_ARTIST, R.string.sort_artist, selected == "artist"),
            item(ID_ALBUM, R.string.sort_album, selected == "album"),
            item(ID_FOLDER, R.string.sort_folder, selected == "folder"),
            item(ID_DATE_ADDED, R.string.sort_add_time, selected == "date"),
            item(ID_SIZE, R.string.sort_size, selected == "size"),
            item(ID_DURATION, R.string.sort_duration, selected == "duration"),
            item(ID_RANDOM, R.string.sort_random, selected == "random"),
            item(ID_REVERSE, R.string.sort_reverse, false)
        )
    }

    private fun artistSortItems(preferenceUtil: PreferenceUtil): List<ContextMenuItem> {
        val selected = preferenceUtil.getArtistSortStyle()
        val reversed = preferenceUtil.isArtistSortReversed()
        return listOf(
            item(ID_A_Z, R.string.sort_title, selected == "name" && !reversed),
            item(ID_Z_A, R.string.sort_title_reverse, selected == "name" && reversed),
            item(ID_TRACK_COUNT, R.string.sort_track_number, selected == "music_count"),
            item(ID_ALBUM_COUNT, R.string.sort_album_number, selected == "album_count"),
            item(ID_REVERSE_ALL, R.string.sort_reverse_all, false)
        )
    }

    private fun albumSortItems(preferenceUtil: PreferenceUtil): List<ContextMenuItem> {
        val selected = preferenceUtil.getAlbumSortStyle()
        val reversed = preferenceUtil.isAlbumSortReversed()
        return listOf(
            item(ID_A_Z, R.string.sort_title, selected == "name" && !reversed),
            item(ID_Z_A, R.string.sort_title_reverse, selected == "name" && reversed),
            item(ID_YEAR, R.string.sort_year, selected == "year"),
            item(ID_ARTIST, R.string.sort_artist, selected == "artist"),
            item(ID_TRACK_COUNT, R.string.sort_track_number, selected == "music_count"),
            item(ID_DATE_ADDED, R.string.sort_add_time, selected == "date"),
            item(ID_REVERSE_ALL, R.string.sort_reverse_all, false)
        )
    }

    private fun genreSortItems(preferenceUtil: PreferenceUtil): List<ContextMenuItem> {
        val selected = preferenceUtil.getGenreSortStyle()
        val reversed = preferenceUtil.isGenreSortReversed()
        return listOf(
            item(ID_A_Z, R.string.sort_title, selected == "name" && !reversed),
            item(ID_Z_A, R.string.sort_title_reverse, selected == "name" && reversed),
            item(ID_TRACK_COUNT, R.string.sort_track_number, selected == "music_count"),
            item(ID_REVERSE_ALL, R.string.sort_reverse_all, false)
        )
    }

    private fun playlistSortItems(preferenceUtil: PreferenceUtil): List<ContextMenuItem> {
        val selected = preferenceUtil.getPlaylistSortStyle()
        val reversed = preferenceUtil.isPlaylistSortReversed()
        return listOf(
            item(ID_DEFAULT, R.string.sort_default, selected == "default"),
            item(ID_A_Z, R.string.sort_title, selected == "name" && !reversed),
            item(ID_Z_A, R.string.sort_title_reverse, selected == "name" && reversed),
            item(ID_DATE_ADDED, R.string.sort_add_time, selected == "date"),
            item(ID_TRACK_COUNT, R.string.sort_track_number, selected == "amount"),
            item(ID_REVERSE_ALL, R.string.sort_reverse_all, false)
        )
    }

    private fun folderSortItems(preferenceUtil: PreferenceUtil): List<ContextMenuItem> {
        val selected = preferenceUtil.getFolderSortStyle(selectionMode)
        val reversed = preferenceUtil.isFolderSortReversed(selectionMode)
        return listOf(
            item(ID_A_Z, R.string.sort_title, selected == "name" && !reversed),
            item(ID_Z_A, R.string.sort_title_reverse, selected == "name" && reversed),
            item(ID_TRACK_COUNT, R.string.sort_track_number, selected == "music_count"),
            item(ID_DATE_ADDED, R.string.sort_add_time, selected == "date"),
            item(ID_REVERSE_ALL, R.string.sort_reverse_all, false)
        )
    }

    private fun currentSortStyle(preferenceUtil: PreferenceUtil): String {
        return when (musicSet) {
            is MusicSet.TrackCollection -> preferenceUtil.getSortStyle(musicSet, selectionMode)
            is MusicSet.Artists -> preferenceUtil.getArtistSortStyle()
            is MusicSet.Albums -> preferenceUtil.getAlbumSortStyle()
            is MusicSet.Genres -> preferenceUtil.getGenreSortStyle()
            is MusicSet.Playlists -> preferenceUtil.getPlaylistSortStyle()
            is MusicSet.Folders -> preferenceUtil.getFolderSortStyle(selectionMode)
            else -> ""
        }
    }

    private fun isCurrentSortReversed(preferenceUtil: PreferenceUtil): Boolean {
        return when (musicSet) {
            is MusicSet.TrackCollection -> preferenceUtil.isSortReversed(musicSet, selectionMode)
            is MusicSet.Artists -> preferenceUtil.isArtistSortReversed()
            is MusicSet.Albums -> preferenceUtil.isAlbumSortReversed()
            is MusicSet.Genres -> preferenceUtil.isGenreSortReversed()
            is MusicSet.Playlists -> preferenceUtil.isPlaylistSortReversed()
            is MusicSet.Folders -> preferenceUtil.isFolderSortReversed(selectionMode)
            else -> false
        }
    }

    private fun applySort(preferenceUtil: PreferenceUtil, itemId: String): Boolean {
        return when (musicSet) {
            is MusicSet.TrackCollection -> applyTrackSort(preferenceUtil, musicSet, itemId)
            is MusicSet.Artists -> applyArtistSort(preferenceUtil, itemId)
            is MusicSet.Albums -> applyAlbumSort(preferenceUtil, itemId)
            is MusicSet.Genres -> applyGenreSort(preferenceUtil, itemId)
            is MusicSet.Playlists -> applyPlaylistSort(preferenceUtil, itemId)
            is MusicSet.Folders -> applyFoldersSort(preferenceUtil, itemId)
            else -> false
        }
    }

    private fun applyTrackSort(
        preferenceUtil: PreferenceUtil,
        musicSet: MusicSet,
        itemId: String
    ): Boolean {
        when (itemId) {
            ID_A_Z -> {
                preferenceUtil.setSortStyle(musicSet, "name", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }

            ID_Z_A -> {
                preferenceUtil.setSortStyle(musicSet, "name", selectionMode)
                preferenceUtil.setSortReversed(musicSet, true, selectionMode)
            }

            ID_YEAR -> {
                preferenceUtil.setSortStyle(musicSet, "year", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }
            ID_ARTIST -> {
                preferenceUtil.setSortStyle(musicSet, "artist", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }
            ID_ALBUM -> {
                preferenceUtil.setSortStyle(musicSet, "album", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }
            ID_FOLDER -> {
                preferenceUtil.setSortStyle(musicSet, "folder", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }
            ID_DATE_ADDED -> {
                preferenceUtil.setSortStyle(musicSet, "date", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }
            ID_SIZE -> {
                preferenceUtil.setSortStyle(musicSet, "size", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }
            ID_DURATION -> {
                preferenceUtil.setSortStyle(musicSet, "duration", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }

            ID_RANDOM -> {
                preferenceUtil.setSortStyle(musicSet, "random", selectionMode)
                preferenceUtil.setSortReversed(musicSet, false, selectionMode)
            }

            ID_REVERSE -> preferenceUtil.setSortReversed(
                musicSet,
                !preferenceUtil.isSortReversed(musicSet, selectionMode),
                selectionMode
            )

            else -> return false
        }
        return true
    }

    private fun applyArtistSort(preferenceUtil: PreferenceUtil, itemId: String): Boolean {
        when (itemId) {
            ID_A_Z -> {
                preferenceUtil.setArtistSortStyle("name")
                preferenceUtil.setArtistSortReversed(false)
            }

            ID_Z_A -> {
                preferenceUtil.setArtistSortStyle("name")
                preferenceUtil.setArtistSortReversed(true)
            }

            ID_TRACK_COUNT -> {
                preferenceUtil.setArtistSortStyle("music_count")
                preferenceUtil.setArtistSortReversed(false)
            }
            ID_ALBUM_COUNT -> {
                preferenceUtil.setArtistSortStyle("album_count")
                preferenceUtil.setArtistSortReversed(false)
            }
            ID_REVERSE_ALL -> preferenceUtil.setArtistSortReversed(
                !preferenceUtil.isArtistSortReversed()
            )

            else -> return false
        }
        return true
    }

    private fun applyAlbumSort(preferenceUtil: PreferenceUtil, itemId: String): Boolean {
        when (itemId) {
            ID_A_Z -> {
                preferenceUtil.setAlbumSortStyle("name")
                preferenceUtil.setAlbumSortReversed(false)
            }

            ID_Z_A -> {
                preferenceUtil.setAlbumSortStyle("name")
                preferenceUtil.setAlbumSortReversed(true)
            }

            ID_YEAR -> {
                preferenceUtil.setAlbumSortStyle("year")
                preferenceUtil.setAlbumSortReversed(false)
            }
            ID_ARTIST -> {
                preferenceUtil.setAlbumSortStyle("artist")
                preferenceUtil.setAlbumSortReversed(false)
            }
            ID_TRACK_COUNT -> {
                preferenceUtil.setAlbumSortStyle("music_count")
                preferenceUtil.setAlbumSortReversed(false)
            }
            ID_DATE_ADDED -> {
                preferenceUtil.setAlbumSortStyle("date")
                preferenceUtil.setAlbumSortReversed(false)
            }
            ID_REVERSE_ALL -> preferenceUtil.setAlbumSortReversed(
                !preferenceUtil.isAlbumSortReversed()
            )

            else -> return false
        }
        return true
    }

    private fun applyGenreSort(
        preferenceUtil: PreferenceUtil,
        itemId: String
    ): Boolean {
        when (itemId) {
            ID_A_Z -> {
                preferenceUtil.setGenreSortStyle("name")
                preferenceUtil.setGenreSortReversed(false)
            }

            ID_Z_A -> {
                preferenceUtil.setGenreSortStyle("name")
                preferenceUtil.setGenreSortReversed(true)
            }

            ID_TRACK_COUNT -> {
                preferenceUtil.setGenreSortStyle("music_count")
                preferenceUtil.setGenreSortReversed(false)
            }
            ID_REVERSE_ALL -> preferenceUtil.setGenreSortReversed(
                !preferenceUtil.isGenreSortReversed()
            )

            else -> return false
        }
        return true
    }

    private fun applyPlaylistSort(preferenceUtil: PreferenceUtil, itemId: String): Boolean {
        when (itemId) {
            ID_DEFAULT -> {
                preferenceUtil.setPlaylistSortStyle("default")
                preferenceUtil.setPlaylistSortReversed(false)
            }

            ID_A_Z -> {
                preferenceUtil.setPlaylistSortStyle("name")
                preferenceUtil.setPlaylistSortReversed(false)
            }

            ID_Z_A -> {
                preferenceUtil.setPlaylistSortStyle("name")
                preferenceUtil.setPlaylistSortReversed(true)
            }

            ID_DATE_ADDED -> {
                preferenceUtil.setPlaylistSortStyle("date")
                preferenceUtil.setPlaylistSortReversed(false)
            }

            ID_TRACK_COUNT -> {
                preferenceUtil.setPlaylistSortStyle("amount")
                preferenceUtil.setPlaylistSortReversed(false)
            }

            ID_REVERSE_ALL -> preferenceUtil.setPlaylistSortReversed(
                !preferenceUtil.isPlaylistSortReversed()
            )

            else -> return false
        }
        return true
    }

    private fun applyFoldersSort(preferenceUtil: PreferenceUtil, itemId: String): Boolean {
        when (itemId) {
            ID_A_Z -> {
                preferenceUtil.setFolderSortStyle("name", selectionMode)
                preferenceUtil.setFolderSortReversed(reversed = false, selectionMode = selectionMode)
            }

            ID_Z_A -> {
                preferenceUtil.setFolderSortStyle("name", selectionMode)
                preferenceUtil.setFolderSortReversed(reversed = true, selectionMode = selectionMode)
            }

            ID_DATE_ADDED -> {
                preferenceUtil.setFolderSortStyle("date", selectionMode)
                preferenceUtil.setFolderSortReversed(reversed = false, selectionMode = selectionMode)
            }

            ID_TRACK_COUNT -> {
                preferenceUtil.setFolderSortStyle("music_count", selectionMode)
                preferenceUtil.setFolderSortReversed(false, selectionMode = selectionMode)
            }

            ID_REVERSE_ALL -> preferenceUtil.setFolderSortReversed(
                !preferenceUtil.isFolderSortReversed(selectionMode), selectionMode
            )

            else -> return false
        }
        return true
    }

    private fun item(id: String, titleRes: Int, selected: Boolean): ContextMenuItem =
        ContextMenuItem(
            id = id,
            titleRes = titleRes,
            rightIconRes = if (selected) R.drawable.b_vector_menu_single_selected else null,
            selected = selected
        )

    private companion object {
        private const val ID_DEFAULT = "default"
        private const val ID_TITLE = "title"
        private const val ID_A_Z = "a_z"
        private const val ID_Z_A = "z_a"
        private const val ID_YEAR = "year"
        private const val ID_ARTIST = "artist"
        private const val ID_ALBUM = "album"
        private const val ID_FOLDER = "folder"
        private const val ID_DATE_ADDED = "date_added"
        private const val ID_SIZE = "size"
        private const val ID_DURATION = "duration"
        private const val ID_RANDOM = "random"
        private const val ID_REVERSE = "reverse"
        private const val ID_TRACK_COUNT = "track_count"
        private const val ID_ALBUM_COUNT = "album_count"
        private const val ID_REVERSE_ALL = "reverse_all"
    }
}
