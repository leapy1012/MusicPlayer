package gd.app.musicplayer.playback.notification

import android.content.Context
import androidx.core.graphics.ColorUtils

open class AlbumColorRemoteViewsNotificationBuilder(
    context: Context,
    shouldUseDynamicColors: Boolean,
) : LegacyRemoteViewsNotificationBuilder(
    context = context,
    shouldUseDynamicColors = shouldUseDynamicColors,
) {

    override fun resolveColorStyle(
        albumArt: NotificationAlbumArtwork,
    ): NotificationColorStyle {
        val colorStyle = super.resolveColorStyle(albumArt)
        val palette = albumArt.palette ?: return colorStyle

        var dominantSwatch = palette.dominantSwatch
        val vibrantSwatch = palette.vibrantSwatch

        if (dominantSwatch == null ||
            vibrantSwatch != null && dominantSwatch.population <= vibrantSwatch.population
        ) {
            dominantSwatch = vibrantSwatch
        }

        val selectedSwatch = dominantSwatch ?: return colorStyle
        val albumColor = selectedSwatch.rgb

        when {
            isLightColor(albumColor) -> {
                colorStyle.backgroundColor = ColorUtils.compositeColors(
                    LIGHT_OVERLAY_COLOR,
                    albumColor,
                )
                colorStyle.useLightTheme = true
            }

            isDarkColor(albumColor) -> {
                colorStyle.backgroundColor = ColorUtils.compositeColors(
                    DARK_OVERLAY_COLOR,
                    albumColor,
                )
                colorStyle.useLightTheme = false
            }

            else -> {
                val hsl = selectedSwatch.hsl.copyOf()
                hsl[2] = hsl[2] * DARKEN_FACTOR

                colorStyle.backgroundColor = ColorUtils.HSLToColor(hsl)
                colorStyle.useLightTheme = false
            }
        }

        return colorStyle
    }

    companion object {
        private const val LIGHT_OVERLAY_COLOR = 220603942
        private const val DARK_OVERLAY_COLOR = 234881023
        private const val DARKEN_FACTOR = 0.6f

        private fun isLightColor(color: Int): Boolean = ColorUtils.calculateLuminance(color) > 0.75
        private fun isDarkColor(color: Int): Boolean = ColorUtils.calculateLuminance(color) < 0.25
    }
}
