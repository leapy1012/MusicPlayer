package gd.app.musicplayer.data.db

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class MusicDatabaseSeeder(
    private val context: Context,
    private val seedProvider: MusicDatabaseSeedProvider
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedPlaylists(db)
        reseedEffectPresets(db)
    }

    fun reseedEffectPresets(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM effect WHERE preset = 1")
            db.execSQL("DELETE FROM effect_ten WHERE preset = 1")
            seedFiveBandEffects(db)
            seedTenBandEffects(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedPlaylists(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        seedProvider.defaultPlaylists().forEach { name ->
            db.execSQL(
                "INSERT INTO playlist(name, setup_time) VALUES(?, ?)",
                arrayOf<Any>(name, now)
            )
        }
    }

    private fun seedFiveBandEffects(db: SupportSQLiteDatabase) {
        seedProvider.fiveBandPresets().forEach { preset ->
            require(preset.bands.size == 5) { "Five-band preset must contain exactly 5 values." }
            db.execSQL(
                """
                INSERT INTO effect(name, b1, b2, b3, b4, b5, preset)
                VALUES(?, ?, ?, ?, ?, ?, 1)
                """.trimIndent(),
                arrayOf<Any>(
                    preset.name,
                    preset.bands[0],
                    preset.bands[1],
                    preset.bands[2],
                    preset.bands[3],
                    preset.bands[4]
                )
            )
        }
    }

    private fun seedTenBandEffects(db: SupportSQLiteDatabase) {
        seedProvider.tenBandPresets().forEach { preset ->
            require(preset.bands.size == 10) { "Ten-band preset must contain exactly 10 values." }
            db.execSQL(
                """
                INSERT INTO effect_ten(name, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, preset)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """.trimIndent(),
                arrayOf<Any>(
                    preset.name,
                    preset.bands[0],
                    preset.bands[1],
                    preset.bands[2],
                    preset.bands[3],
                    preset.bands[4],
                    preset.bands[5],
                    preset.bands[6],
                    preset.bands[7],
                    preset.bands[8],
                    preset.bands[9]
                )
            )
        }
    }
}
