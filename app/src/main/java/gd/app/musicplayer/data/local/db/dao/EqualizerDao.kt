package gd.app.musicplayer.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import gd.app.musicplayer.data.local.db.entity.EffectPresetEntity
import gd.app.musicplayer.data.local.db.entity.EffectTenPresetEntity

@Dao
interface EqualizerDao {

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
}