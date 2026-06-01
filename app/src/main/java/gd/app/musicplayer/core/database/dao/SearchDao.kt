package gd.app.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

    @Query(
        """
        SELECT
          music.*,
          list.p_id AS p_id
        FROM
          musictbl AS music
          LEFT JOIN (
            SELECT DISTINCT ([m_id]), [p_id]
            FROM music_playlist
            WHERE music_playlist.p_id = 1
          ) AS list ON music.[_id] = list.[m_id]
        WHERE music.hide_time = 0
          AND music.`show` = 1
          AND music.folder_path NOT IN (SELECT folder_path FROM hide_folder)
          AND (
              COALESCE(music.title, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeSearchTracks(query: String): Flow<List<Music>>

    @Query(
        """
        SELECT *
        FROM (
          select album name,
                 count(album) musicCount,
                 max(album_id) id,
                 a.s_pic albumArt,
                 max(date) date,
                 music.artist,
                 music.genres,
                 max(year) year
          from (
            select * from musictbl
            where hide_time = 0
              and show = 1
              and folder_path not in (select folder_path from hide_folder)
          ) as music
          left join album_picture a
            on music.album = a.s_name
           and a.s_id = -5
          group by album
        ) AS albumtbl
        WHERE COALESCE(albumtbl.name, '') LIKE '%' || :query || '%' COLLATE NOCASE
           OR COALESCE(albumtbl.artist, '') LIKE '%' || :query || '%' COLLATE NOCASE
        """
    )
    fun observeSearchAlbums(query: String): Flow<List<MusicSet.Album>>

    @Query(
        """
        SELECT *
        FROM (
          SELECT
            artist as name,
            sum(c_id) musicCount,
            max(a_id) as id,
            a.s_pic as albumArt,
            count(album) albumCount
          FROM
            (
              SELECT
                artist,
                album,
                count(music._id) c_id,
                max(album_id) a_id
              FROM
                (SELECT * FROM musictbl WHERE hide_time = 0 AND show = 1 AND folder_path NOT IN (SELECT folder_path FROM hide_folder)) AS music
              GROUP BY
                artist,
                album
            ) AS artisttbl
            LEFT JOIN album_picture a ON artisttbl.artist = a.s_name
            AND a.s_id = - 4
          GROUP BY
            artist
        ) AS artisttbl
        WHERE COALESCE(artisttbl.name, '') LIKE '%' || :query || '%' COLLATE NOCASE
        """
    )
    fun observeSearchArtists(query: String): Flow<List<MusicSet.Artist>>

    @Query(
        """
        SELECT *
        FROM (
          select music.folder_path folderPath,
                 count(music.folder_path) musicCount,
                 max(music.album_id) id,
                 a.s_pic albumArt,
                 music.folder_name name,
                 max(music.date) date
          from (
            select * from musictbl
            where hide_time = 0
              and show = 1
              and folder_path not in (select folder_path from hide_folder)
          ) as music
          left join album_picture a
            on music.folder_path = a.s_name
           and a.s_id = -6
          group by music.folder_path
        ) AS foldertbl
        WHERE COALESCE(foldertbl.name, '') LIKE '%' || :query || '%' COLLATE NOCASE
           OR COALESCE(foldertbl.folderPath, '') LIKE '%' || :query || '%' COLLATE NOCASE
        """
    )
    fun observeSearchFolders(query: String): Flow<List<MusicSet.Folder>>

    @Query(
        """
        SELECT *
        FROM (
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
        ) AS playlisttbl
        WHERE COALESCE(playlisttbl.name, '') LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY playlisttbl.sort ASC, playlisttbl.setup_time ASC, playlisttbl.id ASC
        """
    )
    fun observeSearchPlaylists(query: String): Flow<List<MusicSet.Playlist>>
}
