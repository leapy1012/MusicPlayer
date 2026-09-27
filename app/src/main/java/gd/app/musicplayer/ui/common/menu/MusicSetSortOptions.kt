package gd.app.musicplayer.ui.common.menu

import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.MusicSet

/**
 * Sort options for library more-menu / selection sort UI.
 */
internal object MusicSetSortOptions {

    data class Option(
        val id: Int,
        val titleRes: Int,
        val style: String,
        val reversed: Boolean,
        val isSelected: Boolean
    )

    fun build(
        musicSet: MusicSet,
        currentSortStyle: String,
        currentSortDescending: Boolean
    ): List<Option> {
        return when (musicSet) {
            is MusicSet.TrackCollection -> trackOptions(currentSortStyle, currentSortDescending)
            is MusicSet.Artists -> artistOptions(currentSortStyle, currentSortDescending)
            is MusicSet.Albums -> albumOptions(currentSortStyle, currentSortDescending)
            is MusicSet.Genres -> genreOptions(currentSortStyle, currentSortDescending)
            is MusicSet.Playlists -> playlistOptions(currentSortStyle, currentSortDescending)
            is MusicSet.Folders -> folderOptions(currentSortStyle, currentSortDescending)
            else -> emptyList()
        }
    }

    fun find(musicSet: MusicSet, id: Int, currentSortStyle: String, currentSortDescending: Boolean): Option? {
        return build(musicSet, currentSortStyle, currentSortDescending).firstOrNull { it.id == id }
    }

    private fun trackOptions(style: String, reversed: Boolean): List<Option> = listOf(
        option(ID_A_Z, R.string.sort_title, SORT_NAME, false, style == SORT_NAME && !reversed),
        option(ID_Z_A, R.string.sort_title_reverse, SORT_NAME, true, style == SORT_NAME && reversed),
        option(ID_YEAR, R.string.sort_year, SORT_YEAR, false, style == SORT_YEAR),
        option(ID_ARTIST, R.string.sort_artist, SORT_ARTIST, false, style == SORT_ARTIST),
        option(ID_ALBUM, R.string.sort_album, SORT_ALBUM, false, style == SORT_ALBUM),
        option(ID_FOLDER, R.string.sort_folder, SORT_FOLDER, false, style == SORT_FOLDER),
        option(ID_DATE_ADDED, R.string.sort_add_time, SORT_DATE, false, style == SORT_DATE),
        option(ID_SIZE, R.string.sort_size, SORT_SIZE, false, style == SORT_SIZE),
        option(ID_DURATION, R.string.sort_duration, SORT_DURATION, false, style == SORT_DURATION),
        option(ID_RANDOM, R.string.sort_random, SORT_RANDOM, false, style == SORT_RANDOM),
        option(ID_REVERSE, R.string.sort_reverse, style, !reversed, false)
    )

    private fun artistOptions(style: String, reversed: Boolean): List<Option> = listOf(
        option(ID_A_Z, R.string.sort_title, SORT_NAME, false, style == SORT_NAME && !reversed),
        option(ID_Z_A, R.string.sort_title_reverse, SORT_NAME, true, style == SORT_NAME && reversed),
        option(ID_TRACK_COUNT, R.string.sort_track_number, SORT_MUSIC_COUNT, false, style == SORT_MUSIC_COUNT),
        option(ID_ALBUM_COUNT, R.string.sort_album_number, SORT_ALBUM_COUNT, false, style == SORT_ALBUM_COUNT),
        option(ID_REVERSE_ALL, R.string.sort_reverse_all, style, !reversed, false)
    )

    private fun albumOptions(style: String, reversed: Boolean): List<Option> = listOf(
        option(ID_A_Z, R.string.sort_title, SORT_NAME, false, style == SORT_NAME && !reversed),
        option(ID_Z_A, R.string.sort_title_reverse, SORT_NAME, true, style == SORT_NAME && reversed),
        option(ID_YEAR, R.string.sort_year, SORT_YEAR, false, style == SORT_YEAR),
        option(ID_ARTIST, R.string.sort_artist, SORT_ARTIST, false, style == SORT_ARTIST),
        option(ID_TRACK_COUNT, R.string.sort_track_number, SORT_MUSIC_COUNT, false, style == SORT_MUSIC_COUNT),
        option(ID_DATE_ADDED, R.string.sort_add_time, SORT_DATE, false, style == SORT_DATE),
        option(ID_REVERSE_ALL, R.string.sort_reverse_all, style, !reversed, false)
    )

    private fun genreOptions(style: String, reversed: Boolean): List<Option> = listOf(
        option(ID_A_Z, R.string.sort_title, SORT_NAME, false, style == SORT_NAME && !reversed),
        option(ID_Z_A, R.string.sort_title_reverse, SORT_NAME, true, style == SORT_NAME && reversed),
        option(ID_TRACK_COUNT, R.string.sort_track_number, SORT_MUSIC_COUNT, false, style == SORT_MUSIC_COUNT),
        option(ID_REVERSE_ALL, R.string.sort_reverse_all, style, !reversed, false)
    )

    private fun playlistOptions(style: String, reversed: Boolean): List<Option> = listOf(
        option(ID_DEFAULT, R.string.sort_default, SORT_DEFAULT, false, style == SORT_DEFAULT),
        option(ID_A_Z, R.string.sort_title, SORT_NAME, false, style == SORT_NAME && !reversed),
        option(ID_Z_A, R.string.sort_title_reverse, SORT_NAME, true, style == SORT_NAME && reversed),
        option(ID_DATE_ADDED, R.string.sort_add_time, SORT_DATE, false, style == SORT_DATE),
        option(ID_TRACK_COUNT, R.string.sort_track_number, SORT_AMOUNT, false, style == SORT_AMOUNT),
        option(ID_REVERSE_ALL, R.string.sort_reverse_all, style, !reversed, false)
    )

    private fun folderOptions(style: String, reversed: Boolean): List<Option> = listOf(
        option(ID_A_Z, R.string.sort_title, SORT_NAME, false, style == SORT_NAME && !reversed),
        option(ID_Z_A, R.string.sort_title_reverse, SORT_NAME, true, style == SORT_NAME && reversed),
        option(ID_TRACK_COUNT, R.string.sort_track_number, SORT_MUSIC_COUNT, false, style == SORT_MUSIC_COUNT),
        option(ID_DATE_ADDED, R.string.sort_add_time, SORT_DATE, false, style == SORT_DATE),
        option(ID_REVERSE_ALL, R.string.sort_reverse_all, style, !reversed, false)
    )

    private fun option(
        id: Int,
        titleRes: Int,
        style: String,
        reversed: Boolean,
        selected: Boolean
    ) = Option(id, titleRes, style, reversed, selected)

    const val ID_DEFAULT = 200
    const val ID_A_Z = 201
    const val ID_Z_A = 202
    const val ID_YEAR = 203
    const val ID_ARTIST = 204
    const val ID_ALBUM = 205
    const val ID_FOLDER = 206
    const val ID_DATE_ADDED = 207
    const val ID_SIZE = 208
    const val ID_DURATION = 209
    const val ID_RANDOM = 210
    const val ID_REVERSE = 211
    const val ID_TRACK_COUNT = 212
    const val ID_ALBUM_COUNT = 213
    const val ID_REVERSE_ALL = 214

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
