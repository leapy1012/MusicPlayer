package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import com.coui.appcompat.theme.COUIThemeOverlay
import gd.app.musicplayer.core.datastore.ThemeSettings

/** Theme cards are authoritative; system dark mode temporarily overrides their appearance. */
object MusicAppearance {
    fun themeType(settings: ThemeSettings, systemDark: Boolean): Int =
        if (systemDark) ThemeManager.THEME_TYPE_DARK else when (settings.themeType) {
            ThemeManager.THEME_TYPE_DARK -> ThemeManager.THEME_TYPE_DARK
            ThemeManager.THEME_TYPE_PICTURE -> ThemeManager.THEME_TYPE_PICTURE
            else -> ThemeManager.THEME_TYPE_LIGHT
        }

    fun accent(settings: ThemeSettings, systemAccent: Int): Int =
        if (settings.followSystemAccent) systemAccent else settings.themeColor

    /** Resolve the shared COUI overlay in a clean theme, without the app accent overlay. */
    fun systemAccent(context: Context, dark: Boolean): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val configured = context.createConfigurationContext(configuration)
        val themed = ContextThemeWrapper(configured, if (dark)
            com.coui.appcompat.R.style.Theme_COUI_Main_Dark else com.coui.appcompat.R.style.Theme_COUI_Main_Light)
        COUIThemeOverlay.getInstance().applyThemeOverlays(themed)
        val colors = themed.obtainStyledAttributes(intArrayOf(com.coui.appcompat.R.attr.couiColorPrimary))
        return try { colors.getColor(0, ThemeAccentDefaults.BLUE) } finally { colors.recycle() }
    }
}
