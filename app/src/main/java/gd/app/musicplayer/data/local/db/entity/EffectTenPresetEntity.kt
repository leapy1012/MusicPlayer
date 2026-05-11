package gd.app.musicplayer.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "effect_ten")
data class EffectTenPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    val name: String,
    val b1: Int = 0,
    val b2: Int = 0,
    val b3: Int = 0,
    val b4: Int = 0,
    val b5: Int = 0,
    val b6: Int = 0,
    val b7: Int = 0,
    val b8: Int = 0,
    val b9: Int = 0,
    val b10: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val preset: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val sort: Int = 0
)
