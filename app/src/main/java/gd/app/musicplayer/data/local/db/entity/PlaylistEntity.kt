package gd.app.musicplayer.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val sort: Int = 0,
    @ColumnInfo(name = "setup_time")
    val setupTime: Long
)
