package gd.app.musicplayer.feature.search

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.View
import gd.app.musicplayer.core.designsystem.theme.DarkThemePalette
import gd.app.musicplayer.core.designsystem.theme.LightThemePalette
import gd.app.musicplayer.core.designsystem.theme.PictureThemePalette
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class SearchPageSurfaceTest {
    @Test fun picturedThemeClearsBothOpaqueLayersEvenWithDarkCouiChrome() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), com.coui.appcompat.R.style.Theme_COUI_Main_Dark)
        val root = View(context).apply { setBackgroundColor(Color.BLACK) }
        val list = View(context).apply { setBackgroundColor(Color.BLACK) }
        SearchPageSurface.apply(root, list, PictureThemePalette())
        assertEquals(Color.TRANSPARENT, (root.background as ColorDrawable).color)
        assertEquals(Color.TRANSPARENT, (list.background as ColorDrawable).color)
    }

    @Test fun switchingThemesReplacesBothLayersAndRetainsSolidCouiSurfaces() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), com.coui.appcompat.R.style.Theme_COUI_Main_Light)
        val root = View(context)
        val list = View(context)
        for (palette in listOf(LightThemePalette(), PictureThemePalette(), DarkThemePalette(), PictureThemePalette(), LightThemePalette())) {
            context.theme.applyStyle(if (palette.isDialogSurfaceLight()) com.coui.appcompat.R.style.Theme_COUI_Main_Light else com.coui.appcompat.R.style.Theme_COUI_Main_Dark, true)
            SearchPageSurface.apply(root, list, palette)
            val actual = (root.background as ColorDrawable).color
            assertEquals(actual, (list.background as ColorDrawable).color)
            if (palette.getThemeType() == gd.app.musicplayer.core.designsystem.theme.ThemeManager.THEME_TYPE_PICTURE) {
                assertEquals(Color.TRANSPARENT, actual)
            } else {
                val values = context.obtainStyledAttributes(intArrayOf(com.coui.appcompat.R.attr.couiColorBackgroundWithCard))
                try { assertEquals(values.getColor(0, Color.MAGENTA), actual) } finally { values.recycle() }
                assertEquals(255, Color.alpha(actual))
            }
        }
    }
}
