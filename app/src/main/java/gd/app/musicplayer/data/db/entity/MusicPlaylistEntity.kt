package gd.app.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_playlist",
    indices = [
        Index(value = ["m_id"]),
        Index(value = ["p_id"]),
        Index(value = ["m_id", "p_id"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = MusicEntity::class,
            parentColumns = ["_id"],
            childColumns = ["m_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["_id"],
            childColumns = ["p_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
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
