package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.ContextMenuItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.selects.select

class SortByContextMenu(
    context: Context,
    private val musicSet: MusicSet,
    private val selectionMode: Boolean = false,
    private val onSortChanged: ((String, Boolean) -> Unit)? = null
) : BaseContextMenu(context) {

    private val preferenceUtil: PreferenceUtil =
        context.appDependencies.preferenceUtil

    override fun buildItems(): List<ContextMenuItem> {
        val options = musicSet.sortOptions()

        if (options.isEmpty()) {
            return emptyList()
        }

        return buildList {
            add(
                ContextMenuItem(
                    id = ID_TITLE,
                    titleRes = R.string.sort_by,
                    enabled = false
                )
            )

            options.forEach { option ->
                add(option.toMenuItem())
            }
        }
    }

    override fun onItemClicked(
        item: ContextMenuItem,
        anchor: View
    ) {
        val option = musicSet
            .sortOptions()
            .firstOrNull { option -> option.id == item.id }

        if (option == null) {
            dismiss()
            ToastUtil.show(context, R.string.feature_not_implemented)
            return
        }

        applySort(option)

        dismiss()

        onSortChanged?.invoke(
            currentSortStyle(),
            isCurrentSortReversed()
        )
    }

    private fun SortOption.toMenuItem(): ContextMenuItem {
        return ContextMenuItem(
            id = id,
            titleRes = titleRes,
            rightIconRes = if (isSelected) {
                R.drawable.b_vector_menu_single_selected
            } else {
                null
            },
            selected = isSelected
        )
    }

    private fun MusicSet.sortOptions(): List<SortOption> {
        return when (this) {
            is MusicSet.TrackCollection -> trackSortOptions(this)
            is MusicSet.Artists -> artistSortOptions()
            is MusicSet.Albums -> albumSortOptions()
            is MusicSet.Genres -> genreSortOptions()
            is MusicSet.Playlists -> playlistSortOptions()
            is MusicSet.Folders -> folderSortOptions()
            else -> emptyList()
        }
    }

    private fun trackSortOptions(musicSet: MusicSet): List<SortOption> {
        val selectedStyle = preferenceUtil.getSortStyle(
            musicSet = musicSet,
            selectionMode = selectionMode
        )

        val reversed = preferenceUtil.isSortReversed(
            musicSet = musicSet,
            selectionMode = selectionMode
        )

        return listOf(
            SortOption(
                id = ID_A_Z,
                titleRes = R.string.sort_title,
                style = SORT_NAME,
                reversed = false,
                isSelected = selectedStyle == SORT_NAME && !reversed
            ),
            SortOption(
                id = ID_Z_A,
                titleRes = R.string.sort_title_reverse,
                style = SORT_NAME,
                reversed = true,
                isSelected = selectedStyle == SORT_NAME && reversed
            ),
            SortOption(
                id = ID_YEAR,
                titleRes = R.string.sort_year,
                style = SORT_YEAR,
                reversed = false,
                isSelected = selectedStyle == SORT_YEAR
            ),
            SortOption(
                id = ID_ARTIST,
                titleRes = R.string.sort_artist,
                style = SORT_ARTIST,
                reversed = false,
                isSelected = selectedStyle == SORT_ARTIST
            ),
            SortOption(
                id = ID_ALBUM,
                titleRes = R.string.sort_album,
                style = SORT_ALBUM,
                reversed = false,
                isSelected = selectedStyle == SORT_ALBUM
            ),
            SortOption(
                id = ID_FOLDER,
                titleRes = R.string.sort_folder,
                style = SORT_FOLDER,
                reversed = false,
                isSelected = selectedStyle == SORT_FOLDER
            ),
            SortOption(
                id = ID_DATE_ADDED,
                titleRes = R.string.sort_add_time,
                style = SORT_DATE,
                reversed = false,
                isSelected = selectedStyle == SORT_DATE
            ),
            SortOption(
                id = ID_SIZE,
                titleRes = R.string.sort_size,
                style = SORT_SIZE,
                reversed = false,
                isSelected = selectedStyle == SORT_SIZE
            ),
            SortOption(
                id = ID_DURATION,
                titleRes = R.string.sort_duration,
                style = SORT_DURATION,
                reversed = false,
                isSelected = selectedStyle == SORT_DURATION
            ),
            SortOption(
                id = ID_RANDOM,
                titleRes = R.string.sort_random,
                style = SORT_RANDOM,
                reversed = false,
                isSelected = selectedStyle == SORT_RANDOM
            ),
            SortOption(
                id = ID_REVERSE,
                titleRes = R.string.sort_reverse,
                style = selectedStyle,
                reversed = !reversed,
                isSelected = false,
                isReverseToggle = true
            )
        )
    }

    private fun artistSortOptions(): List<SortOption> {
        val selectedStyle = preferenceUtil.getArtistSortStyle()
        val reversed = preferenceUtil.isArtistSortReversed()

        return listOf(
            SortOption(
                id = ID_A_Z,
                titleRes = R.string.sort_title,
                style = SORT_NAME,
                reversed = false,
                isSelected = selectedStyle == SORT_NAME && !reversed
            ),
            SortOption(
                id = ID_Z_A,
                titleRes = R.string.sort_title_reverse,
                style = SORT_NAME,
                reversed = true,
                isSelected = selectedStyle == SORT_NAME && reversed
            ),
            SortOption(
                id = ID_TRACK_COUNT,
                titleRes = R.string.sort_track_number,
                style = SORT_MUSIC_COUNT,
                reversed = false,
                isSelected = selectedStyle == SORT_MUSIC_COUNT
            ),
            SortOption(
                id = ID_ALBUM_COUNT,
                titleRes = R.string.sort_album_number,
                style = SORT_ALBUM_COUNT,
                reversed = false,
                isSelected = selectedStyle == SORT_ALBUM_COUNT
            ),
            SortOption(
                id = ID_REVERSE_ALL,
                titleRes = R.string.sort_reverse_all,
                style = selectedStyle,
                reversed = !reversed,
                isSelected = false,
                isReverseToggle = true
            )
        )
    }

    private fun albumSortOptions(): List<SortOption> {
        val selectedStyle = preferenceUtil.getAlbumSortStyle()
        val reversed = preferenceUtil.isAlbumSortReversed()

        return listOf(
            SortOption(
                id = ID_A_Z,
                titleRes = R.string.sort_title,
                style = SORT_NAME,
                reversed = false,
                isSelected = selectedStyle == SORT_NAME && !reversed
            ),
            SortOption(
                id = ID_Z_A,
                titleRes = R.string.sort_title_reverse,
                style = SORT_NAME,
                reversed = true,
                isSelected = selectedStyle == SORT_NAME && reversed
            ),
            SortOption(
                id = ID_YEAR,
                titleRes = R.string.sort_year,
                style = SORT_YEAR,
                reversed = false,
                isSelected = selectedStyle == SORT_YEAR
            ),
            SortOption(
                id = ID_ARTIST,
                titleRes = R.string.sort_artist,
                style = SORT_ARTIST,
                reversed = false,
                isSelected = selectedStyle == SORT_ARTIST
            ),
            SortOption(
                id = ID_TRACK_COUNT,
                titleRes = R.string.sort_track_number,
                style = SORT_MUSIC_COUNT,
                reversed = false,
                isSelected = selectedStyle == SORT_MUSIC_COUNT
            ),
            SortOption(
                id = ID_DATE_ADDED,
                titleRes = R.string.sort_add_time,
                style = SORT_DATE,
                reversed = false,
                isSelected = selectedStyle == SORT_DATE
            ),
            SortOption(
                id = ID_REVERSE_ALL,
                titleRes = R.string.sort_reverse_all,
                style = selectedStyle,
                reversed = !reversed,
                isSelected = false,
                isReverseToggle = true
            )
        )
    }

    private fun genreSortOptions(): List<SortOption> {
        val selectedStyle = preferenceUtil.getGenreSortStyle()
        val reversed = preferenceUtil.isGenreSortReversed()

        return listOf(
            SortOption(
                id = ID_A_Z,
                titleRes = R.string.sort_title,
                style = SORT_NAME,
                reversed = false,
                isSelected = selectedStyle == SORT_NAME && !reversed
            ),
            SortOption(
                id = ID_Z_A,
                titleRes = R.string.sort_title_reverse,
                style = SORT_NAME,
                reversed = true,
                isSelected = selectedStyle == SORT_NAME && reversed
            ),
            SortOption(
                id = ID_TRACK_COUNT,
                titleRes = R.string.sort_track_number,
                style = SORT_MUSIC_COUNT,
                reversed = false,
                isSelected = selectedStyle == SORT_MUSIC_COUNT
            ),
            SortOption(
                id = ID_REVERSE_ALL,
                titleRes = R.string.sort_reverse_all,
                style = selectedStyle,
                reversed = !reversed,
                isSelected = false,
                isReverseToggle = true
            )
        )
    }

    private fun playlistSortOptions(): List<SortOption> {
        val selectedStyle = preferenceUtil.getPlaylistSortStyle()
        val reversed = preferenceUtil.isPlaylistSortReversed()

        return listOf(
            SortOption(
                id = ID_DEFAULT,
                titleRes = R.string.sort_default,
                style = SORT_DEFAULT,
                reversed = false,
                isSelected = selectedStyle == SORT_DEFAULT
            ),
            SortOption(
                id = ID_A_Z,
                titleRes = R.string.sort_title,
                style = SORT_NAME,
                reversed = false,
                isSelected = selectedStyle == SORT_NAME && !reversed
            ),
            SortOption(
                id = ID_Z_A,
                titleRes = R.string.sort_title_reverse,
                style = SORT_NAME,
                reversed = true,
                isSelected = selectedStyle == SORT_NAME && reversed
            ),
            SortOption(
                id = ID_DATE_ADDED,
                titleRes = R.string.sort_add_time,
                style = SORT_DATE,
                reversed = false,
                isSelected = selectedStyle == SORT_DATE
            ),
            SortOption(
                id = ID_TRACK_COUNT,
                titleRes = R.string.sort_track_number,
                style = SORT_AMOUNT,
                reversed = false,
                isSelected = selectedStyle == SORT_AMOUNT
            ),
            SortOption(
                id = ID_REVERSE_ALL,
                titleRes = R.string.sort_reverse_all,
                style = selectedStyle,
                reversed = !reversed,
                isSelected = false,
                isReverseToggle = true
            )
        )
    }

    private fun folderSortOptions(): List<SortOption> {
        val selectedStyle = preferenceUtil.getFolderSortStyle(selectionMode)
        val reversed = preferenceUtil.isFolderSortReversed(selectionMode)

        return listOf(
            SortOption(
                id = ID_A_Z,
                titleRes = R.string.sort_title,
                style = SORT_NAME,
                reversed = false,
                isSelected = selectedStyle == SORT_NAME && !reversed
            ),
            SortOption(
                id = ID_Z_A,
                titleRes = R.string.sort_title_reverse,
                style = SORT_NAME,
                reversed = true,
                isSelected = selectedStyle == SORT_NAME && reversed
            ),
            SortOption(
                id = ID_TRACK_COUNT,
                titleRes = R.string.sort_track_number,
                style = SORT_MUSIC_COUNT,
                reversed = false,
                isSelected = selectedStyle == SORT_MUSIC_COUNT
            ),
            SortOption(
                id = ID_DATE_ADDED,
                titleRes = R.string.sort_add_time,
                style = SORT_DATE,
                reversed = false,
                isSelected = selectedStyle == SORT_DATE
            ),
            SortOption(
                id = ID_REVERSE_ALL,
                titleRes = R.string.sort_reverse_all,
                style = selectedStyle,
                reversed = !reversed,
                isSelected = false,
                isReverseToggle = true
            )
        )
    }

    private fun applySort(option: SortOption) {
        when (musicSet) {
            is MusicSet.TrackCollection -> {
                preferenceUtil.setSortStyle(
                    musicSet = musicSet,
                    style = option.style,
                    selectionMode = selectionMode
                )

                preferenceUtil.setSortReversed(
                    musicSet = musicSet,
                    reversed = option.reversed,
                    selectionMode = selectionMode
                )
            }

            is MusicSet.Artists -> {
                preferenceUtil.setArtistSortStyle(option.style)
                preferenceUtil.setArtistSortReversed(option.reversed)
            }

            is MusicSet.Albums -> {
                preferenceUtil.setAlbumSortStyle(option.style)
                preferenceUtil.setAlbumSortReversed(option.reversed)
            }

            is MusicSet.Genres -> {
                preferenceUtil.setGenreSortStyle(option.style)
                preferenceUtil.setGenreSortReversed(option.reversed)
            }

            is MusicSet.Playlists -> {
                preferenceUtil.setPlaylistSortStyle(option.style)
                preferenceUtil.setPlaylistSortReversed(option.reversed)
            }

            is MusicSet.Folders -> {
                preferenceUtil.setFolderSortStyle(
                    style = option.style,
                    selectionMode = selectionMode
                )

                preferenceUtil.setFolderSortReversed(
                    reversed = option.reversed,
                    selectionMode = selectionMode
                )
            }

            else -> Unit
        }
    }

    private fun currentSortStyle(): String {
        return when (musicSet) {
            is MusicSet.TrackCollection -> {
                preferenceUtil.getSortStyle(
                    musicSet = musicSet,
                    selectionMode = selectionMode
                )
            }

            is MusicSet.Artists -> preferenceUtil.getArtistSortStyle()
            is MusicSet.Albums -> preferenceUtil.getAlbumSortStyle()
            is MusicSet.Genres -> preferenceUtil.getGenreSortStyle()
            is MusicSet.Playlists -> preferenceUtil.getPlaylistSortStyle()
            is MusicSet.Folders -> preferenceUtil.getFolderSortStyle(selectionMode)
            else -> ""
        }
    }

    private fun isCurrentSortReversed(): Boolean {
        return when (musicSet) {
            is MusicSet.TrackCollection -> {
                preferenceUtil.isSortReversed(
                    musicSet = musicSet,
                    selectionMode = selectionMode
                )
            }

            is MusicSet.Artists -> preferenceUtil.isArtistSortReversed()
            is MusicSet.Albums -> preferenceUtil.isAlbumSortReversed()
            is MusicSet.Genres -> preferenceUtil.isGenreSortReversed()
            is MusicSet.Playlists -> preferenceUtil.isPlaylistSortReversed()
            is MusicSet.Folders -> preferenceUtil.isFolderSortReversed(selectionMode)
            else -> false
        }
    }

    private data class SortOption(
        val id: String,
        val titleRes: Int,
        val style: String,
        val reversed: Boolean,
        val isSelected: Boolean,
        val isReverseToggle: Boolean = false
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

        private const val SORT_DEFAULT = "default"
        private const val SORT_NAME = "name"
        private const val SORT_YEAR = "year"
        private const val SORT_ARTIST = "artist"
        private const val SORT_ALBUM = "album"
        private const val SORT_FOLDER = "folder"
        private const val SORT_DATE = "date"
        private const val SORT_SIZE = "size"
        private const val SORT_DURATION = "duration"
        private const val SORT_RANDOM = "random"
        private const val SORT_MUSIC_COUNT = "music_count"
        private const val SORT_ALBUM_COUNT = "album_count"
        private const val SORT_AMOUNT = "amount"
    }
}