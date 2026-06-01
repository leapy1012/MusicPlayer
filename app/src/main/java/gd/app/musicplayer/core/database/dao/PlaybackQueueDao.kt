package gd.app.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.core.database.entity.MusicPlaylistEntity
import gd.app.musicplayer.domain.model.Music
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackQueueDao {

    @Query(
        """
        SELECT music.*, list.p_id AS p_id
        FROM music_playlist map
        LEFT JOIN (
          SELECT *
          FROM musictbl
          WHERE hide_time = 0
            AND `show` = 1
            AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
        ) AS music ON music.[_id] = map.[m_id]
        LEFT JOIN (
          SELECT DISTINCT([m_id]), [p_id]
          FROM music_playlist
          WHERE music_playlist.p_id = 1
        ) AS list ON music.[_id] = list.[m_id]
        WHERE map.[p_id] = :queueId
          AND music._id IS NOT NULL
        ORDER BY map.sort ASC, map.rowid ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeQueue(queueId: Long): Flow<List<Music>>

    @Query(
        """
        DELETE FROM music_playlist
        WHERE p_id = :queueId
        """
    )
    suspend fun clearQueue(queueId: Long)

    @Query("DELETE FROM music_playlist WHERE m_id = :musicId")
    suspend fun remove(musicId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<MusicPlaylistEntity>)

    @Transaction
    suspend fun replaceQueue(
        queueId: Long,
        musicIdsInOrder: List<Long>
    ) {
        clearQueue(queueId)
        if (musicIdsInOrder.isEmpty()) return
        insert(musicIdsInOrder.mapIndexed { index, musicId ->
            MusicPlaylistEntity(
                musicId = musicId,
                playlistId = queueId,
                sort = index
            )
        })
    }
}
