package gd.app.musicplayer.feature.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import gd.app.musicplayer.core.theme.BaseThemePalette
import gd.app.musicplayer.core.theme.DefaultThemeBinder
import gd.app.musicplayer.core.theme.ThemePalette
import gd.app.musicplayer.core.theme.ThemeProvider
import gd.app.musicplayer.core.theme.ThemeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val palette = TestPalette(
        accentColor = 0xFF00AAFF.toInt(),
        titleColor = Color.RED,
        itemTextColor = Color.WHITE,
        lightSurface = false
    )
    private val themeEngine = ThemeEngine(
        ThemeRegistry().apply {
            installProvider(TestThemeProvider(palette), true)
        }
    )

    @Test
    fun `title tag applies text and hint colors`() {
        val view = TextView(context).apply {
            tag = ThemeTags.TITLE_COLOR
            hint = "hint"
        }

        themeEngine.apply(view)

        assertEquals(palette.headerTitleColor, view.currentTextColor)
        assertEquals(ColorUtils.setAlphaComponent(palette.headerTitleColor, 128), view.currentHintTextColor)
    }

    @Test
    fun `content background uses ripple for clickable view`() {
        val view = TextView(context).apply {
            tag = ThemeTags.CONTENT_BACKGROUND
            isClickable = true
        }

        themeEngine.apply(view)

        assertTrue(view.background is RippleDrawable)
    }

    @Test
    fun `ignore tag skips child theming`() {
        val parent = LinearLayout(context).apply {
            tag = ThemeTags.IGNORE
        }
        val child = TextView(context).apply {
            tag = ThemeTags.TITLE_COLOR
            text = "child"
        }
        parent.addView(child)

        themeEngine.apply(parent)

        assertNotEquals(palette.headerTitleColor, child.currentTextColor)
    }

    private class TestThemeProvider(
        private val palette: ThemePalette
    ) : ThemeProvider {
        private val binder = DefaultThemeBinder()

        override fun getThemeBinder() = binder

        override fun applyTheme(palette: ThemePalette) = Unit

        override fun refreshTheme(context: Context) = Unit

        override fun getCurrentTheme(): ThemePalette = palette
    }

    private class TestPalette(
        private var accentColor: Int,
        private val titleColor: Int,
        private val itemTextColor: Int,
        private val lightSurface: Boolean
    ) : BaseThemePalette() {
        override fun getActivityBackgroundDrawable(context: Context) = ColorDrawable(Color.BLACK)

        override fun getHeaderTitleColor(): Int = titleColor

        override fun getItemPrimaryTextColor(): Int = itemTextColor

        override fun isHeaderSurfaceLight(): Boolean = lightSurface

        override fun isContentSurfaceLight(): Boolean = lightSurface

        override fun getAccentColor(): Int = accentColor

        override fun setAccentColor(accentColor: Int) {
            this.accentColor = accentColor
        }
    }
}
