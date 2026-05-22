package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.drawable.DialogBackgroundFactory
import gd.app.musicplayer.core.designsystem.drawable.OverlayCenterCropDrawable
import gd.app.musicplayer.core.designsystem.drawable.RoundedMaskDrawable
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore

open class PictureThemePalette : BaseThemePalette() {
    protected var themeAccentColor: Int = 0
    protected var themeImageName: String = ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE
    private var backgroundBitmap: Bitmap? = null
    private var blurredBitmap: Bitmap? = null
    private var themeBlurAmount: Int = 0
    private var themeBackgroundOverlayColor: Int = 855638016

    override fun getHeaderOverlayColor(): Int = if (isHeaderSurfaceLight()) 0 else 855638016

    override fun ensureResourcesLoaded(
        context: Context,
        themeBitmapLoader: ThemeBitmapLoader
    ): Boolean {
        if (backgroundBitmap == null) {
            backgroundBitmap = themeBitmapLoader.loadBitmap(
                context = context,
                imageName = themeImageName,
                blurRadius = themeBlurAmount
            )
        }

        if (blurredBitmap == null) {
            blurredBitmap = themeBitmapLoader.loadBlurBackgroundBitmap(
                context = context,
                imageName = themeImageName
            )
        }

        return backgroundBitmap != null
    }

    override fun getActivityBackgroundDrawable(context: Context): Drawable =
        OverlayCenterCropDrawable(
            context.resources,
            backgroundBitmap,
            themeBackgroundOverlayColor
        )

    override fun isActionAreaLight(): Boolean = true

    override fun getPopupPressedOverlayColor(): Int = 436207616

    override fun getPopupBackgroundDrawable(context: Context): Drawable =
        AppCompatResources.getDrawable(context, R.drawable.popup_bg) ?: Color.WHITE.toDrawable()

    override fun getHeaderBackgroundDrawable(context: Context): Drawable =
        if (isHeaderSurfaceLight()) {
            RoundedMaskDrawable(
                637534208.toDrawable(),
                context.resources.displayMetrics.density * 1.5f
            )
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

    // Spinner/context popups use the shared light popup background asset (`popup_bg`),
    // so popup foreground colors must be computed against a light surface even when
    // the picture theme's dialog/content surfaces are dark.
    override fun isPopupSurfaceLight(): Boolean = true

    override fun isHeaderSurfaceLight(): Boolean = isContentSurfaceLight()

    override fun getRecyclerDividerColor(): Int = if (isContentSurfaceLight()) 218103808 else 234881023

    override fun isDialogSurfaceLight(): Boolean = blurredBitmap == null || isContentSurfaceLight()

    override fun isContentSurfaceLight(): Boolean = false

    override fun getAccentColor(): Int = themeAccentColor

    override fun getBlurredBackgroundDrawable(context: Context): Drawable {
        return blurredBitmap?.toDrawable(context.resources) ?: 0xFFF9F9F9.toInt().toDrawable()
    }

    open fun getBottomDialogSurfaceDrawable(context: Context): Drawable =
        DialogBackgroundFactory.pictureDialogBackgroundBase(context, blurredBitmap)

    fun setImageName(imageName: String?) {
        themeImageName = imageName?.takeUnless(String::isBlank) ?: ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE
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
