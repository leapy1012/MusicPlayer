package gd.app.musicplayer.domain.usecase.database

import gd.app.musicplayer.core.datastore.AppStartupPreferenceDataStore
import gd.app.musicplayer.core.mediastore.MediaStoreLibraryObserver
import gd.app.musicplayer.domain.repository.DatabaseStartupGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cold-start bootstrap matching original Welcome / [BMusicActivity.d1]:
 * - schedule MediaStore sync (non-blocking)
 * - run only light one-shot DB work on the splash path
 */
@Singleton
class RunMusicDatabaseStartupSyncUseCase @Inject constructor(
    private val appStartupPreferenceDataStore: AppStartupPreferenceDataStore,
    private val databaseStartupGateway: DatabaseStartupGateway,
    private val mediaStoreLibraryObserver: MediaStoreLibraryObserver
) {

    suspend operator fun invoke() {
        // Original m.c().f(): schedule only — never await MediaStore → DB.
        mediaStoreLibraryObserver.scheduleSync()

        reseedEffectPresetsIfNeeded()

        if (appStartupPreferenceDataStore.isFirstStart()) {
            appStartupPreferenceDataStore.setFirstStart(false)
        }
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

    private companion object {
        const val EFFECT_PRESET_SCHEMA_VERSION = 2
    }
}
