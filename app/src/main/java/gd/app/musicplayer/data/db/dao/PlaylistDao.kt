package gd.app.musicplayer.data.db.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import gd.app.musicplayer.data.db.entity.MusicPlaylistEntity
import gd.app.musicplayer.data.db.entity.PlaylistEntity
import gd.app.musicplayer.data.model.MusicSet
import kotlinx.coroutines.flow.Flow

interface PlaylistDao {

    data class PlaylistSongMatchCount(
        val playlistId: Long,
        val matchedCount: Int
    )

    data class PlaylistSongRef(
        val musicId: Long,
        val playlistId: Long
    )

    data class PlaylistBackupRow(
        val id: Long,
        val name: String
    )

    @Insert
    suspend fun insertPlaylist(item: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMusicPlaylistRefs(items: List<MusicPlaylistEntity>)

    @Query(
        """
        DELETE FROM music_playlist
        WHERE p_id = :playlistId
          AND m_id IN (:musicIds)
        """
    )
    suspend fun deleteMusicPlaylistRefs(playlistId: Long, musicIds: List<Long>)

    @Query(
        """
        SELECT COALESCE(MAX(sort), 0)
        FROM music_playlist
        WHERE p_id = :playlistId
        """
    )
    suspend fun getMaxPlaylistSort(playlistId: Long): Int

    @Query(
        """
        SELECT
            list._id AS id,
            list.name AS name,
            album.s_pic AS albumArt,
            list.sort,
            list.setup_time,
            COUNT(map.p_id) AS musicCount,
            COALESCE(music.album_id, -1) AS album_id,
            COALESCE(album.s_pic, '') AS s_pic,
            CAST(0 AS INTEGER) AS disabled
        FROM
            (SELECT * FROM playlist WHERE playlist._id > 1) AS list
            LEFT JOIN (
                SELECT
                    *
                FROM
                    music_playlist
                WHERE
                    m_id IN (
                        SELECT
                            _id
                        FROM
                            (
                                SELECT *
                                FROM musictbl
                                WHERE hide_time = 0
                                  AND `show` = 1
                                  AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
                            ) AS music
                    )
            ) AS map ON list._id = map.p_id
            LEFT JOIN musictbl AS music ON music._id = map.m_id
            LEFT JOIN album_picture album ON list.name = album.s_name
                AND list._id = album.s_id
        GROUP BY list._id
        ORDER BY list.sort ASC, list.setup_time ASC, list._id ASC
        """
    )
    fun observePlaylists(): Flow<List<MusicSet.Playlist>>

    @Query(
        """
        SELECT
            list._id AS id,
            list.name AS name,
            album.s_pic AS albumArt,
            list.sort,
            list.setup_time,
            COUNT(map.p_id) AS musicCount,
            COALESCE(music.album_id, -1) AS album_id,
            COALESCE(album.s_pic, '') AS s_pic,
            CAST(0 AS INTEGER) AS disabled
        FROM
            (SELECT * FROM playlist WHERE playlist._id > 0) AS list
            LEFT JOIN (
                SELECT
                    *
                FROM
                    music_playlist
                WHERE
                    m_id IN (
                        SELECT
                            _id
                        FROM
                            (
                                SELECT *
                                FROM musictbl
                                WHERE hide_time = 0
                                  AND `show` = 1
                                  AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
                            ) AS music
                    )
            ) AS map ON list._id = map.p_id
            LEFT JOIN musictbl AS music ON music._id = map.m_id
            LEFT JOIN album_picture album ON list.name = album.s_name
                AND list._id = album.s_id
        GROUP BY list._id
        ORDER BY list.sort ASC, list.setup_time ASC, list._id ASC
        """
    )
    fun observeSelectablePlaylists(): Flow<List<MusicSet.Playlist>>

    @Query(
        """
        SELECT
            p._id AS playlistId,
            COUNT(DISTINCT mp.m_id) AS matchedCount
        FROM playlist p
        LEFT JOIN music_playlist mp
            ON p._id = mp.p_id
           AND mp.m_id IN (:songIds)
        WHERE p._id > 0
        GROUP BY p._id
        """
    )
    suspend fun getPlaylistSongMatchCounts(songIds: List<Long>): List<PlaylistSongMatchCount>

    @Query(
        """
        SELECT _id
        FROM playlist
        WHERE name = :name
        LIMIT 1
        """
    )
    suspend fun findPlaylistIdByName(name: String): Long?

    @Query(
        """
        SELECT name
        FROM playlist
        WHERE _id = :playlistId
        LIMIT 1
        """
    )
    suspend fun getPlaylistNameById(playlistId: Long): String?

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM playlist
        WHERE name = :name
          AND (:excludePlaylistId <= 0 OR _id != :excludePlaylistId)
        """
    )
    suspend fun playlistNameExists(name: String, excludePlaylistId: Long = -1L): Boolean

    @Query(
        """
        UPDATE playlist
        SET name = :newName
        WHERE _id = :playlistId
        """
    )
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query(
        """
        DELETE FROM music_playlist
        WHERE p_id = :playlistId
        """
    )
    suspend fun deletePlaylistMusicRefs(playlistId: Long)

    @Query(
        """
        DELETE FROM playlist
        WHERE _id = :playlistId
        """
    )
    suspend fun deletePlaylistRow(playlistId: Long)

    @Transaction
    suspend fun deletePlaylist(playlistId: Long) {
        deletePlaylistMusicRefs(playlistId)
        deletePlaylistRow(playlistId)
    }

    @Query(
        """
        DELETE FROM playlist
        WHERE _id > 1
          AND _id NOT IN (SELECT DISTINCT p_id FROM music_playlist)
        """
    )
    suspend fun deleteEmptyPlaylists(): Int

    @Query(
        """
        UPDATE playlist
        SET sort = :sort
        WHERE _id = :playlistId
        """
    )
    suspend fun updatePlaylistSort(playlistId: Long, sort: Int)

    @Transaction
    suspend fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        playlistIdsInDisplayOrder.forEachIndexed { index, playlistId ->
            updatePlaylistSort(playlistId, index)
        }
    }

    @Query(
        """
        SELECT name
        FROM playlist
        WHERE _id > 0
        """
    )
    suspend fun getAllPlaylistNames(): List<String>

    @Query(
        """
        SELECT _id AS id, name
        FROM playlist
        WHERE _id > 1
        ORDER BY sort ASC, setup_time ASC, _id ASC
        """
    )
    suspend fun getUserPlaylistBackups(): List<PlaylistBackupRow>

    @Query(
        """
        SELECT m_id
        FROM music_playlist
        WHERE p_id = :playlistId
        ORDER BY sort ASC, rowid ASC
        """
    )
    suspend fun getPlaylistTrackIds(playlistId: Long): List<Long>

    @Query(
        """
        SELECT m_id AS musicId, p_id AS playlistId
        FROM music_playlist
        WHERE p_id IN (:playlistIds)
          AND m_id IN (:songIds)
        """
    )
    suspend fun getExistingPlaylistSongRefs(
        playlistIds: List<Long>,
        songIds: List<Long>
    ): List<PlaylistSongRef>
}
