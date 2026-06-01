package gd.app.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Insert
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import gd.app.musicplayer.core.database.entity.AlbumPictureEntity
import gd.app.musicplayer.core.database.entity.HiddenFolderEntity
import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.core.database.entity.MusicPlaylistEntity
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Upsert
    suspend fun upsertAll(items: List<MusicEntity>)

    @Query("SELECT * FROM musictbl")
    suspend fun getAllMusicForSync(): List<MusicEntity>

    @Query("SELECT * FROM musictbl WHERE _id = :trackId LIMIT 1")
    suspend fun getMusicEntityById(trackId: Long): MusicEntity?

    @Query("SELECT * FROM musictbl WHERE _id IN (:trackIds)")
    suspend fun getMusicEntitiesByIds(trackIds: List<Long>): List<MusicEntity>

    @Query("SELECT _id FROM musictbl")
    suspend fun getAllTrackIdsForSync(): List<Long>

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
    suspend fun getVisibleTracksSnapshotForPlayback(): List<Music>

    @Query("SELECT MAX(date_modified) FROM musictbl")
    suspend fun getMaxTrackDateModifiedForSync(): Long?

    @Query(
        """
        UPDATE musictbl
        SET title = :title,
            album = :album,
            artist = :artist,
            genres = :genre,
            track = :trackNumber,
            album_pic = :artworkPath
        WHERE _id = :trackId
        """
    )
    suspend fun updateTrackMetadata(
        trackId: Long,
        title: String,
        album: String,
        artist: String,
        genre: String,
        trackNumber: Int,
        artworkPath: String?
    ): Int

    @Query(
        """
        SELECT COUNT(_id)
        FROM musictbl
        WHERE hide_time = 0
          AND `show` = 1
          AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
        """
    )
    suspend fun countVisibleTracksForSync(): Int

    @Query(
        """
        SELECT COUNT(DISTINCT album)
        FROM musictbl
        WHERE hide_time = 0
          AND `show` = 1
          AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
          AND album IS NOT NULL
          AND album != ''
        """
    )
    suspend fun countVisibleAlbumsForSync(): Int

    @Query(
        """
        SELECT COUNT(DISTINCT artist)
        FROM musictbl
        WHERE hide_time = 0
          AND `show` = 1
          AND folder_path NOT IN (SELECT folder_path FROM hide_folder)
          AND artist IS NOT NULL
          AND artist != ''
        """
    )
    suspend fun countVisibleArtistsForSync(): Int

    @Query("SELECT COUNT(*) FROM hide_folder")
    suspend fun countHiddenFoldersForSync(): Int

    @Query(
        """
        SELECT COUNT(_id)
        FROM musictbl
        WHERE hide_time > 0
          AND `show` = 1
        """
    )
    suspend fun countHiddenTracksForSync(): Int

    @Query(
        """
        SELECT COUNT(_id)
        FROM musictbl
        WHERE hide_time = 0
          AND `show` = 0
        """
    )
    suspend fun countSourceDeletedTracksForSync(): Int

    @Query(
        """
        UPDATE musictbl
        SET `show` = 0
        WHERE `show` = 2
        """
    )
    suspend fun normalizeSourceDeletedTracksForSync(): Int

    @Query(
        """
        UPDATE musictbl
        SET `show` = :visibleState,
            state_time = :stateTime,
            hide_time = 0
        WHERE _id IN (:musicIds)
        """
    )
    suspend fun updateMusicVisibleState(
        visibleState: Int,
        stateTime: Long,
        musicIds: List<Long>
    )

    @Query(
        """
        DELETE FROM music_playlist
        WHERE m_id IN (:musicIds)
        """
    )
    suspend fun deleteMusicPlaylistRefsByTrackIds(musicIds: List<Long>)

    @Upsert
    suspend fun upsertHiddenFolders(items: List<HiddenFolderEntity>)

    @Insert
    suspend fun insertAlbumPicture(item: AlbumPictureEntity): Long

    @Query(
        """
        UPDATE musictbl
        SET hide_time = :hideTime
        WHERE _id IN (:musicIds)
        """
    )
    suspend fun updateMusicHideTime(hideTime: Long, musicIds: List<Long>)

    @Query(
        """
        SELECT s_pic
        FROM album_picture
        WHERE s_id = :sourceId AND s_name = :sourceName
        LIMIT 1
        """
    )
    suspend fun getAlbumPicture(sourceId: Long, sourceName: String): String?

    @Query(
        """
        SELECT s_pic
        FROM album_picture
        WHERE s_id = :sourceId
        """
    )
    suspend fun getAlbumPicturesBySourceId(sourceId: Long): List<String>

    @Query(
        """
        SELECT _id
        FROM album_picture
        WHERE s_id = :sourceId AND s_name = :sourceName
        LIMIT 1
        """
    )
    suspend fun getAlbumPictureRowId(sourceId: Long, sourceName: String): Long?

    @Query(
        """
        UPDATE album_picture
        SET s_pic = :sourcePicture
        WHERE _id = :rowId
        """
    )
    suspend fun updateAlbumPicture(rowId: Long, sourcePicture: String?)

    @Query(
        """
        UPDATE album_picture
        SET s_name = :newSourceName,
            s_pic = :artworkPath
        WHERE s_id = :sourceId
          AND s_name = :oldSourceName
        """
    )
    suspend fun updateAlbumPictureSource(
        sourceId: Long,
        oldSourceName: String,
        newSourceName: String,
        artworkPath: String?
    ): Int

    @Query(
        """
        DELETE FROM album_picture
        WHERE s_id = :sourceId AND s_name = :sourceName
        """
    )
    suspend fun deleteAlbumPicture(sourceId: Long, sourceName: String)

    @Query(
        """
        UPDATE album_picture
        SET s_name = :newSourceName
        WHERE s_id = :sourceId
          AND (:oldSourceName IS NULL OR s_name = :oldSourceName)
        """
    )
    suspend fun updateAlbumPictureSourceName(
        sourceId: Long,
        newSourceName: String,
        oldSourceName: String? = null
    )

    @Query(
        """
        DELETE FROM album_picture
        WHERE s_id = :sourceId
        """
    )
    suspend fun deleteAlbumPicturesBySourceId(sourceId: Long)

    @Query(
        """
        DELETE FROM album_picture
        WHERE s_id > 0
          AND s_id NOT IN (SELECT _id FROM playlist)
        """
    )
    suspend fun deleteOrphanedPlaylistAlbumPictures()

    @Query(
        """
        SELECT s_pic
        FROM album_picture
        WHERE s_id > 0
          AND s_id NOT IN (SELECT _id FROM playlist)
        """
    )
    suspend fun getOrphanedPlaylistAlbumPicturePaths(): List<String>

    @Query(
        """
        UPDATE musictbl
        SET album_pic = :artworkPath
        WHERE _id = :trackId
        """
    )
    suspend fun updateTrackArtwork(trackId: Long, artworkPath: String?)

    @Query(
        """
        SELECT album_pic
        FROM musictbl
        WHERE _id = :trackId
        LIMIT 1
        """
    )
    fun observeTrackArtwork(trackId: Long): Flow<String?>

    @Query(
        """
        UPDATE musictbl
        SET play_time = :playTime
        WHERE _id = :trackId
        """
    )
    suspend fun updateTrackPlayTime(trackId: Long, playTime: Long)

    @Query(
        """
        UPDATE musictbl
        SET count = count + 1
        WHERE _id = :trackId
        """
    )
    suspend fun incrementTrackPlayCount(trackId: Long)

    @Query(
        """
        UPDATE musictbl
        SET count = 0
        """
    )
    suspend fun clearMostPlayedStats()

    @Query(
        """
        UPDATE musictbl
        SET count = 0
        WHERE _id = :trackId
        """
    )
    suspend fun clearTrackMostPlayedStats(trackId: Long)

    @Query(
        """
        UPDATE musictbl
        SET play_time = 0
        """
    )
    suspend fun clearRecentlyPlayedStats()

    @Query(
        """
        UPDATE musictbl
        SET play_time = 0
        WHERE _id = :trackId
        """
    )
    suspend fun clearTrackRecentlyPlayedStats(trackId: Long)

    @Query(
        """
        UPDATE musictbl
        SET date = 0
        WHERE date != 0
        """
    )
    suspend fun clearRecentlyAddedStats()

    @Query(
        """
        DELETE FROM music_playlist
        WHERE p_id = :playlistId
        """
    )
    suspend fun clearPlaylistEntries(playlistId: Long)

    @Query(
        """
        UPDATE musictbl
        SET album_pic = :artworkPath
        WHERE album = :albumName
        """
    )
    suspend fun updateTrackArtworkByAlbum(albumName: String, artworkPath: String?)

    @Query(
        """
        UPDATE musictbl
        SET album_pic = :artworkPath
        WHERE artist = :artistName
        """
    )
    suspend fun updateTrackArtworkByArtist(artistName: String, artworkPath: String?)

    @Query(
        """
        UPDATE musictbl
        SET album_pic = :artworkPath
        WHERE genres = :genreName
        """
    )
    suspend fun updateTrackArtworkByGenre(genreName: String, artworkPath: String?)

    @Query(
        """
        UPDATE musictbl
        SET album = :newAlbum,
            artist = :newArtist,
            genres = :newGenre,
            year = :newYear
        WHERE album = :oldAlbum
        """
    )
    suspend fun updateTracksByAlbumName(
        oldAlbum: String,
        newAlbum: String,
        newArtist: String,
        newGenre: String,
        newYear: Int
    )

    @Query(
        """
        UPDATE musictbl
        SET artist = :newArtist
        WHERE artist = :oldArtist
        """
    )
    suspend fun updateTracksByArtistName(oldArtist: String, newArtist: String)

    @Query(
        """
        UPDATE musictbl
        SET genres = :newGenre
        WHERE genres = :oldGenre
        """
    )
    suspend fun updateTracksByGenreName(oldGenre: String, newGenre: String)

    @Query(
        """
        SELECT COUNT(_id)
        FROM musictbl
        WHERE album_pic = :artworkPath
        """
    )
    suspend fun countTracksUsingArtwork(artworkPath: String): Int

    @Query(
        """
        SELECT COUNT(_id)
        FROM album_picture
        WHERE s_pic = :artworkPath
        """
    )
    suspend fun countSetsUsingArtwork(artworkPath: String): Int

    @Query(
        """
        SELECT
            h.folder_path AS folderPath,
            COUNT(m.folder_path) AS musicCount,
            COALESCE(MAX(m.album_id), -1) AS id,
            a.s_pic AS albumArt,
            COALESCE(MAX(m.date), 0) AS date,
            COALESCE(MAX(m.folder_name), h.folder_path) AS name
        FROM hide_folder h
        LEFT JOIN musictbl m
            ON m.folder_path = h.folder_path
           AND m.hide_time = 0
           AND m.`show` = 1
        LEFT JOIN album_picture a
            ON m.folder_path = a.s_name
           AND a.s_id = -6
        GROUP BY h.folder_path
        ORDER BY h.folder_path
        """
    )
    fun observeHiddenFolders(): Flow<List<MusicSet.Folder>>

    @Query(
        """
        SELECT
          music.*,
          list.p_id AS p_id
        FROM (SELECT * FROM musictbl WHERE hide_time > 0 AND `show` = 1) AS music
        LEFT JOIN (
            SELECT DISTINCT([m_id]), [p_id]
            FROM music_playlist
            WHERE music_playlist.p_id = 1
        ) AS list
            ON music.[_id] = list.[m_id]
        ORDER BY music.state_time DESC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeHiddenSongs(): Flow<List<Music>>

    @Query(
        """
        SELECT
          music.*,
          list.p_id AS p_id
        FROM (SELECT * FROM musictbl WHERE hide_time = 0 AND `show` = 0) AS music
        LEFT JOIN (
            SELECT DISTINCT([m_id]), [p_id]
            FROM music_playlist
            WHERE music_playlist.p_id = 1
        ) AS list
            ON music.[_id] = list.[m_id]
        ORDER BY music.state_time DESC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeDeletedSongs(): Flow<List<Music>>

    @Query(
        """
        UPDATE musictbl
        SET `show` = 1
        WHERE _id IN (:musicIds)
        """
    )
    suspend fun restoreDeletedSongs(musicIds: List<Long>)

    @Query(
        """
        UPDATE musictbl
        SET `show` = 0,
            state_time = :stateTime
        WHERE _id IN (:musicIds)
        """
    )
    suspend fun markSourceDeletedSongs(musicIds: List<Long>, stateTime: Long)

    @Query(
        """
        UPDATE musictbl
        SET `show` = 2,
            state_time = :stateTime
        WHERE _id IN (:musicIds)
        """
    )
    suspend fun markDeletedSourceFilesRemoved(musicIds: List<Long>, stateTime: Long)

    @Query(
        """
        DELETE FROM hide_folder
        WHERE folder_path = :folderPath
        """
    )
    suspend fun removeHiddenFolder(folderPath: String)

    @Query(
        """
        SELECT _id
        FROM musictbl
        WHERE _id IN (:trackIds)
        """
    )
    suspend fun getExistingTrackIds(trackIds: List<Long>): List<Long>


    @RawQuery(
        observedEntities = [
            MusicEntity::class,
            MusicPlaylistEntity::class,
            HiddenFolderEntity::class
        ]
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksRaw(query: SupportSQLiteQuery): Flow<List<Music>>

    @RawQuery(observedEntities = [MusicEntity::class, HiddenFolderEntity::class, AlbumPictureEntity::class])
    fun observeArtistsRaw(query: SupportSQLiteQuery): Flow<List<MusicSet.Artist>>

    @RawQuery(observedEntities = [MusicEntity::class, HiddenFolderEntity::class, AlbumPictureEntity::class])
    fun observeAlbumsRaw(query: SupportSQLiteQuery): Flow<List<MusicSet.Album>>

    @RawQuery(observedEntities = [MusicEntity::class, HiddenFolderEntity::class, AlbumPictureEntity::class])
    fun observeGenresRaw(query: SupportSQLiteQuery): Flow<List<MusicSet.Genre>>

    @RawQuery(observedEntities = [MusicEntity::class, HiddenFolderEntity::class, AlbumPictureEntity::class])
    fun observeFoldersRaw(query: SupportSQLiteQuery): Flow<List<MusicSet.Folder>>

    @RawQuery(observedEntities = [MusicEntity::class, HiddenFolderEntity::class, AlbumPictureEntity::class])
    fun observeAlbumsByArtistRaw(query: SupportSQLiteQuery): Flow<List<MusicSet.Album>>

    @RawQuery(observedEntities = [HiddenFolderEntity::class, MusicEntity::class, AlbumPictureEntity::class])
    fun observeHiddenFoldersRaw(query: SupportSQLiteQuery): Flow<List<MusicSet.Folder>>

    @Query(
        """
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
        """
    )
    fun observeFolders(): Flow<List<MusicSet.Folder>>
}
