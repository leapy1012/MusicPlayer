package gd.app.musicplayer.data.repo

import android.content.Context
import gd.app.musicplayer.R
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.ui.feature.scan.ScanOptions
import gd.app.musicplayer.util.LibraryTabConfig
import gd.app.musicplayer.util.PreferenceKeys
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.ThemePreferenceOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ThemeSettings(
    val imageName: String,
    val customImageNames: List<String>,
    val accentColor: Int,
    val overlayColor: Int,
    val blur: Int
)

class UserPreferencesRepo(
    private val context: Context,
    private val preferenceUtil: PreferenceUtil
) {
    fun observePreferenceChanges(vararg keys: String): Flow<Unit> =
        preferenceUtil.observePreferenceChanges(*keys)

    fun getLibraryLastTab(): Int =
        preferenceUtil.getLibraryLastTab()

    fun setLibraryLastTab(tab: Int) {
        preferenceUtil.setLibraryLastTab(tab)
    }

    fun getLibraryTabConfigs(): List<LibraryTabConfig> =
        preferenceUtil.getLibraryTabConfigs()

    fun setLibraryTabConfigs(items: List<LibraryTabConfig>) {
        preferenceUtil.setLibraryTabConfigs(items)
    }

    fun getPlayMode(): Int =
        preferenceUtil.getPlayMode()

    fun cyclePlayMode(): Int {
        val nextMode = when (preferenceUtil.getPlayMode()) {
            PLAY_MODE_SINGLE -> PLAY_MODE_ORDER
            PLAY_MODE_ORDER -> PLAY_MODE_LOOP_ALL
            PLAY_MODE_LOOP_ALL -> PLAY_MODE_SHUFFLE_ALL
            else -> PLAY_MODE_SINGLE
        }
        preferenceUtil.setPlayMode(nextMode)
        return nextMode
    }

    fun shouldShowHiddenFolders(): Boolean =
        preferenceUtil.getBooleanPreference(PreferenceKeys.KEY_SHOW_HIDDEN_FOLDERS, true)

    fun getEqualizerPresetName(): String {
        val settings = AudioEffectsManager.loadSettings(context)
        if (!settings.eqEnabled) return context.getString(gd.app.musicplayer.R.string.close)

        val defaultPresetNames = context.resources.getStringArray(R.array.eq_presetName).toList()
        val userPresets = AudioEffectsManager.readUserPresets(context, settings.useTenBand)
        val names = buildList {
            addAll(defaultPresetNames)
            addAll(userPresets.map { it.name })
        }
        return names.getOrElse(settings.selectedPresetIndex().coerceAtLeast(0)) {
            defaultPresetNames.firstOrNull().orEmpty()
        }
    }

    fun observeThemeSettings(): Flow<ThemeSettings> =
        observePreferenceChanges(
            KEY_THEME_IMAGE_NAME,
            ThemePreferenceOps.KEY_THEME_SKIN_URIS,
            ThemePreferenceOps.KEY_THEME_COLOR,
            ThemePreferenceOps.KEY_THEME_OVERLAY_COLOR,
            ThemePreferenceOps.KEY_THEME_BLUR
        ).map { getThemeSettings() }

    fun getThemeSettings(): ThemeSettings =
        ThemeSettings(
            imageName = preferenceUtil.getThemeImageName(),
            customImageNames = preferenceUtil.getThemeImageUris(),
            accentColor = preferenceUtil.getThemeColor(),
            overlayColor = preferenceUtil.getThemeOverlayColor(),
            blur = preferenceUtil.getThemeBlur()
        )

    fun setThemeImageName(fileName: String) {
        preferenceUtil.setThemeImageName(fileName)
    }

    fun addThemeImageName(fileName: String) {
        preferenceUtil.addThemeImageUri(fileName)
    }

    fun setThemeOverlayColor(color: Int) {
        preferenceUtil.setThemeOverlayColor(color)
    }

    fun setThemeBlur(blur: Int) {
        preferenceUtil.setThemeBlur(blur)
    }

    fun loadScanOptions() = ScanOptions(
        excludeShort = preferenceUtil.ignoreShortTracksUnder60Seconds(),
        excludeBySize = preferenceUtil.shouldExcludeMusicBySize(),
        excludeRingtone = preferenceUtil.shouldIgnoreRingtones(),
        durationSec = maxOf(1, preferenceUtil.getExcludedMusicDurationMs() / 1000),
        sizeKb = maxOf(1, preferenceUtil.getExcludedMusicSizeBytes() / 1024)
    )

    fun persistScanOptions(options: ScanOptions) {
        preferenceUtil.setIgnoreShortTracksUnder60Seconds(options.excludeShort)
        preferenceUtil.setExcludeMusicBySize(options.excludeBySize)
        preferenceUtil.setIgnoreRingtones(options.excludeRingtone)
        preferenceUtil.setExcludedMusicDurationMs(options.durationSec * 1000)
        preferenceUtil.setExcludedMusicSizeBytes(options.sizeKb * 1024)
    }

    private companion object {
        const val PLAY_MODE_SINGLE = 0
        const val PLAY_MODE_ORDER = 1
        const val PLAY_MODE_LOOP_ALL = 2
        const val PLAY_MODE_SHUFFLE_ALL = 3
        const val KEY_THEME_IMAGE_NAME = ThemePreferenceOps.KEY_IMAGE_NAME
    }
}
