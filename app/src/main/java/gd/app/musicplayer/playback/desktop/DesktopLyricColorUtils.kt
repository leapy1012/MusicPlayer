package gd.app.musicplayer.playback.desktop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.text.TextUtilsCompat
import java.util.Locale

object DesktopLyricColorUtils {
    val currentGradientColors = intArrayOf(
        -11170817,
        -572604,
        -3053,
        -14745794,
        -16193834
    )

    val normalGradientColors = intArrayOf(
        -1,
        -11170817,
        -572604,
        -3053,
        -14745794,
        -16193834
    )

    fun interpolate(colors: IntArray, fraction: Float): Int {
        val lastIndex = colors.lastIndex
        if (lastIndex <= 0) return colors.firstOrNull() ?: Color.WHITE

        val step = 1f / lastIndex
        val index = (fraction / step).toInt()

        if (index <= 0 && fraction <= 0f) return colors[0]
        if (index >= lastIndex) return colors[lastIndex]

        val localFraction = (fraction - index * step) / step
        val start = colors[index]
        val end = colors[index + 1]

        return Color.rgb(
            interpolateChannel(Color.red(start), Color.red(end), localFraction),
            interpolateChannel(Color.green(start), Color.green(end), localFraction),
            interpolateChannel(Color.blue(start), Color.blue(end), localFraction)
        )
    }

    fun gradientDrawable(context: Context, colors: IntArray): GradientDrawable {
        val resolvedColors = if (isRtl(context)) colors.reversedArray() else colors
        return GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setColors(resolvedColors)
            cornerRadius = 16f
        }
    }

    private fun interpolateChannel(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).toInt()
    }

    private fun isRtl(context: Context): Boolean {
        return TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL ||
                context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }
}
