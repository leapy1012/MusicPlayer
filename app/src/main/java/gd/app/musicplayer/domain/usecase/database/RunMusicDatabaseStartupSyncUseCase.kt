package gd.app.musicplayer.domain.usecase.database

import android.os.Build
import androidx.annotation.RequiresApi
import gd.app.musicplayer.core.datastore.AppStartupPreferenceDataStore
import gd.app.musicplayer.domain.repository.DatabaseStartupGateway
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunMusicDatabaseStartupSyncUseCase @Inject constructor(
    private val appStartupPreferenceDataStore: AppStartupPreferenceDataStore,
    private val databaseStartupGateway: DatabaseStartupGateway
) {

    @RequiresApi(Build.VERSION_CODES.R)
    suspend operator fun invoke() {
        reseedEffectPresetsIfNeeded()
        importMusicOnFirstStartIfNeeded()
    }

    private suspend fun reseedEffectPresetsIfNeeded() {
        val currentSchemaVersion =
            appStartupPreferenceDataStore.getEffectPresetSchemaVersion()

        if (currentSchemaVersion >= EFFECT_PRESET_SCHEMA_VERSION) {
            return
        }

        databaseStartupGateway.reseedEffectPresets()

        appStartupPreferenceDataStore.setEffectPresetSchemaVersion(
            EFFECT_PRESET_SCHEMA_VERSION
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun importMusicOnFirstStartIfNeeded() {
        if (!appStartupPreferenceDataStore.isFirstStart()) {
            return
        }

        databaseStartupGateway.importAllMusic()
        appStartupPreferenceDataStore.setFirstStart(false)
    }

    private companion object {
        const val EFFECT_PRESET_SCHEMA_VERSION = 2
    }
}
