package gd.app.musicplayer.core.database.dao

import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import gd.app.musicplayer.domain.model.Music
import kotlinx.coroutines.flow.Flow

/**
 * Visible-library track observe queries split out of [LibraryDao].
 */
interface LibraryVisibleTracksDao {
    fun observeTracks(sortStyle: String, sortDescending: Boolean): Flow<List<Music>> {
        return when (sortStyle) {
            "random" -> observeTracksRandom()
            "title_desc" -> observeTracksTitleDesc()
            "track" -> if (sortDescending) observeTracksTrackDesc() else observeTracksTrackAsc()
            "year" -> if (sortDescending) observeTracksYearDesc() else observeTracksYearAsc()
            "artist" -> if (sortDescending) observeTracksArtistDesc() else observeTracksArtistAsc()
            "album" -> if (sortDescending) observeTracksAlbumDesc() else observeTracksAlbumAsc()
            "folder" -> if (sortDescending) observeTracksFolderDesc() else observeTracksFolderAsc()
            "date" -> if (sortDescending) observeTracksDateDesc() else observeTracksDateAsc()
            "size" -> if (sortDescending) observeTracksSizeDesc() else observeTracksSizeAsc()
            "duration" -> if (sortDescending) observeTracksDurationDesc() else observeTracksDurationAsc()
            else -> if (sortDescending) observeTracksTitleDesc() else observeTracksTitleAsc()
        }
    }

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY RANDOM(), music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksRandom(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksTitleAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.title COLLATE NOCASE DESC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksTitleDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.track, 0) ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksTrackAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.track, 0) DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksTrackDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.year, 0) ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksYearAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.year, 0) DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksYearDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.artist COLLATE NOCASE ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksArtistAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.artist COLLATE NOCASE DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksArtistDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.album COLLATE NOCASE ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksAlbumAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.album COLLATE NOCASE DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksAlbumDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.folder_path COLLATE NOCASE ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksFolderAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY music.folder_path COLLATE NOCASE DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksFolderDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.date, 0) ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksDateAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.date, 0) DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksDateDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.size, 0) ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksSizeAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.size, 0) DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksSizeDesc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.duration, 0) ASC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksDurationAsc(): Flow<List<Music>>

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM musictbl AS music
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ORDER BY COALESCE(music.duration, 0) DESC, music.title COLLATE NOCASE ASC, music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksDurationDesc(): Flow<List<Music>>
}
