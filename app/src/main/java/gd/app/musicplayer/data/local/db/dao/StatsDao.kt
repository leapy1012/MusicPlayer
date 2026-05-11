package gd.app.musicplayer.data.local.db.dao

import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface StatsDao {

    @Query(
        """
        SELECT COUNT(_id) FROM musictbl
        WHERE hide_time = 0
          AND `show` = 1
          AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
        """
    )
    fun observeLibraryCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT folder_path
            FROM musictbl
            WHERE hide_time = 0
              AND `show` = 1
              AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
            GROUP BY folder_path
        ) t
        """
    )
    fun observeFolderCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM music_playlist mp
        WHERE mp.p_id = 1
          AND mp.m_id IN (
              SELECT _id
              FROM musictbl
              WHERE hide_time = 0
                AND `show` = 1
                AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
          )
        """
    )
    fun observeFavoriteCount(): Flow<Int>

    @Query(
        """
        SELECT CASE
            WHEN :playlistLimit > 0 AND COUNT(*) > :playlistLimit THEN :playlistLimit
            ELSE COUNT(*)
        END
        FROM musictbl
        WHERE hide_time = 0
          AND `show` = 1
          AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
          AND (
                (:playlistWindowMs > 0 AND play_time > :windowStartMs)
                OR
                (:playlistWindowMs <= 0 AND play_time > 0)
          )
        """
    )
    fun observeRecentPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int>

    @Query(
        """
        SELECT CASE
            WHEN :playlistLimit > 0 AND COUNT(*) > :playlistLimit THEN :playlistLimit
            ELSE COUNT(*)
        END
        FROM musictbl
        WHERE hide_time = 0
          AND `show` = 1
          AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
          AND (
                :playlistWindowMs <= 0
                OR
                date > :windowStartMs
          )
        """
    )
    fun observeRecentAddCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int>

    @Query(
        """
        SELECT CASE
            WHEN :playlistLimit > 0 AND COUNT(*) > :playlistLimit THEN :playlistLimit
            ELSE COUNT(*)
        END
        FROM musictbl
        WHERE hide_time = 0
          AND `show` = 1
          AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
          AND count > 0
          AND (
                :playlistWindowMs <= 0
                OR
                play_time > :windowStartMs
          )
        """
    )
    fun observeMostPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int>
}
