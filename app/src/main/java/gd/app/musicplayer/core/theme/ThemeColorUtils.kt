package gd.app.musicplayer.core.theme

object ThemeColorUtils {
    @JvmStatic
    fun pressedOverlay(lightSurface: Boolean): Int = if (lightSurface) 436207616 else 654311423

    @JvmStatic
    fun iconTintPairSecondary(lightSurface: Boolean): Int = if (lightSurface) 1291845632 else 1627389951

    @JvmStatic
    fun iconTintPairPrimary(lightSurface: Boolean): Int = if (lightSurface) 1711276032 else -2130706433

    @JvmStatic
    fun primaryTextColor(lightSurface: Boolean): Int = if (lightSurface) -570425344 else -1

    @JvmStatic
    fun maskColor(lightSurface: Boolean): Int = if (lightSurface) 1711276032 else 1308622847

    @JvmStatic
    fun secondaryTextColor(lightSurface: Boolean): Int = if (lightSurface) -1979711488 else -1275068417
}
