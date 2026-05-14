package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.core.designsystem.drawable.DrawableUtil

abstract class BaseThemePalette : ThemePalette {
    override fun getContentColor(): Int = if (getTitleColor() == Color.WHITE) 0x10000000 else 0

    override fun getTitleColor(): Int = getHeaderTitleColor()

    override fun getMessageColor(): Int =
        ColorUtils.setAlphaComponent(getTitleColor(), if (getTitleColor() == Color.WHITE) 179 else 138)

    override fun getDividerColor(): Int = if (getTitleColor() == Color.WHITE) 0x0DFFFFFF else 0x0D000000

    override fun getCancelTextColor(): Int =
        if (getTitleColor() == Color.WHITE) 0xCCFFFFFF.toInt() else 0xDE000000.toInt()

    override fun getCancelBaseColor(): Int = if (getTitleColor() == Color.WHITE) 0x0DFFFFFF else 0x0D000000

    override fun getConfirmRippleColor(): Int = 0x26FFFFFF

    override fun getItemTextColor(): Int = getItemPrimaryTextColor()

    override fun getRippleColor(): Int = if (getTitleColor() == Color.WHITE) 0x26FFFFFF else 0x1A000000

    override fun getHeaderOverlayColor(): Int = 0

    override fun getPopupDividerColor(): Int = ThemeColorUtils.secondaryTextColor(isPopupSurfaceLight())

    override fun getItemSecondaryTextColor(): Int = ThemeColorUtils.secondaryTextColor(isContentSurfaceLight())

    override fun getSelectionBorderColor(): Int = ThemeColorUtils.maskColor(isContentSurfaceLight())

    override fun getDialogTitleColor(): Int = ThemeColorUtils.primaryTextColor(isDialogSurfaceLight())

    override fun getDialogPressedOverlayColor(): Int = ThemeColorUtils.pressedOverlay(isDialogSurfaceLight())

    override fun getDialogDividerColor(): Int = if (isDialogSurfaceLight()) 218103808 else 234881023

    override fun ensureResourcesLoaded(context: Context, themeBitmapLoader: ThemeBitmapLoader): Boolean = true

    override fun getActivityBackgroundDrawable(context: Context): Drawable = Color.WHITE.toDrawable()

    override fun isActionAreaLight(): Boolean = true

    override fun getPopupTitleColor(): Int = ThemeColorUtils.primaryTextColor(isPopupSurfaceLight())

    override fun getPopupPressedOverlayColor(): Int = 0

    override fun getPopupBackgroundMaskColor(): Int = ThemeColorUtils.maskColor(isPopupSurfaceLight())

    override fun getItemPressedOverlayColor(): Int = ThemeColorUtils.pressedOverlay(isContentSurfaceLight())

    override fun isDarkMode(): Boolean = false

    override fun getPopupBackgroundDrawable(context: Context): Drawable = Color.WHITE.toDrawable()

    override fun getHeaderSecondaryTextColor(): Int = ThemeColorUtils.maskColor(isHeaderSurfaceLight())

    override fun getHeaderBackgroundDrawable(context: Context): Drawable = ColorDrawable(getHeaderOverlayColor())

    override fun isNightTheme(): Boolean = false

    override fun getItemPrimaryTextColor(): Int = ThemeColorUtils.primaryTextColor(isContentSurfaceLight())

    override fun getThemeType(): Int = 0

    override fun getPopupPressedColor(): Int = ThemeColorUtils.pressedOverlay(isPopupSurfaceLight())

    override fun getDefaultAccentColor(): Int = 0

    override fun getPopupDividerShadowColor(): Int = if (isPopupSurfaceLight()) 218103808 else 234881023

    override fun getHeaderPressedOverlayColor(): Int = ThemeColorUtils.pressedOverlay(isHeaderSurfaceLight())

    override fun getDialogSurfaceDrawable(context: Context): Drawable = Color.WHITE.toDrawable()

    override fun getHeaderTitleColor(): Int = ThemeColorUtils.primaryTextColor(isHeaderSurfaceLight())

    override fun getActionAreaTextColor(): Int = ThemeColorUtils.primaryTextColor(isActionAreaLight())

    override fun isPopupSurfaceLight(): Boolean = true

    override fun isHeaderSurfaceLight(): Boolean = true

    override fun getHeaderSubtitleColor(): Int = ThemeColorUtils.secondaryTextColor(isHeaderSurfaceLight())

    override fun getDialogSecondaryTextColor(): Int = ThemeColorUtils.secondaryTextColor(isDialogSurfaceLight())

    override fun getDialogBackgroundMaskColor(): Int = ThemeColorUtils.maskColor(isDialogSurfaceLight())

    override fun getRecyclerDividerColor(): Int = if (isContentSurfaceLight()) 218103808 else 234881023

    override fun isDialogSurfaceLight(): Boolean = true

    override fun isContentSurfaceLight(): Boolean = true

    override fun getActionAreaPressedOverlayColor(): Int = ThemeColorUtils.pressedOverlay(isActionAreaLight())

    override fun getAccentColor(): Int = -16776961

    override fun getBlurredBackgroundDrawable(context: Context): Drawable = Color.WHITE.toDrawable()

    open fun setDefaultAccentColor(accentColor: Int) = Unit

    override fun setAccentColor(accentColor: Int) = Unit

    override fun getEditTextBackground(context: Context): Drawable {
        val fillColor = if (getTitleColor() == Color.WHITE) 352321535 else 335544320
        return DrawableUtil.gradientDrawable(context.resources.displayMetrics.density * 8f, fillColor)
    }
}
