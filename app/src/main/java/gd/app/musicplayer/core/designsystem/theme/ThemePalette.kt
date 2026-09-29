package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.graphics.drawable.Drawable

interface ThemePalette {
    fun getContentColor(): Int
    fun getTitleColor(): Int
    fun getMessageColor(): Int
    fun getDividerColor(): Int
    fun getCancelTextColor(): Int
    fun getCancelBaseColor(): Int
    fun getConfirmRippleColor(): Int
    fun getItemTextColor(): Int
    fun getRippleColor(): Int
    fun getHeaderOverlayColor(): Int
    fun getItemSecondaryTextColor(): Int
    fun getSelectionBorderColor(): Int
    fun getDialogTitleColor(): Int
    fun getDialogPressedOverlayColor(): Int
    fun getDialogDividerColor(): Int
    fun ensureResourcesLoaded(context: Context, themeBitmapLoader: ThemeBitmapLoader): Boolean
    fun getActivityBackgroundDrawable(context: Context): Drawable
    fun isActionAreaLight(): Boolean
    fun getItemPressedOverlayColor(): Int
    fun isDarkMode(): Boolean
    fun getHeaderSecondaryTextColor(): Int
    fun getHeaderBackgroundDrawable(context: Context): Drawable
    fun isNightTheme(): Boolean
    fun getItemPrimaryTextColor(): Int
    fun getThemeType(): Int
    fun getDefaultAccentColor(): Int
    fun getHeaderPressedOverlayColor(): Int
    fun getDialogSurfaceDrawable(context: Context): Drawable
    /** Bottom-sheet surface (picture = blur+overlay base; no full 12dp round). */
    fun getBottomDialogSurfaceDrawable(context: Context): Drawable = getDialogSurfaceDrawable(context)
    fun getHeaderTitleColor(): Int
    fun setAccentColor(accentColor: Int)
    fun getActionAreaTextColor(): Int
    fun isHeaderSurfaceLight(): Boolean
    fun getHeaderSubtitleColor(): Int
    fun getDialogSecondaryTextColor(): Int
    fun getDialogBackgroundMaskColor(): Int
    fun getRecyclerDividerColor(): Int
    fun isDialogSurfaceLight(): Boolean
    fun isContentSurfaceLight(): Boolean
    fun getActionAreaPressedOverlayColor(): Int
    fun getAccentColor(): Int
    fun getBlurredBackgroundDrawable(context: Context): Drawable
    fun getEditTextBackground(context: Context): Drawable
    fun getDialogBackground(context: Context): Drawable = getDialogSurfaceDrawable(context)
    fun getActivityBackground(context: Context): Drawable = getActivityBackgroundDrawable(context)
    fun getBlurBackground(context: Context): Drawable = getBlurredBackgroundDrawable(context)
}

val ThemePalette.contentColor: Int
    get() = getContentColor()

val ThemePalette.titleColor: Int
    get() = getTitleColor()

val ThemePalette.messageColor: Int
    get() = getMessageColor()

val ThemePalette.dividerColor: Int
    get() = getDividerColor()

val ThemePalette.cancelTextColor: Int
    get() = getCancelTextColor()

val ThemePalette.cancelBaseColor: Int
    get() = getCancelBaseColor()

val ThemePalette.confirmRippleColor: Int
    get() = getConfirmRippleColor()

val ThemePalette.itemTextColor: Int
    get() = getItemTextColor()

val ThemePalette.rippleColor: Int
    get() = getRippleColor()

val ThemePalette.headerOverlayColor: Int
    get() = getHeaderOverlayColor()

val ThemePalette.itemSecondaryTextColor: Int
    get() = getItemSecondaryTextColor()

val ThemePalette.selectionBorderColor: Int
    get() = getSelectionBorderColor()

val ThemePalette.dialogTitleColor: Int
    get() = getDialogTitleColor()

val ThemePalette.dialogPressedOverlayColor: Int
    get() = getDialogPressedOverlayColor()

val ThemePalette.dialogDividerColor: Int
    get() = getDialogDividerColor()

val ThemePalette.actionAreaLight: Boolean
    get() = isActionAreaLight()

val ThemePalette.itemPressedOverlayColor: Int
    get() = getItemPressedOverlayColor()

val ThemePalette.darkMode: Boolean
    get() = isDarkMode()

val ThemePalette.headerSecondaryTextColor: Int
    get() = getHeaderSecondaryTextColor()

val ThemePalette.nightTheme: Boolean
    get() = isNightTheme()

val ThemePalette.itemPrimaryTextColor: Int
    get() = getItemPrimaryTextColor()

val ThemePalette.themeType: Int
    get() = getThemeType()

val ThemePalette.defaultAccentColor: Int
    get() = getDefaultAccentColor()

val ThemePalette.headerPressedOverlayColor: Int
    get() = getHeaderPressedOverlayColor()

val ThemePalette.headerTitleColor: Int
    get() = getHeaderTitleColor()

val ThemePalette.actionAreaTextColor: Int
    get() = getActionAreaTextColor()

val ThemePalette.headerSurfaceLight: Boolean
    get() = isHeaderSurfaceLight()

val ThemePalette.headerSubtitleColor: Int
    get() = getHeaderSubtitleColor()

val ThemePalette.dialogSecondaryTextColor: Int
    get() = getDialogSecondaryTextColor()

val ThemePalette.dialogBackgroundMaskColor: Int
    get() = getDialogBackgroundMaskColor()

val ThemePalette.recyclerDividerColor: Int
    get() = getRecyclerDividerColor()

val ThemePalette.dialogSurfaceLight: Boolean
    get() = isDialogSurfaceLight()

val ThemePalette.contentSurfaceLight: Boolean
    get() = isContentSurfaceLight()

val ThemePalette.actionAreaPressedOverlayColor: Int
    get() = getActionAreaPressedOverlayColor()

val ThemePalette.accentColor: Int
    get() = getAccentColor()
