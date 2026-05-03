package gd.app.musicplayer.data.db.dao

import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Insert
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import gd.app.musicplayer.data.db.entity.AlbumPictureEntity
import gd.app.musicplayer.data.db.entity.EffectPresetEntity
import gd.app.musicplayer.data.db.entity.EffectTenPresetEntity
import gd.app.musicplayer.data.db.entity.HiddenFolderEntity
import gd.app.musicplayer.data.db.entity.MusicEntity
import gd.app.musicplayer.data.db.entity.MusicPlaylistEntity
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import kotlinx.coroutines.flow.Flow

interface LibraryDao {

    @Upsert
    suspend fun upsertAll(items: List<MusicEntity>)

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

    @Query("SELECT * FROM effect ORDER BY _id ASC")
    suspend fun getEffectPresets(): List<EffectPresetEntity>

    @Query("SELECT * FROM effect_ten ORDER BY _id ASC")
    suspend fun getEffectTenPresets(): List<EffectTenPresetEntity>

    @Query("SELECT * FROM effect WHERE _id = :id LIMIT 1")
    suspend fun getEffectPresetById(id: Long): EffectPresetEntity?

    @Query("SELECT * FROM effect_ten WHERE _id = :id LIMIT 1")
    suspend fun getEffectTenPresetById(id: Long): EffectTenPresetEntity?

    @Insert
    suspend fun insertEffectPreset(item: EffectPresetEntity): Long

    @Insert
    suspend fun insertEffectTenPreset(item: EffectTenPresetEntity): Long

    @Query(
        """
        UPDATE effect
        SET name = :name,
            b1 = :b1,
            b2 = :b2,
            b3 = :b3,
            b4 = :b4,
            b5 = :b5
        WHERE _id = :id
        """
    )
    suspend fun updateEffectPreset(
        id: Long,
        name: String,
        b1: Int,
        b2: Int,
        b3: Int,
        b4: Int,
        b5: Int
    )

    @Query(
        """
        UPDATE effect_ten
        SET name = :name,
            b1 = :b1,
            b2 = :b2,
            b3 = :b3,
            b4 = :b4,
            b5 = :b5,
            b6 = :b6,
            b7 = :b7,
            b8 = :b8,
            b9 = :b9,
            b10 = :b10
        WHERE _id = :id
        """
    )
    suspend fun updateEffectTenPreset(
        id: Long,
        name: String,
        b1: Int,
        b2: Int,
        b3: Int,
        b4: Int,
        b5: Int,
        b6: Int,
        b7: Int,
        b8: Int,
        b9: Int,
        b10: Int
    )

    @Query("DELETE FROM effect WHERE _id = :id")
    suspend fun deleteEffectPresetById(id: Long)

    @Query("DELETE FROM effect_ten WHERE _id = :id")
    suspend fun deleteEffectTenPresetById(id: Long)

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
    suspend fun getAlbumPicturesBySourceId(sourceId: Long): List<String?>

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
    suspend fun getOrphanedPlaylistAlbumPicturePaths(): List<String?>

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
        SET play_time = 0
        """
    )
    suspend fun clearRecentlyPlayedStats()

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

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and artist = :artist
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list
        on music.[_id] = list.[m_id]
        ORDER BY
          CASE WHEN :sortStyle = 'random' THEN RANDOM() END,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 0 THEN music.title END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 1 THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'title_desc' THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 0 THEN COALESCE(music.track, 0) END ASC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 1 THEN COALESCE(music.track, 0) END DESC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 0 THEN COALESCE(music.year, 0) END ASC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 1 THEN COALESCE(music.year, 0) END DESC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 0 THEN music.artist END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 1 THEN music.artist END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 0 THEN music.album END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 1 THEN music.album END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 0 THEN music.folder_path END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 1 THEN music.folder_path END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 0 THEN COALESCE(music.date, 0) END ASC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 1 THEN COALESCE(music.date, 0) END DESC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 0 THEN COALESCE(music.size, 0) END ASC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 1 THEN COALESCE(music.size, 0) END DESC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 0 THEN COALESCE(music.duration, 0) END ASC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 1 THEN COALESCE(music.duration, 0) END DESC,
          music.title COLLATE NOCASE ASC,
          music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksByArtist(
        artist: String,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and album_id = :albumId
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list
        on music.[_id] = list.[m_id]
        ORDER BY
          CASE WHEN :sortStyle = 'random' THEN RANDOM() END,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 0 THEN music.title END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 1 THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'title_desc' THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 0 THEN COALESCE(music.track, 0) END ASC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 1 THEN COALESCE(music.track, 0) END DESC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 0 THEN COALESCE(music.year, 0) END ASC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 1 THEN COALESCE(music.year, 0) END DESC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 0 THEN music.artist END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 1 THEN music.artist END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 0 THEN music.album END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 1 THEN music.album END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 0 THEN music.folder_path END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 1 THEN music.folder_path END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 0 THEN COALESCE(music.date, 0) END ASC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 1 THEN COALESCE(music.date, 0) END DESC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 0 THEN COALESCE(music.size, 0) END ASC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 1 THEN COALESCE(music.size, 0) END DESC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 0 THEN COALESCE(music.duration, 0) END ASC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 1 THEN COALESCE(music.duration, 0) END DESC,
          music.title COLLATE NOCASE ASC,
          music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksByAlbum(
        albumId: Long,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and genres = :genre
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list
        on music.[_id] = list.[m_id]
        ORDER BY
          CASE WHEN :sortStyle = 'random' THEN RANDOM() END,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 0 THEN music.title END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 1 THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'title_desc' THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 0 THEN COALESCE(music.track, 0) END ASC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 1 THEN COALESCE(music.track, 0) END DESC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 0 THEN COALESCE(music.year, 0) END ASC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 1 THEN COALESCE(music.year, 0) END DESC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 0 THEN music.artist END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 1 THEN music.artist END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 0 THEN music.album END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 1 THEN music.album END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 0 THEN music.folder_path END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 1 THEN music.folder_path END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 0 THEN COALESCE(music.date, 0) END ASC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 1 THEN COALESCE(music.date, 0) END DESC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 0 THEN COALESCE(music.size, 0) END ASC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 1 THEN COALESCE(music.size, 0) END DESC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 0 THEN COALESCE(music.duration, 0) END ASC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 1 THEN COALESCE(music.duration, 0) END DESC,
          music.title COLLATE NOCASE ASC,
          music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksByGenre(
        genre: String,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and folder_path = :folderPath
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list
        on music.[_id] = list.[m_id]
        ORDER BY
          CASE WHEN :sortStyle = 'random' THEN RANDOM() END,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 0 THEN music.title END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 1 THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'title_desc' THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 0 THEN COALESCE(music.track, 0) END ASC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 1 THEN COALESCE(music.track, 0) END DESC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 0 THEN COALESCE(music.year, 0) END ASC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 1 THEN COALESCE(music.year, 0) END DESC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 0 THEN music.artist END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 1 THEN music.artist END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 0 THEN music.album END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 1 THEN music.album END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 0 THEN music.folder_path END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 1 THEN music.folder_path END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 0 THEN COALESCE(music.date, 0) END ASC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 1 THEN COALESCE(music.date, 0) END DESC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 0 THEN COALESCE(music.size, 0) END ASC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 1 THEN COALESCE(music.size, 0) END DESC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 0 THEN COALESCE(music.duration, 0) END ASC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 1 THEN COALESCE(music.duration, 0) END DESC,
          music.title COLLATE NOCASE ASC,
          music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksByFolder(
        folderPath: String,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from music_playlist map
        left join (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and folder_path not in (select folder_path from hide_folder)
        ) as music on music.[_id] = map.[m_id]
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list on music.[_id] = list.[m_id]
        where map.[p_id] = :playlistId
          and music._id is not null
        ORDER BY
          CASE WHEN :sortStyle = 'random' THEN RANDOM() END,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 0 THEN music.title END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 1 THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'title_desc' THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 0 THEN COALESCE(music.track, 0) END ASC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 1 THEN COALESCE(music.track, 0) END DESC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 0 THEN COALESCE(music.year, 0) END ASC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 1 THEN COALESCE(music.year, 0) END DESC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 0 THEN music.artist END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 1 THEN music.artist END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 0 THEN music.album END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 1 THEN music.album END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 0 THEN music.folder_path END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 1 THEN music.folder_path END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 0 THEN COALESCE(music.date, 0) END ASC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 1 THEN COALESCE(music.date, 0) END DESC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 0 THEN COALESCE(music.size, 0) END ASC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 1 THEN COALESCE(music.size, 0) END DESC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 0 THEN COALESCE(music.duration, 0) END ASC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 1 THEN COALESCE(music.duration, 0) END DESC,
          music.title COLLATE NOCASE ASC,
          music._id ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeTracksByPlaylist(
        playlistId: Long,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>>


    @RawQuery(observedEntities = [MusicEntity::class, MusicPlaylistEntity::class])
    fun observeRecentlyAddedTracks(
        query: SupportSQLiteQuery
    ): Flow<List<Music>>

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
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and (:playlistWindowMs <= 0 or date > :windowStartMs)
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list on music.[_id] = list.[m_id]
        ORDER BY
          CASE WHEN :sortStyle = 'random' THEN RANDOM() END,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 0 THEN music.title END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'title' AND :sortDescending = 1 THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'title_desc' THEN music.title END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 0 THEN COALESCE(music.track, 0) END ASC,
          CASE WHEN :sortStyle = 'track' AND :sortDescending = 1 THEN COALESCE(music.track, 0) END DESC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 0 THEN COALESCE(music.year, 0) END ASC,
          CASE WHEN :sortStyle = 'year' AND :sortDescending = 1 THEN COALESCE(music.year, 0) END DESC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 0 THEN music.artist END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'artist' AND :sortDescending = 1 THEN music.artist END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 0 THEN music.album END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'album' AND :sortDescending = 1 THEN music.album END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 0 THEN music.folder_path END COLLATE NOCASE ASC,
          CASE WHEN :sortStyle = 'folder' AND :sortDescending = 1 THEN music.folder_path END COLLATE NOCASE DESC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 0 THEN COALESCE(music.date, 0) END ASC,
          CASE WHEN :sortStyle = 'date' AND :sortDescending = 1 THEN COALESCE(music.date, 0) END DESC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 0 THEN COALESCE(music.size, 0) END ASC,
          CASE WHEN :sortStyle = 'size' AND :sortDescending = 1 THEN COALESCE(music.size, 0) END DESC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 0 THEN COALESCE(music.duration, 0) END ASC,
          CASE WHEN :sortStyle = 'duration' AND :sortDescending = 1 THEN COALESCE(music.duration, 0) END DESC,
          music.title COLLATE NOCASE ASC,
          music._id ASC
        LIMIT :playlistLimit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeRecentlyAddedTracksLimited(
        sortStyle: String,
        sortDescending: Boolean,
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and (
              (:playlistWindowMs > 0 and play_time > :windowStartMs)
              or
              (:playlistWindowMs <= 0 and play_time > 0)
            )
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list on music.[_id] = list.[m_id]
        order by play_time desc, title
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeRecentlyPlayedTracks(
        playlistWindowMs: Long,
        windowStartMs: Long
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and (
              (:playlistWindowMs > 0 and play_time > :windowStartMs)
              or
              (:playlistWindowMs <= 0 and play_time > 0)
            )
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list on music.[_id] = list.[m_id]
        order by play_time desc, title
        LIMIT :playlistLimit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeRecentlyPlayedTracksLimited(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and count > 0
            and (
              :playlistWindowMs <= 0
              or
              play_time > :windowStartMs
            )
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list on music.[_id] = list.[m_id]
        order by count desc, play_time desc
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeMostPlayedTracks(
        playlistWindowMs: Long,
        windowStartMs: Long
    ): Flow<List<Music>>

    @Query(
        """
        select music.*, list.p_id as p_id
        from (
          select * from musictbl
          where hide_time = 0
            and show = 1
            and count > 0
            and (
              :playlistWindowMs <= 0
              or
              play_time > :windowStartMs
            )
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join (
          select DISTINCT([m_id]), [p_id]
          from music_playlist
          where music_playlist.p_id = 1
        ) as list on music.[_id] = list.[m_id]
        order by count desc, play_time desc
        LIMIT :playlistLimit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeMostPlayedTracksLimited(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<List<Music>>

    @Query(
        """
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
        """
    )
    fun observeArtists(): Flow<List<MusicSet.Artist>>

    @Query(
        """
        select album name,
               count(album) musicCount,
               max(album_id) id,
               a.s_pic albumArt,
               :artist artist,
               max(year) year,
               max(date) date,
               COALESCE(max(genres), '') genres
        from (
          select * from musictbl
          where hide_time = 0
            and artist = :artist
            and show = 1
            and folder_path not in (select folder_path from hide_folder)
        ) as music
        left join album_picture a
          on music.album = a.s_name
         and a.s_id = -5
        group by album
        """
    )
    fun observeAlbumsByArtist(artist: String): Flow<List<MusicSet.Album>>

    @Query(
        """
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
        """
    )
    fun observeAlbums(): Flow<List<MusicSet.Album>>

    @Query(
        """
        select genres name, count(genres) musicCount, max(album_id) id, a.s_pic albumArt
        from (select * from musictbl where hide_time = 0 and show = 1 and folder_path not in (select folder_path from hide_folder)) as music
        left join album_picture a on music.genres = a.s_name and a.s_id = -8
        group by genres
        """
    )
    fun observeGenres(): Flow<List<MusicSet.Genre>>

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
