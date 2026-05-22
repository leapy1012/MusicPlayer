package gd.app.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DatabaseMaintenanceDao {

    @Query("SELECT COUNT(*) FROM playlist")
    suspend fun getPlaylistCount(): Int

    @Query("SELECT COUNT(*) FROM effect")
    suspend fun getEffectPresetCount(): Int

    @Query("SELECT COUNT(*) FROM effect_ten")
    suspend fun getTenBandEffectPresetCount(): Int
}
