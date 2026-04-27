package gd.app.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hide_folder",
    indices = [Index(value = ["folder_path"], unique = true)]
)
data class HiddenFolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "folder_path")
    val folderPath: String
)
