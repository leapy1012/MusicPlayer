package gd.app.musicplayer.domain.model

import androidx.room.ColumnInfo
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Music(
    @ColumnInfo("_id")
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo("album_id")
    val albumId: String,
    @ColumnInfo("p_id")
    val playlistId: Long,
    @ColumnInfo(name = "album_pic")
    val albumPicture: String? = null,
    val data: String? = null,
    val size: Long? = null,
    val duration: Int = 0,
    @ColumnInfo("folder_path")
    val folderPath: String? = null,
    val date: Long? = null,
    @ColumnInfo(name = "play_time")
    val playTime: Long? = null,
    @ColumnInfo(name = "count")
    val playCount: Int = 0,
    val year: Int? = null
) : Parcelable
