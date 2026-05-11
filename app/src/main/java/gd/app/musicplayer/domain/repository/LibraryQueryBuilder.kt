package gd.app.musicplayer.domain.repository

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.SmartPlaylistConfig

internal object LibraryQueryBuilder {

    fun buildTrackQuery(
        musicSet: MusicSet,
        sortStyle: String,
        sortDescending: Boolean,
        smartConfig: SmartPlaylistConfig
    ): SupportSQLiteQuery {
        val args = mutableListOf<Any>()

        val sql = when (musicSet) {
            is MusicSet.Tracks -> {
                visibleTrackQuery()
                    .appendTrackOrder(
                        sortStyle = sortStyle,
                        sortDescending = sortDescending,
                        sourceId = MusicSet.ALL_TRACKS.toInt()
                    )
            }

            is MusicSet.Artist -> {
                args += musicSet.name

                visibleTrackQuery("artist = ?")
                    .appendTrackOrder(
                        sortStyle = sortStyle,
                        sortDescending = sortDescending,
                        sourceId = MusicSet.ARTISTS.toInt()
                    )
            }

            is MusicSet.Album -> {
                args += musicSet.name

                val whereClause = if (musicSet.artist.isNotBlank()) {
                    args += musicSet.artist
                    "album = ? and artist = ?"
                } else {
                    "album = ?"
                }

                visibleTrackQuery(whereClause)
                    .appendTrackOrder(
                        sortStyle = sortStyle,
                        sortDescending = sortDescending,
                        sourceId = MusicSet.ALBUMS.toInt()
                    )
            }

            is MusicSet.Genre -> {
                args += musicSet.name

                visibleTrackQuery("genres = ?")
                    .appendTrackOrder(
                        sortStyle = sortStyle,
                        sortDescending = sortDescending,
                        sourceId = MusicSet.GENRES.toInt()
                    )
            }

            is MusicSet.Folder -> {
                args += musicSet.folderPath

                visibleTrackQuery("folder_path = ?")
                    .appendTrackOrder(
                        sortStyle = sortStyle,
                        sortDescending = sortDescending,
                        sourceId = MusicSet.FOLDERS.toInt()
                    )
            }

            is MusicSet.Playlist -> {
                args += musicSet.id
                playlistTrackQuery().appendTrackOrder(
                    sortStyle = sortStyle,
                    sortDescending = sortDescending,
                    sourceId = musicSet.id.toInt()
                )
            }

            is MusicSet.Favorites -> {
                args += MusicSet.FAVORITES
                playlistTrackQuery().appendTrackOrder(
                    sortStyle = sortStyle,
                    sortDescending = sortDescending,
                    sourceId = MusicSet.FAVORITES.toInt()
                )
            }

            is MusicSet.RecentlyAdded -> {
                if (smartConfig.windowStartMs > 0L) {
                    args += smartConfig.windowStartMs
                }

                visibleTrackQuery(
                    whereClause = if (smartConfig.windowStartMs > 0L) {
                        "date > ?"
                    } else {
                        null
                    }
                )
                    .appendTrackOrder(
                        sortStyle = sortStyle,
                        sortDescending = sortDescending,
                        sourceId = musicSet.id.toInt()
                    )
                    .appendLimit(
                        limit = smartConfig.trackLimit,
                        args = args
                    )
            }

            is MusicSet.RecentlyPlayed -> {
                if (smartConfig.windowStartMs > 0L) {
                    args += smartConfig.windowStartMs
                }

                visibleTrackQuery(
                    whereClause = if (smartConfig.windowStartMs > 0L) {
                        "play_time > ?"
                    } else {
                        "play_time > 0"
                    }
                )
                    .appendOrderBy("play_time desc, title collate localized asc")
                    .appendLimit(
                        limit = smartConfig.trackLimit,
                        args = args
                    )
            }

            is MusicSet.MostPlayed -> {
                if (smartConfig.windowStartMs > 0L) {
                    args += smartConfig.windowStartMs
                }

                visibleTrackQuery(
                    whereClause = if (smartConfig.windowStartMs > 0L) {
                        "count > 0 and play_time > ?"
                    } else {
                        "count > 0"
                    }
                )
                    .appendOrderBy("count desc, play_time desc, title collate localized asc")
                    .appendLimit(
                        limit = smartConfig.trackLimit,
                        args = args
                    )
            }

            else -> emptyTrackQuery()
        }
        Log.e("Leapy", sql)
        return SimpleSQLiteQuery(
            sql,
            args.toTypedArray()
        )
    }

    fun buildAlbumsByArtistQuery(
        artist: String,
        sortStyle: String,
        sortDescending: Boolean
    ): SupportSQLiteQuery {
        val sql = """
            select album name,
                   count(album) musicCount,
                   max(album_id) id,
                   a.s_pic albumArt,
                   ? artist,
                   max(year) year,
                   max(date) date,
                   coalesce(max(genres), '') genres
            from (
                select *
                from musictbl
                where hide_time = 0
                  and artist = ?
                  and show = 1
                  and folder_path not in (select folder_path from hide_folder)
            ) as music
            left join album_picture a
                   on music.album = a.s_name
                  and a.s_id = -5
            group by album
        """.trimIndent() + buildAlbumOrderBy(sortStyle, sortDescending)

        return SimpleSQLiteQuery(
            sql,
            arrayOf(artist, artist)
        )
    }

    fun buildArtistsQuery(
        sortStyle: String,
        sortDescending: Boolean
    ): SupportSQLiteQuery {
        val sql = """
            select artist name,
                   sum(c_id) musicCount,
                   max(a_id) id,
                   a.s_pic albumArt,
                   count(album) albumCount
            from (
                select artist,
                       album,
                       count(music._id) c_id,
                       max(album_id) a_id
                from (
                    select *
                    from musictbl
                    where hide_time = 0
                      and show = 1
                      and folder_path not in (select folder_path from hide_folder)
                ) as music
                group by artist, album
            ) as artisttbl
            left join album_picture a
                   on artisttbl.artist = a.s_name
                  and a.s_id = -4
            group by artist
        """.trimIndent() + buildArtistOrderBy(sortStyle, sortDescending)

        return SimpleSQLiteQuery(sql)
    }

    fun buildAlbumsQuery(
        sortStyle: String,
        sortDescending: Boolean
    ): SupportSQLiteQuery {
        val sql = """
            select album name,
                   count(album) musicCount,
                   max(album_id) id,
                   a.s_pic albumArt,
                   max(date) date,
                   coalesce(max(music.artist), '') artist,
                   coalesce(max(music.genres), '') genres,
                   max(year) year
            from (
                select *
                from musictbl
                where hide_time = 0
                  and show = 1
                  and folder_path not in (select folder_path from hide_folder)
            ) as music
            left join album_picture a
                   on music.album = a.s_name
                  and a.s_id = -5
            group by album
        """.trimIndent() + buildAlbumOrderBy(sortStyle, sortDescending)

        return SimpleSQLiteQuery(sql)
    }

    fun buildGenresQuery(
        sortStyle: String,
        sortDescending: Boolean
    ): SupportSQLiteQuery {
        val sql = """
            select genres name,
                   count(genres) musicCount,
                   max(album_id) id,
                   a.s_pic albumArt
            from (
                select *
                from musictbl
                where hide_time = 0
                  and show = 1
                  and folder_path not in (select folder_path from hide_folder)
            ) as music
            left join album_picture a
                   on music.genres = a.s_name
                  and a.s_id = -8
            group by genres
        """.trimIndent() + buildGenreOrderBy(sortStyle, sortDescending)

        return SimpleSQLiteQuery(sql)
    }

    fun buildFoldersQuery(
        sortStyle: String,
        sortDescending: Boolean
    ): SupportSQLiteQuery {
        val sql = """
            select music.folder_path folderPath,
                   count(music.folder_path) musicCount,
                   max(music.album_id) id,
                   a.s_pic albumArt,
                   coalesce(max(music.folder_name), music.folder_path) name,
                   max(music.date) date
            from (
                select *
                from musictbl
                where hide_time = 0
                  and show = 1
                  and folder_path not in (select folder_path from hide_folder)
            ) as music
            left join album_picture a
                   on music.folder_path = a.s_name
                  and a.s_id = -6
            group by music.folder_path
        """.trimIndent() + buildFolderOrderBy(sortStyle, sortDescending)

        return SimpleSQLiteQuery(sql)
    }

    fun buildHiddenFoldersQuery(): SupportSQLiteQuery {
        val sql = """
            select h.folder_path folderPath,
                   count(m.folder_path) musicCount,
                   coalesce(max(m.album_id), -1) id,
                   a.s_pic albumArt,
                   coalesce(max(m.date), 0) date,
                   coalesce(max(m.folder_name), h.folder_path) name
            from hide_folder h
            left join musictbl m
                   on m.folder_path = h.folder_path
                  and m.hide_time = 0
                  and m.show = 1
            left join album_picture a
                   on m.folder_path = a.s_name
                  and a.s_id = -6
            group by h.folder_path
            order by h.folder_path collate localized asc
        """.trimIndent()

        return SimpleSQLiteQuery(sql)
    }

    private fun visibleTrackQuery(
        whereClause: String? = null
    ): String {
        val extraWhere = whereClause
            ?.takeIf { it.isNotBlank() }
            ?.let { "and $it" }
            .orEmpty()

        return """
            select music.*, list.p_id as p_id
            from (
                select *
                from musictbl
                where hide_time = 0
                  and show = 1
                  $extraWhere
                  and folder_path not in (select folder_path from hide_folder)
            ) as music
            left join (
                select distinct [m_id], [p_id]
                from music_playlist
                where music_playlist.p_id = 1
            ) as list
                   on music.[_id] = list.[m_id]
        """.trimIndent()
    }

    private fun playlistTrackQuery(): String {
        return """
            select music.*, list.p_id as p_id
            from music_playlist map
            left join (
                select *
                from musictbl
                where hide_time = 0
                  and show = 1
                  and folder_path not in (select folder_path from hide_folder)
            ) as music
                   on music.[_id] = map.[m_id]
            left join (
                select distinct [m_id], [p_id]
                from music_playlist
                where music_playlist.p_id = 1
            ) as list
                   on music.[_id] = list.[m_id]
            where map.[p_id] = ?
              and music._id is not null
        """.trimIndent()
    }

    private fun emptyTrackQuery(): String {
        return """
            select music.*, list.p_id as p_id
            from musictbl as music
            left join (
                select distinct [m_id], [p_id]
                from music_playlist
                where music_playlist.p_id = 1
            ) as list
                   on music.[_id] = list.[m_id]
            where 1 = 0
        """.trimIndent()
    }

    private fun String.appendTrackOrder(
        sortStyle: String,
        sortDescending: Boolean,
        sourceId: Int
    ): String {
        if (sortStyle == SORT_RANDOM) {
            return appendOrderBy("random()")
        }

        val sortColumn = mapTrackSortColumn(
            sortStyle = sortStyle,
            sourceId = sourceId
        )

        val direction = resolveSortDirection(
            sortStyle = sortStyle,
            column = sortColumn,
            reversed = sortDescending,
            numericLikeColumns = NUMERIC_LIKE_TRACK_COLUMNS
        )

        val orderExpression = buildString {
            append(sortColumn)

            if (sortColumn !in NUMERIC_LIKE_TRACK_COLUMNS) {
                append(" collate localized")
            }

            append(direction)

            if (sortColumn != TRACK_TITLE_COLUMN) {
                append(", ")
                append(TRACK_TITLE_COLUMN)
                append(" collate localized asc")
            }
        }

        return appendOrderBy(orderExpression)
    }

    private fun buildAlbumOrderBy(
        sortStyle: String,
        sortDescending: Boolean
    ): String {
        val column = when (sortStyle) {
            "artist" -> "artist"
            "music_count" -> "musicCount"
            "date" -> "date"
            "year" -> "year"
            else -> "name"
        }

        return buildCollectionOrderBy(
            column = column,
            style = sortStyle,
            reversed = sortDescending,
            numericLikeColumns = setOf("musicCount", "date", "year")
        )
    }

    private fun buildArtistOrderBy(
        sortStyle: String,
        sortDescending: Boolean
    ): String {
        val column = when (sortStyle) {
            "music_count" -> "musicCount"
            "album_count" -> "albumCount"
            else -> "name"
        }

        return buildCollectionOrderBy(
            column = column,
            style = sortStyle,
            reversed = sortDescending,
            numericLikeColumns = setOf("musicCount", "albumCount")
        )
    }

    private fun buildGenreOrderBy(
        sortStyle: String,
        sortDescending: Boolean
    ): String {
        val column = when (sortStyle) {
            "music_count" -> "musicCount"
            else -> "name"
        }

        return buildCollectionOrderBy(
            column = column,
            style = sortStyle,
            reversed = sortDescending,
            numericLikeColumns = setOf("musicCount")
        )
    }

    private fun buildFolderOrderBy(
        sortStyle: String,
        sortDescending: Boolean
    ): String {
        val column = when (sortStyle) {
            "music_count" -> "musicCount"
            "date" -> "date"
            else -> "name"
        }

        return buildCollectionOrderBy(
            column = column,
            style = sortStyle,
            reversed = sortDescending,
            numericLikeColumns = setOf("musicCount", "date")
        )
    }

    private fun buildCollectionOrderBy(
        column: String,
        style: String,
        reversed: Boolean,
        numericLikeColumns: Set<String>
    ): String {
        val direction = resolveSortDirection(
            sortStyle = style,
            column = column,
            reversed = reversed,
            numericLikeColumns = numericLikeColumns
        )

        val expression = buildString {
            append(column)

            if (column !in numericLikeColumns) {
                append(" collate localized")
            }

            append(direction)

            if (column != COLLECTION_NAME_COLUMN) {
                append(", ")
                append(COLLECTION_NAME_COLUMN)
                append(" collate localized asc")
            }
        }

        return " order by $expression"
    }

    private fun resolveSortDirection(
        sortStyle: String,
        column: String,
        reversed: Boolean,
        numericLikeColumns: Set<String>
    ): String {
        return when {
            sortStyle == SORT_NAME -> {
                if (reversed) " desc" else " asc"
            }

            column in numericLikeColumns -> {
                if (reversed) " asc" else " desc"
            }

            else -> {
                if (reversed) " desc" else " asc"
            }
        }
    }

    private fun String.appendOrderBy(
        expression: String
    ): String {
        return "$this order by $expression"
    }

    private fun String.appendLimit(
        limit: Int,
        args: MutableList<Any>
    ): String {
        if (limit <= 0) {
            return this
        }

        args += limit
        return "$this limit ?"
    }

    private fun mapTrackSortColumn(
        sortStyle: String,
        sourceId: Int
    ): String {
        return when (sortStyle) {
            SORT_NAME,
            SORT_RANDOM -> TRACK_TITLE_COLUMN

            "artist" -> "artist"
            "album" -> "album"
            "folder" -> {
                if (sourceId == MusicSet.FOLDERS.toInt()) {
                    "title"
                } else {
                    "folder_path"
                }
            }

            "duration" -> "duration"
            else -> sortStyle
        }
    }

    private const val SORT_NAME = "name"
    private const val SORT_RANDOM = "random"

    private const val TRACK_TITLE_COLUMN = "title"
    private const val COLLECTION_NAME_COLUMN = "name"

    private val NUMERIC_LIKE_TRACK_COLUMNS = setOf(
        "count",
        "date",
        "size",
        "duration"
    )
}
