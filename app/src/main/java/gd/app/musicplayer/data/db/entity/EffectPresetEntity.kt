package gd.app.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "effect")
data class EffectPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    val name: String,
    val b1: Int,
    val b2: Int,
    val b3: Int,
    val b4: Int,
    val b5: Int,
    @ColumnInfo(defaultValue = "0")
    val sort: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val preset: Int = 0
)
