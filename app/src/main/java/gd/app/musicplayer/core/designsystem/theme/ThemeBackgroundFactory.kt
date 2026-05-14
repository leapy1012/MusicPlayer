package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.designsystem.drawable.OverlayCenterCropDrawable
import gd.app.musicplayer.data.local.preference.ThemeSettingPreferenceStore
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.drawable.toDrawable

@Singleton
class ThemeBackgroundFactory @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val themeSettingPreferenceStore: ThemeSettingPreferenceStore,
    private val themeBitmapLoader: ThemeBitmapLoader
) {

    suspend fun createActivityBackground(): BitmapDrawable {
        val settings = themeSettingPreferenceStore.getSettingsSnapshot()

        return OverlayCenterCropDrawable(
            appContext.resources,
            themeBitmapLoader.loadBitmap(
                context = appContext,
                imageName = settings.imageName,
                blurRadius = settings.blur
            ),
            settings.overlayColor
        )
    }

    suspend fun createBlurBackground(): BitmapDrawable? {
        val settings = themeSettingPreferenceStore.getSettingsSnapshot()
        val bitmap = themeBitmapLoader.loadBlurBackgroundBitmap(
            context = appContext,
            imageName = settings.imageName
        ) ?: return null

        return bitmap.toDrawable(appContext.resources)
    }
}