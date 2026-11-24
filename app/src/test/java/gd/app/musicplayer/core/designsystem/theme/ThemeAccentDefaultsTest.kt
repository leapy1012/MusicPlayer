package gd.app.musicplayer.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeAccentDefaultsTest {
    @Test fun lightAndDarkDefaultToBlueAndPicturesDefaultToYellow() {
        assertEquals(ThemeAccentDefaults.BLUE, ThemeAccentDefaults.forTheme(ThemeManager.THEME_TYPE_LIGHT))
        assertEquals(ThemeAccentDefaults.BLUE, ThemeAccentDefaults.forTheme(ThemeManager.THEME_TYPE_DARK))
        assertEquals(ThemeAccentDefaults.YELLOW, ThemeAccentDefaults.forTheme(ThemeManager.THEME_TYPE_PICTURE))
    }

    @Test fun manualAccentOverridesTheCurrentThemeDefault() {
        assertEquals(0xff992233.toInt(), ThemeAccentDefaults.resolve(2, true, 0xff992233.toInt()))
        assertEquals(ThemeAccentDefaults.YELLOW, ThemeAccentDefaults.resolve(2, false, ThemeAccentDefaults.BLUE))
    }
}
