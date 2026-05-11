package gd.app.musicplayer.core.designsystem.dialog

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

internal object DialogWidthResolver {

    fun resolve(
        context: Context,
        widthPx: Int,
        configuration: Configuration? = null
    ): Int {
        return when (widthPx) {
            BaseDialog.Config.WIDTH_90_PERCENT -> {
                resolveScaledWidth(
                    context = context,
                    configuration = configuration,
                    ratio = WIDTH_90_PERCENT_RATIO
                )
            }

            else -> widthPx
        }
    }

    fun isMatchParent(widthPx: Int): Boolean {
        return widthPx == ViewGroup.LayoutParams.MATCH_PARENT
    }

    fun isWrapContent(widthPx: Int): Boolean {
        return widthPx == ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private fun resolveScaledWidth(
        context: Context,
        configuration: Configuration?,
        ratio: Float
    ): Int {
        val displayMetrics = context.resources.displayMetrics
        val isLandscape = configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            calculateLandscapeScaledSize(
                displayMetrics = displayMetrics,
                ratio = ratio
            )
        } else {
            calculatePortraitScaledSize(
                displayMetrics = displayMetrics,
                ratio = ratio
            )
        }
    }

    private fun calculatePortraitScaledSize(
        displayMetrics: DisplayMetrics,
        ratio: Float
    ): Int {
        val shortestSide = min(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels
        )

        return (shortestSide * ratio).toInt()
    }

    private fun calculateLandscapeScaledSize(
        displayMetrics: DisplayMetrics,
        ratio: Float
    ): Int {
        val longestSide = max(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels
        )

        val screenClass = longestSide / displayMetrics.densityDpi.toFloat()

        val scaleFactor = when {
            screenClass <= 2f -> 0.78f
            screenClass <= 2.25f -> 0.75f
            screenClass <= 3.75f -> 0.69f
            screenClass <= 4.8f -> 0.67f
            else -> 0.56f
        }

        return (longestSide * ratio * scaleFactor).toInt()
    }

    private const val WIDTH_90_PERCENT_RATIO = 0.9f
}