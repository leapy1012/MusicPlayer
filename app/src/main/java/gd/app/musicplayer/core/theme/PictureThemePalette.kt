package gd.app.musicplayer.core.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.drawable.DialogBackgroundFactory
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.ui.drawable.OverlayCenterCropDrawable
import gd.app.musicplayer.core.ui.drawable.RoundedMaskDrawable
import gd.app.musicplayer.util.ThemePreferenceOps

open class PictureThemePalette : BaseThemePalette() {
    protected var themeAccentColor: Int = 0
    protected var themeImageName: String = ThemePreferenceOps.DEFAULT_THEME_IMAGE
    private var backgroundBitmap: Bitmap? = null
    private var blurredBitmap: Bitmap? = null
    private var themeBlurAmount: Int = 0
    private var themeBackgroundOverlayColor: Int = 855638016

    override fun getHeaderOverlayColor(): Int = if (isHeaderSurfaceLight()) 0 else 855638016

    override fun ensureResourcesLoaded(context: Context): Boolean {
        if (backgroundBitmap == null) {
            backgroundBitmap = DrawableUtil.loadBitmap(context, themeImageName, themeBlurAmount)
        }
        if (blurredBitmap == null) {
            blurredBitmap = DrawableUtil.loadBlurBackgroundBitmap(context, themeImageName)
        }
        return backgroundBitmap != null
    }

    override fun getActivityBackgroundDrawable(context: Context): Drawable =
        OverlayCenterCropDrawable(context.resources, backgroundBitmap, themeBackgroundOverlayColor)

    override fun isActionAreaLight(): Boolean = true

    override fun getPopupPressedOverlayColor(): Int = 436207616

    override fun getPopupBackgroundDrawable(context: Context): Drawable =
        AppCompatResources.getDrawable(context, R.drawable.popup_bg) ?: Color.WHITE.toDrawable()

    override fun getHeaderBackgroundDrawable(context: Context): Drawable =
        if (isHeaderSurfaceLight()) {
            RoundedMaskDrawable(ColorDrawable(637534208), context.resources.displayMetrics.density * 1.5f)
        } else {
            getHeaderOverlayColor().toDrawable()
        }

    override fun isNightTheme(): Boolean = false

    override fun getThemeType(): Int = ThemeManager.THEME_TYPE_PICTURE

    override fun getDefaultAccentColor(): Int = -12467

    override fun getDialogSurfaceDrawable(context: Context): Drawable =
        DialogBackgroundFactory.pictureDialogBackground(context, blurredBitmap)

    override fun setAccentColor(accentColor: Int) {
        themeAccentColor = accentColor
    }

    override fun isPopupSurfaceLight(): Boolean = isDialogSurfaceLight()

    override fun isHeaderSurfaceLight(): Boolean = isContentSurfaceLight()

    override fun getRecyclerDividerColor(): Int = if (isContentSurfaceLight()) 218103808 else 234881023

    override fun isDialogSurfaceLight(): Boolean = blurredBitmap == null || isContentSurfaceLight()

    override fun isContentSurfaceLight(): Boolean = false

    override fun getAccentColor(): Int = themeAccentColor

    override fun getBlurredBackgroundDrawable(context: Context): Drawable {
        return blurredBitmap?.toDrawable(context.resources) ?: 0xFFF9F9F9.toInt().toDrawable()
//        val bitmap = blurredBitmap ?: return 0xFFF9F9F9.toInt().toDrawable()
//        return blurredBitmap.toDrawable(context.resources)
//        return DialogBackgroundFactory.pictureDialogBackground(context, bitmap)
    }

    fun setImageName(imageName: String?) {
        themeImageName = imageName?.takeUnless(String::isBlank) ?: ThemePreferenceOps.DEFAULT_THEME_IMAGE
    }

    fun getImageName(): String = themeImageName

    fun getBlurAmount(): Int = themeBlurAmount

    fun setBlurAmount(blurAmount: Int) {
        themeBlurAmount = blurAmount
    }

    fun getBackgroundOverlayColor(): Int = themeBackgroundOverlayColor

    fun setBackgroundOverlayColor(backgroundOverlayColor: Int) {
        themeBackgroundOverlayColor = backgroundOverlayColor
    }

    fun copyAsThemeType(themeType: Int, reuseBitmaps: Boolean): PictureThemePalette {
        val copy = if (themeType == ThemeManager.THEME_TYPE_DARK) DarkThemePalette() else PictureThemePalette()
        copy.themeImageName = themeImageName
        copy.themeAccentColor = themeAccentColor
        copy.themeBlurAmount = themeBlurAmount
        copy.themeBackgroundOverlayColor = themeBackgroundOverlayColor
        if (reuseBitmaps) {
            copy.backgroundBitmap = backgroundBitmap
            copy.blurredBitmap = blurredBitmap
        }
        return copy
    }
}
