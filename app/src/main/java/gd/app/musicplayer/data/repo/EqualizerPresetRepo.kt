package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.db.entity.EffectPresetEntity
import gd.app.musicplayer.data.db.entity.EffectTenPresetEntity

data class EqualizerPresetRecord(
    val id: Long,
    val name: String,
    val bands: IntArray,
    val preset: Int
)

class EqualizerPresetRepo(
    private val musicDao: MusicDao
) {
    suspend fun list(tenBand: Boolean): List<EqualizerPresetRecord> {
        return if (tenBand) {
            musicDao.getEffectTenPresets().map {
                EqualizerPresetRecord(
                    id = it.id,
                    name = it.name,
                    bands = intArrayOf(it.b1, it.b2, it.b3, it.b4, it.b5, it.b6, it.b7, it.b8, it.b9, it.b10),
                    preset = it.preset
                )
            }
        } else {
            musicDao.getEffectPresets().map {
                EqualizerPresetRecord(
                    id = it.id,
                    name = it.name,
                    bands = intArrayOf(it.b1, it.b2, it.b3, it.b4, it.b5),
                    preset = it.preset
                )
            }
        }
    }

    suspend fun getById(id: Long, tenBand: Boolean): EqualizerPresetRecord? {
        return if (tenBand) {
            musicDao.getEffectTenPresetById(id)?.let {
                EqualizerPresetRecord(
                    id = it.id,
                    name = it.name,
                    bands = intArrayOf(it.b1, it.b2, it.b3, it.b4, it.b5, it.b6, it.b7, it.b8, it.b9, it.b10),
                    preset = it.preset
                )
            }
        } else {
            musicDao.getEffectPresetById(id)?.let {
                EqualizerPresetRecord(
                    id = it.id,
                    name = it.name,
                    bands = intArrayOf(it.b1, it.b2, it.b3, it.b4, it.b5),
                    preset = it.preset
                )
            }
        }
    }

    suspend fun insert(name: String, bands: IntArray, tenBand: Boolean, preset: Int): Long {
        return if (tenBand) {
            musicDao.insertEffectTenPreset(
                EffectTenPresetEntity(
                    name = name,
                    b1 = bands.getOrElse(0) { 0 },
                    b2 = bands.getOrElse(1) { 0 },
                    b3 = bands.getOrElse(2) { 0 },
                    b4 = bands.getOrElse(3) { 0 },
                    b5 = bands.getOrElse(4) { 0 },
                    b6 = bands.getOrElse(5) { 0 },
                    b7 = bands.getOrElse(6) { 0 },
                    b8 = bands.getOrElse(7) { 0 },
                    b9 = bands.getOrElse(8) { 0 },
                    b10 = bands.getOrElse(9) { 0 },
                    preset = preset
                )
            )
        } else {
            musicDao.insertEffectPreset(
                EffectPresetEntity(
                    name = name,
                    b1 = bands.getOrElse(0) { 0 },
                    b2 = bands.getOrElse(1) { 0 },
                    b3 = bands.getOrElse(2) { 0 },
                    b4 = bands.getOrElse(3) { 0 },
                    b5 = bands.getOrElse(4) { 0 },
                    preset = preset
                )
            )
        }
    }

    suspend fun update(id: Long, name: String, bands: IntArray, tenBand: Boolean) {
        if (tenBand) {
            musicDao.updateEffectTenPreset(
                id = id,
                name = name,
                b1 = bands.getOrElse(0) { 0 },
                b2 = bands.getOrElse(1) { 0 },
                b3 = bands.getOrElse(2) { 0 },
                b4 = bands.getOrElse(3) { 0 },
                b5 = bands.getOrElse(4) { 0 },
                b6 = bands.getOrElse(5) { 0 },
                b7 = bands.getOrElse(6) { 0 },
                b8 = bands.getOrElse(7) { 0 },
                b9 = bands.getOrElse(8) { 0 },
                b10 = bands.getOrElse(9) { 0 }
            )
        } else {
            musicDao.updateEffectPreset(
                id = id,
                name = name,
                b1 = bands.getOrElse(0) { 0 },
                b2 = bands.getOrElse(1) { 0 },
                b3 = bands.getOrElse(2) { 0 },
                b4 = bands.getOrElse(3) { 0 },
                b5 = bands.getOrElse(4) { 0 }
            )
        }
    }

    suspend fun delete(id: Long, tenBand: Boolean) {
        if (tenBand) {
            musicDao.deleteEffectTenPresetById(id)
        } else {
            musicDao.deleteEffectPresetById(id)
        }
    }
}
