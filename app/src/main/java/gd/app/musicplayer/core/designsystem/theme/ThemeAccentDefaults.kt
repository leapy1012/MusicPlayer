package gd.app.musicplayer.core.designsystem.theme

/** App defaults belong to the selected theme, independently of system Colors. */
object ThemeAccentDefaults {
    const val BLUE = 0xFF0066FF.toInt()
    const val YELLOW = 0xFFFFB200.toInt()

    fun forTheme(themeType: Int): Int =
        if (themeType == ThemeManager.THEME_TYPE_PICTURE) YELLOW else BLUE

    fun resolve(themeType: Int, custom: Boolean, selectedColor: Int): Int =
        if (custom) selectedColor else forTheme(themeType)
}
