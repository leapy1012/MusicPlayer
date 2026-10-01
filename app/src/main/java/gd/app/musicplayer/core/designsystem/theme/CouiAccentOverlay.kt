package gd.app.musicplayer.core.designsystem.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.loader.ResourcesLoader
import android.os.Build
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.ThemeUtils
import gd.app.musicplayer.R

/**
 * Overrides COUI primary/label/container tokens with the **exact** app accent.
 *
 * Flow (API 30+):
 * 1. Build a [ResourcesLoader] that replaces [R.color.app_accent] (etc.) with the
 *    runtime ARGB — via Material's ColorResourcesLoaderCreator (same path as
 *    content-based DynamicColors).
 * 2. [ThemeUtils.applyThemeOverlay] with [R.style.ThemeOverlay_App_Accent] so
 *    `couiColorPrimary*` resolve to those placeholders.
 *
 * API 28–29: loader unavailable; AppTheme keeps stock COUI Blue.
 */
object CouiAccentOverlay {

    private const val TAG = "CouiAccentOverlay"

    private var installedLoader: ResourcesLoader? = null

    /**
     * Must run after Hilt inject (post-[Activity] super.onCreate) and before
     * [Activity.setContentView].
     *
     * @return true when exact accent was installed into the theme.
     */
    fun apply(activity: Activity, @ColorInt accent: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        val colorMap = colorReplacementMap(accent)
        if (!installColorLoader(activity, colorMap)) {
            return false
        }
        ThemeUtils.applyThemeOverlay(activity, R.style.ThemeOverlay_App_Accent)
        return true
    }

    /**
     * Picture / dark palettes keep wallpaper via theme tags but still inflate under
     * [R.style.AppTheme] → COUI Light, so toolbars/lists stay black. Overlay COUI Dark
     * for light-on-dark neutrals, then [R.style.ThemeOverlay_App_EdgeToEdgeNoActionBar]
     * so the full Dark theme cannot restore a white ActionBar / opaque status bar.
     */
    fun applyDarkChromeIfNeeded(activity: Activity, palette: ThemePalette): Boolean {
        if (palette.isContentSurfaceLight()) return false
        forceDarkChrome(activity)
        return true
    }

    /**
     * Always install COUI Dark neutrals (e.g. Equalizer’s fixed dark plate, regardless of
     * the app’s Light / Picture / Dark setting).
     */
    fun forceDarkChrome(activity: Activity) {
        ThemeUtils.applyThemeOverlay(
            activity,
            com.coui.appcompat.R.style.Theme_COUI_Main_Dark,
        )
        ThemeUtils.applyThemeOverlay(
            activity,
            R.style.ThemeOverlay_App_EdgeToEdgeNoActionBar,
        )
    }

    fun colorReplacementMap(@ColorInt accent: Int): Map<Int, Int> {
        val opaque = accent or 0xFF000000.toInt()
        return mapOf(
            R.color.app_accent to opaque,
            R.color.app_accent_container to ColorUtils.setAlphaComponent(opaque, 0x1A),
            R.color.app_accent_container_halftone to ColorUtils.setAlphaComponent(opaque, 0x0D),
            R.color.app_accent_disable to ColorUtils.setAlphaComponent(opaque, 0x66),
            R.color.app_accent_focus to opaque,
            R.color.app_accent_focus_outline to ColorUtils.setAlphaComponent(opaque, 0x4D),
            // Match COUI overlay washes (~0x4C) so selection stays readable on black text.
            R.color.app_accent_text_highlight to ColorUtils.setAlphaComponent(opaque, 0x4C),
        )
    }

    @SuppressLint("PrivateApi")
    private fun installColorLoader(context: Context, colorMap: Map<Int, Int>): Boolean {
        return try {
            val creatorClass =
                Class.forName("com.google.android.material.color.ColorResourcesLoaderCreator")
            val create = creatorClass.getDeclaredMethod(
                "create",
                Context::class.java,
                Map::class.java,
            ).also { it.isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val loader = create.invoke(null, context, colorMap) as? ResourcesLoader
            if (loader == null) {
                Log.w(TAG, "ColorResourcesLoaderCreator returned null")
                return false
            }
            val resources = context.resources
            installedLoader?.let { previous ->
                runCatching { resources.removeLoaders(previous) }
            }
            resources.addLoaders(loader)
            installedLoader = loader
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to install accent ResourcesLoader", t)
            false
        }
    }
}
