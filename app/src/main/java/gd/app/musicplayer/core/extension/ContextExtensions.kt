package gd.app.musicplayer.core.extension

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import androidx.core.text.TextUtilsCompat
import dagger.hilt.android.EntryPointAccessors
import gd.app.musicplayer.app.di.AppDependenciesEntryPoint
import kotlin.math.roundToInt

fun Context.dpToPx(value: Float): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    ).roundToInt()

fun Context.spToPx(value: Float): Float =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

val Context.screenWidth: Int get() = resources.displayMetrics.widthPixels
val Context.screenHeight: Int get() = resources.displayMetrics.heightPixels
val Context.smallestScreenWidthDp: Int get() = resources.configuration.smallestScreenWidthDp
val Context.appDependencies: AppDependenciesEntryPoint
    get() = EntryPointAccessors.fromApplication(
        applicationContext,
        AppDependenciesEntryPoint::class.java
    )

val Context.density: Float get() = resources.displayMetrics.density
fun Context.isDarkTheme(): Boolean {
    val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mask == Configuration.UI_MODE_NIGHT_YES
}

fun Context.isTablet(): Boolean {
    return resources.configuration.smallestScreenWidthDp >= 600
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
    return (applicationInfo.flags and 0x400000) != 0
}

fun Context.isRtl(): Boolean {
    val configuration = resources.configuration
    val locale = ConfigurationCompat.getLocales(configuration)[0]
    val layoutDirection = locale?.let(TextUtilsCompat::getLayoutDirectionFromLocale)
        ?: configuration.layoutDirection

    return layoutDirection == View.LAYOUT_DIRECTION_RTL
}

fun Context.getMinScreenSize(includeSystemDecor: Boolean = false): Int {
    val displayMetrics = getDisplayMetrics(includeSystemDecor)
    return minOf(displayMetrics.heightPixels, displayMetrics.widthPixels)
}

fun Context.getMaxScreenSize(includeSystemDecor: Boolean = false): Int {
    val displayMetrics = getDisplayMetrics(includeSystemDecor)
    return maxOf(displayMetrics.heightPixels, displayMetrics.widthPixels)
}

fun Context.getDisplayMetrics(
    includeSystemDecor: Boolean
): DisplayMetrics {
    val densityMetrics = resources.displayMetrics

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowManager = getSystemService<WindowManager>()
            ?: return densityMetrics

        val metrics = if (includeSystemDecor) {
            windowManager.maximumWindowMetrics
        } else {
            windowManager.currentWindowMetrics
        }

        val bounds = metrics.bounds
        val displayMetrics = DisplayMetrics()

        displayMetrics.setTo(densityMetrics)
        displayMetrics.widthPixels = bounds.width()
        displayMetrics.heightPixels = bounds.height()

        if (!includeSystemDecor) {
            val insets = metrics.windowInsets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )

            displayMetrics.widthPixels -= insets.left + insets.right
            displayMetrics.heightPixels -= insets.top + insets.bottom
        }

        return displayMetrics
    }

    @Suppress("DEPRECATION")
    return DisplayMetrics().also { metrics ->
        val windowManager = getSystemService<WindowManager>()

        if (windowManager == null) {
            metrics.setTo(densityMetrics)
        } else {
            if (includeSystemDecor) {
                windowManager.defaultDisplay.getRealMetrics(metrics)
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
        }
    }
}
fun Context.highlightText(
    text: String,
    query: String?,
    highlightColor: Int,
    suffix: String
): CharSequence {
    if (query.isNullOrEmpty()) {
        return text + suffix
    }

    val fullText = text + suffix
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()

    var startIndex = lowerText.indexOf(lowerQuery)
    if (startIndex < 0) {
        return fullText
    }

    val spannable = SpannableStringBuilder(fullText)
    val queryLength = query.length

    while (startIndex >= 0) {
        val endIndex = startIndex + queryLength
        spannable.setSpan(
            ForegroundColorSpan(highlightColor),
            startIndex,
            endIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        startIndex = lowerText.indexOf(lowerQuery, endIndex)
    }

    return spannable
}
