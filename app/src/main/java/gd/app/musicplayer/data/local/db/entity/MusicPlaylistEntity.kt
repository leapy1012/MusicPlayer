package gd.app.musicplayer.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "music_playlist")
data class MusicPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "m_id")
    val musicId: Long,
    @ColumnInfo(name = "p_id")
    val playlistId: Long,
    @ColumnInfo(defaultValue = "0")
    val sort: Int = 0
)
