package gd.app.musicplayer.core.theme

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import gd.app.musicplayer.R
import androidx.core.graphics.drawable.toDrawable

class DarkThemePalette : PictureThemePalette() {
    override fun getHeaderOverlayColor(): Int = 855638016

    override fun ensureResourcesLoaded(context: Context): Boolean = true

    override fun getActivityBackgroundDrawable(context: Context): Drawable =
        DARK_SURFACE.toDrawable()

    override fun isActionAreaLight(): Boolean = false

    override fun isDarkMode(): Boolean = true

    override fun getPopupBackgroundDrawable(context: Context): Drawable =
        AppCompatResources.getDrawable(context, R.drawable.popup_bg_night) ?: DARK_SURFACE.toDrawable()

    override fun getHeaderBackgroundDrawable(context: Context): Drawable =
        getHeaderOverlayColor().toDrawable()

    override fun isNightTheme(): Boolean = true

    override fun getThemeType(): Int = ThemeManager.THEME_TYPE_DARK

    override fun getDialogSurfaceDrawable(context: Context): Drawable =
        AppCompatResources.getDrawable(context, R.drawable.popup_bg_night)
            ?: DARK_SURFACE.toDrawable()

    override fun isPopupSurfaceLight(): Boolean = false

    override fun isHeaderSurfaceLight(): Boolean = false

    override fun isDialogSurfaceLight(): Boolean = false

    override fun isContentSurfaceLight(): Boolean = false

    override fun getBlurredBackgroundDrawable(context: Context): Drawable =
        DARK_SURFACE.toDrawable()

    private companion object {
        private const val DARK_SURFACE = -14540254
    }
}
