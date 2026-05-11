package gd.app.musicplayer.domain.usecase.database

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.local.mediastore.MediaStoreMusicImporter
import gd.app.musicplayer.data.local.db.MusicDatabase
import gd.app.musicplayer.data.local.db.MusicDatabaseSeedProvider
import gd.app.musicplayer.data.local.db.MusicDatabaseSeeder
import gd.app.musicplayer.data.local.preference.AppStartupPreferenceDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunMusicDatabaseStartupSyncUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val seedProvider: MusicDatabaseSeedProvider,
    private val appStartupPreferenceDataStore: AppStartupPreferenceDataStore
) {

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

        MusicDatabaseSeeder(context, seedProvider)
            .reseedEffectPresets(database.openHelper.writableDatabase)

        appStartupPreferenceDataStore.setEffectPresetSchemaVersion(
            EFFECT_PRESET_SCHEMA_VERSION
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun importMusicOnFirstStartIfNeeded() {
        if (!appStartupPreferenceDataStore.isFirstStart()) {
            return
        }

        val music = MediaStoreMusicImporter().queryMusic(context)
        database.musicDao().upsertAll(music)

        appStartupPreferenceDataStore.setFirstStart(false)
    }

    private companion object {
        const val EFFECT_PRESET_SCHEMA_VERSION = 2
    }
}