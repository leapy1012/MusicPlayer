package gd.app.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "musictbl")
data class MusicEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: Long,
    val title: String,
    val data: String,
    val size: Long? = null,
    val duration: Int,
    val album: String? = null,
    @ColumnInfo(name = "album_pic")
    val albumPicture: String? = null,
    @ColumnInfo(name = "album_id")
    val albumId: Long? = null,
    @ColumnInfo(name = "folder_path")
    val folderPath: String? = null,
    @ColumnInfo(name = "folder_name")
    val folderName: String? = null,
    val date: Long? = null,
    @ColumnInfo(name = "date_modified")
    val dateModified: Long? = null,
    val year: Int? = null,
    val artist: String? = null,
    @ColumnInfo(name = "artist_pic")
    val artistPicture: String? = null,
    @ColumnInfo(name = "play_time", defaultValue = "0")
    val playTime: Long = 0,
    @ColumnInfo(defaultValue = "'Unknown'")
    val genres: String = "Unknown",
    @ColumnInfo(name = "is_ringtone", defaultValue = "0")
    val isRingtone: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val count: Int = 0,
    val lrc: String? = null,
    @ColumnInfo(name = "lrc_offset", defaultValue = "0")
    val lyricOffset: Int = 0,
    @ColumnInfo(name = "show", defaultValue = "1")
    val visible: Int = 1,
    @ColumnInfo(name = "state_time", defaultValue = "0")
    val stateTime: Long = 0,
    @ColumnInfo(name = "hide_time", defaultValue = "0")
    val hideTime: Long = 0,
    @ColumnInfo(defaultValue = "-1")
    val track: Int = -1,
    @ColumnInfo(name = "bit_rate", defaultValue = "-1")
    val bitRate: Int = -1,
    @ColumnInfo(name = "sample_rate", defaultValue = "-1")
    val sampleRate: Int = -1,
    @ColumnInfo(defaultValue = "0")
    val sort: Int = 0
)
