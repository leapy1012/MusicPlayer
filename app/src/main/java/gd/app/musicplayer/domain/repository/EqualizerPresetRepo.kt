package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.data.local.db.dao.MusicDao
import gd.app.musicplayer.data.local.db.entity.EffectPresetEntity
import gd.app.musicplayer.data.local.db.entity.EffectTenPresetEntity
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerPresetRecord(
    val id: Long,
    val name: String,
    val bands: List<Int>,
    val preset: Int
) {
    val canDelete: Boolean
        get() = preset != FACTORY_PRESET

    fun bandsArray(): IntArray {
        return bands.toIntArray()
    }

    companion object {
        const val USER_CREATED_PRESET = 0
        const val FACTORY_PRESET = 1
    }
}

@Singleton
class EqualizerPresetRepository @Inject constructor(
    private val musicDao: MusicDao
) {

    suspend fun list(tenBand: Boolean): List<EqualizerPresetRecord> {
        return if (tenBand) {
            musicDao.getEffectTenPresets().map { entity ->
                entity.toRecord()
            }
        } else {
            musicDao.getEffectPresets().map { entity ->
                entity.toRecord()
            }
        }
    }

    suspend fun getById(
        id: Long,
        tenBand: Boolean
    ): EqualizerPresetRecord? {
        return if (tenBand) {
            musicDao.getEffectTenPresetById(id)?.toRecord()
        } else {
            musicDao.getEffectPresetById(id)?.toRecord()
        }
    }

    suspend fun insert(
        name: String,
        bands: List<Int>,
        tenBand: Boolean,
        preset: Int = EqualizerPresetRecord.USER_CREATED_PRESET
    ): Long {
        val normalizedBands = bands.normalizedBandLevels(tenBand)

        return if (tenBand) {
            musicDao.insertEffectTenPreset(
                normalizedBands.toTenBandEntity(
                    name = name,
                    preset = preset
                )
            )
        } else {
            musicDao.insertEffectPreset(
                normalizedBands.toFiveBandEntity(
                    name = name,
                    preset = preset
                )
            )
        }
    }

    suspend fun update(
        id: Long,
        name: String,
        bands: List<Int>,
        tenBand: Boolean
    ) {
        val normalizedBands = bands.normalizedBandLevels(tenBand)

        if (tenBand) {
            musicDao.updateEffectTenPreset(
                id = id,
                name = name,
                b1 = normalizedBands[0],
                b2 = normalizedBands[1],
                b3 = normalizedBands[2],
                b4 = normalizedBands[3],
                b5 = normalizedBands[4],
                b6 = normalizedBands[5],
                b7 = normalizedBands[6],
                b8 = normalizedBands[7],
                b9 = normalizedBands[8],
                b10 = normalizedBands[9]
            )
        } else {
            musicDao.updateEffectPreset(
                id = id,
                name = name,
                b1 = normalizedBands[0],
                b2 = normalizedBands[1],
                b3 = normalizedBands[2],
                b4 = normalizedBands[3],
                b5 = normalizedBands[4]
            )
        }
    }

    suspend fun delete(
        id: Long,
        tenBand: Boolean
    ) {
        if (tenBand) {
            musicDao.deleteEffectTenPresetById(id)
        } else {
            musicDao.deleteEffectPresetById(id)
        }
    }

    private fun EffectPresetEntity.toRecord(): EqualizerPresetRecord {
        return EqualizerPresetRecord(
            id = id,
            name = name,
            bands = listOf(b1, b2, b3, b4, b5),
            preset = preset
        )
    }

    private fun EffectTenPresetEntity.toRecord(): EqualizerPresetRecord {
        return EqualizerPresetRecord(
            id = id,
            name = name,
            bands = listOf(b1, b2, b3, b4, b5, b6, b7, b8, b9, b10),
            preset = preset
        )
    }

    private fun List<Int>.toFiveBandEntity(
        name: String,
        preset: Int
    ): EffectPresetEntity {
        return EffectPresetEntity(
            name = name,
            b1 = this[0],
            b2 = this[1],
            b3 = this[2],
            b4 = this[3],
            b5 = this[4],
            preset = preset
        )
    }

    private fun List<Int>.toTenBandEntity(
        name: String,
        preset: Int
    ): EffectTenPresetEntity {
        return EffectTenPresetEntity(
            name = name,
            b1 = this[0],
            b2 = this[1],
            b3 = this[2],
            b4 = this[3],
            b5 = this[4],
            b6 = this[5],
            b7 = this[6],
            b8 = this[7],
            b9 = this[8],
            b10 = this[9],
            preset = preset
        )
    }

    private fun List<Int>.normalizedBandLevels(
        tenBand: Boolean
    ): List<Int> {
        val requiredSize = if (tenBand) TEN_BAND_COUNT else FIVE_BAND_COUNT

        return List(requiredSize) { index ->
            getOrElse(index) { DEFAULT_BAND_LEVEL }
                .coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL)
        }
    }

    private companion object {
        const val FIVE_BAND_COUNT = 5
        const val TEN_BAND_COUNT = 10

        const val DEFAULT_BAND_LEVEL = 0

        const val MIN_BAND_LEVEL = -1500
        const val MAX_BAND_LEVEL = 1500
    }
}
