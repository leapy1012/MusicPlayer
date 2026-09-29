package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable

/**
 * Solid light / White theme — COUI light surfaces, no wallpaper.
 * Mirrors [DarkThemePalette] as a first-class type (not picture).
 */
class LightThemePalette : PictureThemePalette() {

    override fun getHeaderOverlayColor(): Int = 0

    override fun ensureResourcesLoaded(
        context: Context,
        themeBitmapLoader: ThemeBitmapLoader
    ): Boolean = true

    override fun getActivityBackgroundDrawable(context: Context): Drawable =
        resolveCouiBackground(context)

    override fun isActionAreaLight(): Boolean = true

    override fun isDarkMode(): Boolean = false

    override fun getHeaderBackgroundDrawable(context: Context): Drawable =
        resolveCouiBackground(context)

    override fun isNightTheme(): Boolean = false

    override fun getThemeType(): Int = ThemeManager.THEME_TYPE_LIGHT

    override fun getDialogSurfaceDrawable(context: Context): Drawable =
        resolveCouiCard(context)

    override fun getBottomDialogSurfaceDrawable(context: Context): Drawable =
        resolveCouiCard(context)

    override fun isHeaderSurfaceLight(): Boolean = true

    override fun isDialogSurfaceLight(): Boolean = true

    override fun isContentSurfaceLight(): Boolean = true

    override fun getBlurredBackgroundDrawable(context: Context): Drawable =
        resolveCouiBackground(context)

    private fun resolveCouiBackground(context: Context): Drawable {
        val color = resolveAttrColor(
            context,
            com.coui.appcompat.R.attr.couiColorBackgroundWithCard,
            COUI_BG_FALLBACK
        )
        return color.toDrawable()
    }

    private fun resolveCouiCard(context: Context): Drawable {
        val color = resolveAttrColor(
            context,
            com.coui.appcompat.R.attr.couiColorCardBackground,
            COUI_CARD_FALLBACK
        )
        return color.toDrawable()
    }

    private fun resolveAttrColor(context: Context, attr: Int, fallback: Int): Int {
        val typed = context.obtainStyledAttributes(intArrayOf(attr))
        val color = typed.getColor(0, fallback)
        typed.recycle()
        return color
    }

    private companion object {
        /** Matches coui_color_background_with_card_light. */
        private const val COUI_BG_FALLBACK = 0xFFF0F1F2.toInt()
        private const val COUI_CARD_FALLBACK = 0xFFFFFFFF.toInt()
    }
}
