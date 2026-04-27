package gd.app.musicplayer.util

import android.content.Context
import android.os.Build
import android.view.Gravity
import kotlin.math.roundToInt

class StatusBarLyricSettings private constructor(
    private val context: Context,
    private val preferences: PreferenceUtil
) {

    var xRatio: Float
        get() = preferences.getFloatPreference(KEY_X, DEFAULT_X)
        set(value) = preferences.putFloatPreference(KEY_X, value.coerceIn(0f, 1f))

    var widthRatio: Float
        get() = preferences.getFloatPreference(KEY_WIDTH, DEFAULT_WIDTH)
        set(value) = preferences.putFloatPreference(KEY_WIDTH, value.coerceIn(0f, 1f))

    var fontSizeRatio: Float
        get() = preferences.getFloatPreference(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = preferences.putFloatPreference(KEY_FONT_SIZE, value.coerceIn(0f, 1f))

    var alphaRatio: Float
        get() = preferences.getFloatPreference(KEY_ALPHA, DEFAULT_ALPHA)
        set(value) = preferences.putFloatPreference(KEY_ALPHA, value.coerceIn(0f, 1f))

    var enabled: Boolean
        get() = preferences.getBooleanPreference(KEY_ENABLE, false)
        set(value) = preferences.putBooleanPreference(KEY_ENABLE, value)

    var contentType: Int
        get() = preferences.getIntPreference(KEY_CONTENT_TYPE, CONTENT_TYPE_LYRIC)
        set(value) = preferences.putIntPreference(KEY_CONTENT_TYPE, value)

    var gravity: Int
        get() = preferences.getIntPreference(KEY_GRAVITY, Gravity.CENTER)
        set(value) = preferences.putIntPreference(KEY_GRAVITY, value)

    var clickable: Boolean
        get() = preferences.getBooleanPreference(KEY_CLICKABLE, false)
        set(value) = preferences.putBooleanPreference(KEY_CLICKABLE, value)

    var showPaused: Boolean
        get() = preferences.getBooleanPreference(KEY_SHOW_PAUSED, false)
        set(value) = preferences.putBooleanPreference(KEY_SHOW_PAUSED, value)

    var textColor: Int
        get() = preferences.getIntPreference(KEY_TEXT_COLOR, 0).takeIf { it != 0 }
            ?: preferences.getThemeColor()
        set(value) = preferences.putIntPreference(KEY_TEXT_COLOR, value)

    var pendingEnableAfterPermission: Boolean
        get() = preferences.getBooleanPreference(KEY_PENDING_ENABLE_AFTER_PERMISSION, false)
        set(value) = preferences.putBooleanPreference(KEY_PENDING_ENABLE_AFTER_PERMISSION, value)

    fun yRatio(): Float {
        val stored = preferences.getFloatPreference(KEY_Y, UNSET_Y)
        if (stored >= 0f) {
            return stored.coerceIn(0f, 1f)
        }
        return defaultYRatio()
    }

    fun setYRatio(value: Float) {
        preferences.putFloatPreference(KEY_Y, value.coerceIn(0f, 1f))
    }

    fun resetDisplayTuning() {
        preferences.removePreferences(
            KEY_X,
            KEY_Y,
            KEY_WIDTH,
            KEY_FONT_SIZE,
            KEY_ALPHA,
            KEY_TEXT_COLOR
        )
    }

    private fun defaultYRatio(): Float {
        val statusBarHeight = resolveStatusBarHeight().toFloat().coerceAtLeast(0f)
        val screenHeight = context.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val fontHeight = ((fontSizeRatio.coerceIn(0f, 1f) * 24f) + 8f) * context.resources.displayMetrics.density
        val denominator = (screenHeight - fontHeight).coerceAtLeast(1f)
        return (statusBarHeight / denominator).coerceIn(0f, 1f)
    }

    private fun resolveStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId != 0) {
            return context.resources.getDimensionPixelSize(resourceId)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (24f * context.resources.displayMetrics.density).roundToInt()
        } else {
            0
        }
    }

    companion object {
        const val CONTENT_TYPE_LYRIC = 0
        const val CONTENT_TYPE_TITLE = 1
        const val GRAVITY_LEFT = Gravity.START or Gravity.CENTER_VERTICAL

        private const val KEY_X = "sbar_lyric_x"
        private const val KEY_Y = "sbar_lyric_y"
        private const val KEY_WIDTH = "sbar_lyric_width"
        private const val KEY_FONT_SIZE = "sbar_lyric_font_size"
        private const val KEY_ALPHA = "sbar_lyric_alpha"
        private const val KEY_ENABLE = "sbar_lyric_enable"
        private const val KEY_CONTENT_TYPE = "sbar_lyric_content_type"
        private const val KEY_GRAVITY = "sbar_lyric_gravity"
        private const val KEY_CLICKABLE = "sbar_lyric_clickable"
        private const val KEY_SHOW_PAUSED = "sbar_lyric_show_paused"
        private const val KEY_TEXT_COLOR = "sbar_lyric_text_color"
        private const val KEY_PENDING_ENABLE_AFTER_PERMISSION =
            "sbar_lyric_pending_enable_after_permission"

        private const val DEFAULT_X = 0.5f
        private const val UNSET_Y = -1f
        private const val DEFAULT_WIDTH = 0.35f
        private const val DEFAULT_FONT_SIZE = 0.5f
        private const val DEFAULT_ALPHA = 1.0f

        fun from(context: Context): StatusBarLyricSettings {
            val appContext = context.applicationContext
            return StatusBarLyricSettings(appContext, PreferenceUtil.getInstance(appContext))
        }
    }
}
