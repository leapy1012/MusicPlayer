package gd.app.musicplayer.core.util


import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import gd.app.musicplayer.core.extension.dpToPx
import gd.app.musicplayer.util.AppLogger
import kotlin.math.max
import kotlin.math.min

/** o8.o0 */

abstract class ScreenUtils {

    companion object {
        private var portraitStatusBarHeightCache: Int = 0
        private var landscapeStatusBarHeightCache: Int = 0

        // original: a
        private fun resolveStatusBarHeight(context: Context): Int {
            var statusBarHeight = 0

            try {
                val resourceId = context.resources.getIdentifier(
                    "status_bar_height",
                    "dimen",
                    "android"
                )
                if (resourceId > 0) {
                    statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
                }
            } catch (e: Exception) {
                AppLogger.error("ScreenUtils", e)
            }

            if (statusBarHeight <= 0 && context is Activity) {
                try {
                    statusBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
                    } else {
                        val visibleFrame = Rect()
                        context.window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
                        visibleFrame.top
                    }
                } catch (e: Exception) {
                    AppLogger.error("ScreenUtils", e)
                }
            }

            return if (statusBarHeight <= 0) context.dpToPx(25f) else statusBarHeight
        }

        // original: b
        @JvmStatic
        fun getDisplayMetrics(context: Context): DisplayMetrics {
            return getDisplayMetrics(context, includeSystemDecor = false)
        }

        // original: c
        @JvmStatic
        fun getDisplayMetrics(
            context: Context,
            includeSystemDecor: Boolean
        ): DisplayMetrics {
            val resources: Resources? = context.resources
            if (!includeSystemDecor && resources != null) {
                return resources.displayMetrics
            }

            val windowManager = getWindowManager(context)
            val displayMetrics = DisplayMetrics()

            if (includeSystemDecor) {
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            } else {
                windowManager.defaultDisplay.getMetrics(displayMetrics)
            }

            return displayMetrics
        }

        // original: d
        private fun calculateLandscapeScaledSize(
            displayMetrics: DisplayMetrics,
            ratio: Float
        ): Int {
            val longestSide = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val screenClass = longestSide / displayMetrics.densityDpi

            val scaleFactor = when {
                screenClass <= 2 -> 0.78f
                screenClass <= 2.25f -> 0.75f
                screenClass <= 3.75f -> 0.69f
                screenClass <= 4.8f -> 0.67f
                else -> 0.56f
            }

            return (longestSide * ratio * scaleFactor).toInt()
        }

        // original: e
        private fun calculatePortraitScaledSize(
            displayMetrics: DisplayMetrics,
            ratio: Float
        ): Int {
            val shortestSide = min(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val screenClass = shortestSide / displayMetrics.densityDpi

            val scaleFactor = when {
                screenClass <= 2 -> 1.0f
                screenClass <= 2.25f -> 0.97f
                screenClass <= 3.75f -> 0.89f
                screenClass <= 4.8f -> 0.78f
                else -> 0.67f
            }

            return (shortestSide * ratio * scaleFactor).toInt()
        }

        // original: f
        @JvmStatic
        fun getScaledSizeByCurrentOrientation(context: Context, ratio: Float): Int {
            return getScaledSizeByConfiguration(
                context,
                context.resources.configuration,
                ratio
            )
        }

        // original: g
        @JvmStatic
        fun getScaledSizeByConfiguration(
            context: Context,
            configuration: Configuration,
            ratio: Float
        ): Int {
            val displayMetrics = getDisplayMetrics(context)
            return if (isLandscape(configuration)) {
                calculateLandscapeScaledSize(displayMetrics, ratio)
            } else {
                calculatePortraitScaledSize(displayMetrics, ratio)
            }
        }

        // original: h
        @JvmStatic
        fun getScreenHeight(context: Context): Int {
            return getScreenHeight(context, includeSystemDecor = false)
        }

        // original: i
        @JvmStatic
        fun getScreenHeight(
            context: Context,
            includeSystemDecor: Boolean
        ): Int {
            return getDisplayMetrics(context, includeSystemDecor).heightPixels
        }

        // original: j
        @JvmStatic
        fun getLongSide(context: Context): Int {
            return getLongSide(context, includeSystemDecor = false)
        }

        // original: k
        @JvmStatic
        fun getLongSide(
            context: Context,
            includeSystemDecor: Boolean
        ): Int {
            val metrics = getDisplayMetrics(context, includeSystemDecor)
            return max(metrics.heightPixels, metrics.widthPixels)
        }

        // original: l
        @JvmStatic
        fun getShortSide(context: Context): Int {
            return getShortSide(context, includeSystemDecor = false)
        }

        // original: m
        @JvmStatic
        fun getShortSide(
            context: Context,
            includeSystemDecor: Boolean
        ): Int {
            val metrics = getDisplayMetrics(context, includeSystemDecor)
            return min(metrics.heightPixels, metrics.widthPixels)
        }

        // original: n
        @JvmStatic
        fun getSmallestScreenWidthDp(context: Context): Int {
            return context.resources.configuration.smallestScreenWidthDp
        }

        // original: o
        @JvmStatic
        fun getScreenSides(
            context: Context,
            includeSystemDecor: Boolean,
            longSideFirst: Boolean
        ): IntArray {
            val metrics = getDisplayMetrics(context, includeSystemDecor)
            val shortSide = min(metrics.widthPixels, metrics.heightPixels)
            val longSide = max(metrics.widthPixels, metrics.heightPixels)

            return if (longSideFirst) {
                intArrayOf(longSide, shortSide)
            } else {
                intArrayOf(shortSide, longSide)
            }
        }

        // original: p
        @JvmStatic
        fun getScreenWidth(context: Context): Int {
            return getScreenWidth(context, includeSystemDecor = false)
        }

        // original: q
        @JvmStatic
        fun getScreenWidth(
            context: Context,
            includeSystemDecor: Boolean
        ): Int {
            return getDisplayMetrics(context, includeSystemDecor).widthPixels
        }

        // original: r
        @JvmStatic
        fun getStatusBarHeight(context: Context): Int {
            val isLandscapeMode = isLandscape(context)
            val cachedHeight = if (isLandscapeMode) {
                landscapeStatusBarHeightCache
            } else {
                portraitStatusBarHeightCache
            }

            if (cachedHeight != 0) {
                return cachedHeight
            }

//            if (n0.c()) {
//                return 0
//            }

            val resolvedHeight = resolveStatusBarHeight(context)
            if (isLandscapeMode) {
                landscapeStatusBarHeightCache = resolvedHeight
            } else {
                portraitStatusBarHeightCache = resolvedHeight
            }

            return resolvedHeight
        }

        // original: s
        @JvmStatic
        fun getWindowManager(context: Context): WindowManager {
            return if (context is Activity) {
                context.windowManager
            } else {
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
        }

        // original: t
        @JvmStatic
        fun isLandscape(context: Context): Boolean {
            return isLandscape(context.resources.configuration)
        }

        // original: u
        @JvmStatic
        fun isLandscape(configuration: Configuration): Boolean {
            return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }

        // original: v
        @JvmStatic
        fun isPortrait(context: Context): Boolean {
            return context.resources.configuration.orientation ==
                    Configuration.ORIENTATION_PORTRAIT
        }

        // original: w
        @JvmStatic
        fun isTabletOrLargeScreen(context: Context): Boolean {
            return (context.resources.configuration.screenLayout and
                    Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        }

        // original: x
        @JvmStatic
        fun updateCachedStatusBarHeight(context: Context, statusBarHeight: Int) {
            if (isLandscape(context)) {
                if (landscapeStatusBarHeightCache != statusBarHeight) {
                    landscapeStatusBarHeightCache = statusBarHeight
                }
            } else {
                if (portraitStatusBarHeightCache != statusBarHeight) {
                    portraitStatusBarHeightCache = statusBarHeight
                }
            }
        }
    }
}
