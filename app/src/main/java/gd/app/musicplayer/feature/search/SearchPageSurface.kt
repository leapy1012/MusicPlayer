package gd.app.musicplayer.feature.search

import android.graphics.Color
import android.view.View
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette

/** Search pages share the activity wallpaper; solid themes retain their COUI list surface. */
internal object SearchPageSurface {
    fun apply(root: View, recyclerView: View, palette: ThemePalette) {
        val color = if (palette.getThemeType() == ThemeManager.THEME_TYPE_PICTURE) {
            Color.TRANSPARENT
        } else {
            val attributes = root.context.obtainStyledAttributes(
                intArrayOf(com.coui.appcompat.R.attr.couiColorBackgroundWithCard)
            )
            try {
                attributes.getColor(0, if (palette.isDarkMode()) Color.BLACK else 0xFFF0F1F2.toInt())
            } finally {
                attributes.recycle()
            }
        }
        // Clear both layers, including colors left over from an earlier solid theme.
        root.setBackgroundColor(color)
        recyclerView.setBackgroundColor(color)
    }
}
