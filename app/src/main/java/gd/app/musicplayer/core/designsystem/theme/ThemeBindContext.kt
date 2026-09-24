package gd.app.musicplayer.core.designsystem.theme

import android.graphics.Color
import androidx.core.graphics.ColorUtils

internal data class ThemeBindContext(
    val accentColor: Int,
    val titleColor: Int,
    val itemTextColor: Int,
    val secondaryTextColor: Int,
    val rippleColor: Int,
    val contentOverlay: Int,
    val strongerOverlay: Int,
    val usesDarkForeground: Boolean,
) {
    companion object {
        fun from(palette: ThemePalette): ThemeBindContext {
            val usesDarkForeground = palette.headerTitleColor != Color.WHITE
            val itemTextColor = palette.itemPrimaryTextColor

            return ThemeBindContext(
                accentColor = palette.accentColor,
                titleColor = palette.headerTitleColor,
                itemTextColor = itemTextColor,
                secondaryTextColor = ColorUtils.setAlphaComponent(
                    itemTextColor,
                    ThemeBindDefaults.TEXT_SECONDARY_ALPHA,
                ),
                rippleColor = if (usesDarkForeground) 0x1A000000 else 0x26FFFFFF,
                contentOverlay = if (usesDarkForeground) 0 else 0x10000000,
                strongerOverlay = if (usesDarkForeground) 0x0D000000 else 0x1A000000,
                usesDarkForeground = usesDarkForeground,
            )
        }
    }
}

internal object ThemeBindDefaults {
    const val FULL_ROUND_RADIUS = 1000f
    const val HINT_ALPHA = 128
    const val TEXT_SECONDARY_ALPHA = 180
    const val DISABLED_ALPHA = 77
    const val DIVIDER_ALPHA = 36
}
