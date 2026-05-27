package gd.app.musicplayer.core.common.extension

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import androidx.core.text.TextUtilsCompat

private const val FLAG_SUPPORTS_RTL = 0x400000
private const val TABLET_MIN_WIDTH_DP = 600

fun Context.dpToPx(value: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).toInt()
}

fun Context.spToPx(value: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )
}

val Context.screenWidth: Int
    get() = resources.displayMetrics.widthPixels

val Context.screenHeight: Int
    get() = resources.displayMetrics.heightPixels

val Context.smallestScreenWidthDp: Int
    get() = resources.configuration.smallestScreenWidthDp

val Context.density: Float
    get() = resources.displayMetrics.density

fun Context.isDarkTheme(): Boolean {
    return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}

fun Context.isTablet(): Boolean {
    return smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
}

fun Context.isLandscape(): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

fun Context.startActivityCompat(intent: Intent) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    startActivity(intent)
}

fun Context.isRtlLayoutSupported(): Boolean {
    return applicationInfo.flags and FLAG_SUPPORTS_RTL != 0
}

fun Context.isRtl(): Boolean {
    val configuration = resources.configuration
    val locale = ConfigurationCompat.getLocales(configuration)[0]

    val layoutDirection = locale?.let(TextUtilsCompat::getLayoutDirectionFromLocale)
        ?: configuration.layoutDirection

    return layoutDirection == View.LAYOUT_DIRECTION_RTL
}

fun Context.getMinScreenSize(
    includeSystemDecor: Boolean = false,
): Int {
    val metrics = getDisplayMetrics(includeSystemDecor)
    return minOf(metrics.widthPixels, metrics.heightPixels)
}

fun Context.getMaxScreenSize(
    includeSystemDecor: Boolean = false,
): Int {
    val metrics = getDisplayMetrics(includeSystemDecor)
    return maxOf(metrics.widthPixels, metrics.heightPixels)
}

fun Context.getDisplayMetrics(
    includeSystemDecor: Boolean = false,
): DisplayMetrics {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getDisplayMetricsApi30(includeSystemDecor)
    } else {
        getDisplayMetricsLegacy(includeSystemDecor)
    }
}

fun Context.canLoadImage(): Boolean {
    val activity = this as? Activity ?: return true
    return !activity.isFinishing && !activity.isDestroyed
}

@RequiresApi(Build.VERSION_CODES.R)
private fun Context.getDisplayMetricsApi30(
    includeSystemDecor: Boolean,
): DisplayMetrics {
    val baseMetrics = resources.displayMetrics
    val windowManager = getSystemService<WindowManager>() ?: return baseMetrics

    val windowMetrics = if (includeSystemDecor) {
        windowManager.maximumWindowMetrics
    } else {
        windowManager.currentWindowMetrics
    }

    return DisplayMetrics().apply {
        setTo(baseMetrics)
        widthPixels = windowMetrics.bounds.width()
        heightPixels = windowMetrics.bounds.height()

        if (!includeSystemDecor) {
            val insets = windowMetrics.windowInsets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )

            widthPixels -= insets.left + insets.right
            heightPixels -= insets.top + insets.bottom
        }
    }
}

@Suppress("DEPRECATION")
private fun Context.getDisplayMetricsLegacy(
    includeSystemDecor: Boolean,
): DisplayMetrics {
    val baseMetrics = resources.displayMetrics
    val windowManager = getSystemService<WindowManager>()

    return DisplayMetrics().apply {
        if (windowManager == null) {
            setTo(baseMetrics)
            return@apply
        }

        if (includeSystemDecor) {
            windowManager.defaultDisplay.getRealMetrics(this)
        } else {
            windowManager.defaultDisplay.getMetrics(this)
        }
    }
}