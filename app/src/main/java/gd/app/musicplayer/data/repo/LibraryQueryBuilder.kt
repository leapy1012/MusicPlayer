package gd.app.musicplayer.data.repositorysitory

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.SmartPlaylistPreferenceOps

internal object LibraryQueryBuilder {

    fun buildTrackQuery(
        musicSet: MusicSet,
        sortStyle: String,
        sortDescending: Boolean,
        smartConfig: SmartPlaylistPreferenceOps.SmartPlaylistConfig
    ): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val sql = when (musicSet) {
            is MusicSet.Tracks -> {
                baseVisibleTrackQuery()
                    .appendTrackOrder(sortStyle, sortDescending, musicSet.id.toInt(), false)
            }

            is MusicSet.Artist -> {
                args += musicSet.name
                visibleTrackQuery("and artist = ?")
                    .appendTrackOrder(sortStyle, sortDescending, musicSet.id.toInt(), false)
            }

            is MusicSet.Album -> {
                args += musicSet.name
                if (musicSet.artist.isNotBlank()) {
                    args += musicSet.artist
                    visibleTrackQuery("and album = ? and artist = ?")
                } else {
                    visibleTrackQuery("and album = ?")
                }.appendTrackOrder(sortStyle, sortDescending, musicSet.id.toInt(), false)
            }

            is MusicSet.Genre -> {
                args += musicSet.name
                visibleTrackQuery("and genres = ?")
                    .appendTrackOrder(sortStyle, sortDescending, musicSet.id.toInt(), false)
            }

            is MusicSet.Folder -> {
                args += musicSet.folderPath
                visibleTrackQuery("and folder_path = ?")
                    .appendTrackOrder(sortStyle, sortDescending, musicSet.id.toInt(), false)
            }

            is MusicSet.Playlist -> {
                args += musicSet.id
                playlistTrackQuery()
            }

            is MusicSet.Favorites -> {
                args += MusicSet.FAVORITES_ID
                playlistTrackQuery()
            }

            is MusicSet.RecentlyAdded -> {
                visibleTrackQuery(
                    if (smartConfig.windowStartMs > 0L) {
                        "and date > ${smartConfig.windowStartMs}"
                    } else {
                        ""
                    }
                ).appendTrackOrder(sortStyle, sortDescending, musicSet.id.toInt(), false)
                    .appendLimit(smartConfig.playlistLimit)
            }

            is MusicSet.RecentlyPlayed -> {
                visibleTrackQuery(
                    if (smartConfig.windowStartMs > 0L) {
                        "and play_time > ${smartConfig.windowStartMs}"
                    } else {
                        "and play_time > 0"
                    }
                ) + " order by play_time desc, title" + buildLimitClause(smartConfig.playlistLimit)
            }

            is MusicSet.MostPlayed -> {
                visibleTrackQuery(
                    if (smartConfig.windowStartMs > 0L) {
                        "and count > 0 and play_time > ${smartConfig.windowStartMs}"
                    } else {
                        "and count > 0"
                    }
                ) + " order by count desc, play_time desc" + buildLimitClause(smartConfig.playlistLimit)
            }

            else -> "select music.*, list.p_id as p_id from musictbl as music left join (select DISTINCT([m_id]), [p_id] from music_playlist where music_playlist.p_id = 1) as list on music.[_id] = list.[m_id] where 1 = 0"
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun buildAlbumsByArtistQuery(
        artist: String,
        preferenceUtil: PreferenceUtil
    ): SupportSQLiteQuery {
        val sql = """
            select album name,
                   count(album) musicCount,
                   max(album_id) id,
                   a.s_pic albumArt,
                   ? artist,
                   max(year) year,
                   max(date) date,
                   COALESCE(max(genres), '') genres
            from (select * from musictbl where hide_time = 0 and artist = ? and show = 1 and folder_path not in (select folder_path from hide_folder)) as music
            left join album_picture a on music.album = a.s_name and a.s_id = -5
            group by album
        """.trimIndent() + buildAlbumOrderBy(preferenceUtil)
        return SimpleSQLiteQuery(sql, arrayOf(artist, artist))
    }

    fun buildArtistsQuery(preferenceUtil: PreferenceUtil): SupportSQLiteQuery {
        val sql = """
            select artist name,
                   sum(c_id) musicCount,
                   max(a_id) id,
                   a.s_pic albumArt,
                   count(album) albumCount
            from (
                select artist, album, count(music._id) c_id, max(album_id) a_id
                from (select * from musictbl where hide_time = 0 and show = 1 and folder_path not in (select folder_path from hide_folder)) as music
                group by artist, album
            ) as artisttbl
            left join album_picture a on artisttbl.artist = a.s_name and a.s_id = -4
            group by artist
        """.trimIndent() + buildArtistOrderBy(preferenceUtil)
        return SimpleSQLiteQuery(sql)
    }

    fun buildAlbumsQuery(preferenceUtil: PreferenceUtil): SupportSQLiteQuery {
        val sql = """
            select album name,
                   count(album) musicCount,
                   max(album_id) id,
                   a.s_pic albumArt,
                   max(date) date,
                   COALESCE(max(music.artist), '') artist,
                   COALESCE(max(music.genres), '') genres,
                   max(year) year
            from (select * from musictbl where hide_time = 0 and show = 1 and folder_path not in (select folder_path from hide_folder)) as music
            left join album_picture a on music.album = a.s_name and a.s_id = -5
            group by album
        """.trimIndent() + buildAlbumOrderBy(preferenceUtil)
        android.util.Log.e("Leapy", "album Query = " + sql)
        return SimpleSQLiteQuery(sql)
    }

    fun buildGenresQuery(preferenceUtil: PreferenceUtil): SupportSQLiteQuery {
        val sql = """
            select genres name,
                   count(genres) musicCount,
                   max(album_id) id,
                   a.s_pic albumArt
            from (select * from musictbl where hide_time = 0 and show = 1 and folder_path not in (select folder_path from hide_folder)) as music
            left join album_picture a on music.genres = a.s_name and a.s_id = -8
            group by genres
        """.trimIndent() + buildGenreOrderBy(preferenceUtil)
        return SimpleSQLiteQuery(sql)
    }

    fun buildFoldersQuery(preferenceUtil: PreferenceUtil): SupportSQLiteQuery {
        val sql = """
            select music.folder_path folderPath,
                   count(music.folder_path) musicCount,
                   max(music.album_id) id,
                   a.s_pic albumArt,
                   COALESCE(max(music.folder_name), music.folder_path) name,
                   max(music.date) date
            from (select * from musictbl where hide_time = 0 and show = 1 and folder_path not in (select folder_path from hide_folder)) as music
            left join album_picture a on music.folder_path = a.s_name and a.s_id = -6
            group by music.folder_path
        """.trimIndent() + buildFolderOrderBy(preferenceUtil)
        android.util.Log.e("Leapy", "buildFoldersQuery:" + sql)
        return SimpleSQLiteQuery(sql)
    }

    fun buildHiddenFoldersQuery(): SupportSQLiteQuery =
        SimpleSQLiteQuery(
            """
            select h.folder_path folderPath,
                   count(m.folder_path) musicCount,
                   COALESCE(max(m.album_id), -1) id,
                   a.s_pic albumArt,
                   COALESCE(max(m.date), 0) date,
                   COALESCE(max(m.folder_name), h.folder_path) name
            from hide_folder h
            left join musictbl m on m.folder_path = h.folder_path and m.hide_time = 0 and m.show = 1
            left join album_picture a on m.folder_path = a.s_name and a.s_id = -6
            group by h.folder_path
            order by h.folder_path
            """.trimIndent()
        )

    private fun baseVisibleTrackQuery(): String =
        visibleTrackQuery("")

    private fun visibleTrackQuery(whereClause: String): String =
        """
        select music.*, list.p_id as p_id
        from (select * from musictbl where hide_time = 0 and show = 1 ${whereClause.trimIndent()} and folder_path not in (select folder_path from hide_folder)) as music
        left join (select DISTINCT([m_id]), [p_id] from music_playlist where music_playlist.p_id = 1) as list on music.[_id] = list.[m_id]
        """.trimIndent()

    private fun playlistTrackQuery(): String =
        """
        select music.*, list.p_id as p_id
        from music_playlist map
        left join (select * from musictbl where hide_time = 0 and show = 1 and folder_path not in (select folder_path from hide_folder)) as music on music.[_id] = map.[m_id]
        left join (select DISTINCT([m_id]), [p_id] from music_playlist where music_playlist.p_id = 1) as list on music.[_id] = list.[m_id]
        where map.[p_id] = ? and music._id is not null
        order by map.sort asc
        """.trimIndent()

    private fun String.appendTrackOrder(
        sortStyle: String,
        sortDescending: Boolean,
        sourceId: Int,
        selectionMode: Boolean
    ): String {
        if (sortStyle == "random") {
            return this + " order by RANDOM()"
        }

        val sortColumn = mapTrackSortColumn(sortStyle, sourceId, selectionMode)
        val direction = when {
            sortStyle == "title_desc" -> " desc"
            sortColumn in NUMERIC_LIKE_TRACK_COLUMNS -> if (sortDescending) " asc" else " desc"
            else -> if (sortDescending) " desc" else " asc"
        }

        return buildString {
            append(this@appendTrackOrder)
            append(" order by ")
            append(sortColumn)
            append(" COLLATE LOCALIZED")
            append(direction)
            if (sortColumn != "title") {
                append(", title COLLATE LOCALIZED")
                append(direction)
            }
        }
    }

    private fun buildAlbumOrderBy(preferenceUtil: PreferenceUtil): String {
        val style = preferenceUtil.getAlbumSortStyle()
        val reversed = preferenceUtil.isAlbumSortReversed()
        val column = when (style) {
            "artist" -> "artist"
            "music_count" -> "musicCount"
            "date" -> "date"
            "year" -> "year"
            else -> "name"
        }
        return buildCollectionOrderBy(column, style, reversed, setOf("musicCount", "date"))
    }

    private fun buildArtistOrderBy(preferenceUtil: PreferenceUtil): String {
        val style = preferenceUtil.getArtistSortStyle()
        val reversed = preferenceUtil.isArtistSortReversed()
        val column = when (style) {
            "music_count" -> "musicCount"
            "album_count" -> "albumCount"
            else -> "name"
        }
        return buildCollectionOrderBy(column, style, reversed, setOf("musicCount", "albumCount"))
    }

    private fun buildGenreOrderBy(preferenceUtil: PreferenceUtil): String {
        val style = preferenceUtil.getGenreSortStyle()
        val reversed = preferenceUtil.isGenreSortReversed()
        val column = if (style == "music_count") "musicCount" else "name"
        return buildCollectionOrderBy(column, style, reversed, setOf("musicCount"))
    }

    private fun buildFolderOrderBy(preferenceUtil: PreferenceUtil): String {
        val style = preferenceUtil.getFolderSortStyle(selectionMode = false)
        val reversed = preferenceUtil.isFolderSortReversed(selectionMode = false)
        val column = when (style) {
            "music_count" -> "musicCount"
            "date" -> "date"
            else -> "name"
        }
        return buildCollectionOrderBy(column, style, reversed, setOf("musicCount", "date"))
    }

    private fun buildCollectionOrderBy(
        column: String,
        style: String,
        reversed: Boolean,
        numericLikeColumns: Set<String>
    ): String {
        val direction = when {
            style == "name" && !reversed -> " asc"
            style == "name" && reversed -> " desc"
            column in numericLikeColumns -> if (reversed) " asc" else " desc"
            else -> if (reversed) " desc" else " asc"
        }
        return buildString {
            append(" order by ")
            append(column)
            append(" COLLATE LOCALIZED")
            append(direction)
            if (column != "name") {
                append(", name COLLATE LOCALIZED asc")
            }
        }
    }

    private fun buildLimitClause(limit: Int): String =
        if (limit > 0) " limit $limit" else ""

    private fun String.appendLimit(limit: Int): String =
        this + buildLimitClause(limit)

    private fun mapTrackSortColumn(
        sortStyle: String,
        sourceId: Int,
        selectionMode: Boolean
    ): String {
        val rawColumn = when (sortStyle) {
            "name", "random" -> "title"
            "artist" -> "artist"
            "album" -> "album"
            "folder" -> "folder_path"
            "duration" -> "duration"
            else -> sortStyle
        }
        return if (selectionMode && sourceId == MusicSet.FOLDERS_ID.toInt() && rawColumn == "folder_name") {
            "title"
        } else {
            rawColumn
        }
    }

    private val NUMERIC_LIKE_TRACK_COLUMNS = setOf("count", "date", "size")
}
