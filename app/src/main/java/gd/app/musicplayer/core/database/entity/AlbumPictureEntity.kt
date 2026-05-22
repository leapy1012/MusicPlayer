package gd.app.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "album_picture")
data class AlbumPictureEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "s_id")
    val sourceId: Long,
    @ColumnInfo(name = "s_name")
    val sourceName: String,
    @ColumnInfo(name = "s_pic")
    val sourcePicture: String? = null
)
