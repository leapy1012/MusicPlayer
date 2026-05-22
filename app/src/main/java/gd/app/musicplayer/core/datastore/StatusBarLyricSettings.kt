package gd.app.musicplayer.core.datastore

import android.content.Context
import android.os.Build
import android.view.Gravity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.di.ThemeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class StatusBarLyricSettingsState(
    val xRatio: Float = StatusBarLyricSettings.DEFAULT_X,
    val yRatio: Float = StatusBarLyricSettings.UNSET_Y,
    val widthRatio: Float = StatusBarLyricSettings.DEFAULT_WIDTH,
    val fontSizeRatio: Float = StatusBarLyricSettings.DEFAULT_FONT_SIZE,
    val alphaRatio: Float = StatusBarLyricSettings.DEFAULT_ALPHA,

    val enabled: Boolean = false,
    val contentType: Int = StatusBarLyricSettings.CONTENT_TYPE_LYRIC,
    val gravity: Int = Gravity.CENTER,
    val clickable: Boolean = false,
    val showPaused: Boolean = false,

    val textColor: Int = 0,
    val pendingEnableAfterPermission: Boolean = false
)

@Singleton
class StatusBarLyricSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ThemeSettingsDataStore private val dataStore: DataStore<Preferences>,
    private val themeSettingPreferenceStore: ThemeSettingPreferenceStore
) {

    val settings: Flow<StatusBarLyricSettingsState> =
        dataStore.data.map { preferences ->
            preferences.toState()
        }

    suspend fun getSettingsSnapshot(): StatusBarLyricSettingsState {
        return dataStore.data.first().toState()
    }

    suspend fun getXRatio(): Float {
        return getFloat(KEY_X, DEFAULT_X)
    }

    suspend fun setXRatio(value: Float) {
        set(KEY_X, value.coerceIn(0f, 1f))
    }

    suspend fun getYRatio(): Float {
        val stored = getFloat(KEY_Y, UNSET_Y)

        return if (stored >= 0f) {
            stored.coerceIn(0f, 1f)
        } else {
            defaultYRatio()
        }
    }

    suspend fun setYRatio(value: Float) {
        set(KEY_Y, value.coerceIn(0f, 1f))
    }

    suspend fun getWidthRatio(): Float {
        return getFloat(KEY_WIDTH, DEFAULT_WIDTH)
    }

    suspend fun setWidthRatio(value: Float) {
        set(KEY_WIDTH, value.coerceIn(0f, 1f))
    }

    suspend fun getFontSizeRatio(): Float {
        return getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    }

    suspend fun setFontSizeRatio(value: Float) {
        set(KEY_FONT_SIZE, value.coerceIn(0f, 1f))
    }

    suspend fun getAlphaRatio(): Float {
        return getFloat(KEY_ALPHA, DEFAULT_ALPHA)
    }

    suspend fun setAlphaRatio(value: Float) {
        set(KEY_ALPHA, value.coerceIn(0f, 1f))
    }

    suspend fun isEnabled(): Boolean {
        return getBoolean(KEY_ENABLE, false)
    }

    suspend fun setEnabled(value: Boolean) {
        set(KEY_ENABLE, value)
    }

    suspend fun getContentType(): Int {
        return getInt(KEY_CONTENT_TYPE, CONTENT_TYPE_LYRIC)
    }

    suspend fun setContentType(value: Int) {
        set(KEY_CONTENT_TYPE, value)
    }

    suspend fun getGravity(): Int {
        return getInt(KEY_GRAVITY, Gravity.CENTER)
    }

    suspend fun setGravity(value: Int) {
        set(KEY_GRAVITY, value)
    }

    suspend fun isClickable(): Boolean {
        return getBoolean(KEY_CLICKABLE, false)
    }

    suspend fun setClickable(value: Boolean) {
        set(KEY_CLICKABLE, value)
    }

    suspend fun isShowPaused(): Boolean {
        return getBoolean(KEY_SHOW_PAUSED, false)
    }

    suspend fun setShowPaused(value: Boolean) {
        set(KEY_SHOW_PAUSED, value)
    }

    suspend fun getTextColor(): Int {
        val storedColor = getInt(KEY_TEXT_COLOR, 0)

        return if (storedColor != 0) {
            storedColor
        } else {
            themeSettingPreferenceStore.getThemeColor()
        }
    }

    suspend fun setTextColor(value: Int) {
        set(KEY_TEXT_COLOR, value)
    }

    suspend fun isPendingEnableAfterPermission(): Boolean {
        return getBoolean(KEY_PENDING_ENABLE_AFTER_PERMISSION, false)
    }

    suspend fun setPendingEnableAfterPermission(value: Boolean) {
        set(KEY_PENDING_ENABLE_AFTER_PERMISSION, value)
    }

    suspend fun resetDisplayTuning() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_X)
            preferences.remove(KEY_Y)
            preferences.remove(KEY_WIDTH)
            preferences.remove(KEY_FONT_SIZE)
            preferences.remove(KEY_ALPHA)
            preferences.remove(KEY_TEXT_COLOR)
        }
    }

    private fun Preferences.toState(): StatusBarLyricSettingsState {
        return StatusBarLyricSettingsState(
            xRatio = this[KEY_X] ?: DEFAULT_X,
            yRatio = this[KEY_Y] ?: UNSET_Y,
            widthRatio = this[KEY_WIDTH] ?: DEFAULT_WIDTH,
            fontSizeRatio = this[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE,
            alphaRatio = this[KEY_ALPHA] ?: DEFAULT_ALPHA,

            enabled = this[KEY_ENABLE] ?: false,
            contentType = this[KEY_CONTENT_TYPE] ?: CONTENT_TYPE_LYRIC,
            gravity = this[KEY_GRAVITY] ?: Gravity.CENTER,
            clickable = this[KEY_CLICKABLE] ?: false,
            showPaused = this[KEY_SHOW_PAUSED] ?: false,

            textColor = this[KEY_TEXT_COLOR] ?: 0,
            pendingEnableAfterPermission = this[KEY_PENDING_ENABLE_AFTER_PERMISSION] ?: false
        )
    }

    private fun defaultYRatio(): Float {
        val resources = context.resources

        val statusBarHeight = resolveStatusBarHeight()
            .toFloat()
            .coerceAtLeast(0f)

        val screenHeight = resources.displayMetrics.heightPixels
            .toFloat()
            .coerceAtLeast(1f)

        val fontHeight =
            ((DEFAULT_FONT_SIZE.coerceIn(0f, 1f) * 24f) + 8f) *
                    resources.displayMetrics.density

        val denominator = (screenHeight - fontHeight).coerceAtLeast(1f)

        return (statusBarHeight / denominator).coerceIn(0f, 1f)
    }

    private fun resolveStatusBarHeight(): Int {
        val resources = context.resources

        val resourceId = resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        )

        if (resourceId != 0) {
            return resources.getDimensionPixelSize(resourceId)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (24f * resources.displayMetrics.density).roundToInt()
        } else {
            0
        }
    }

    private suspend fun getFloat(
        key: Preferences.Key<Float>,
        defaultValue: Float
    ): Float {
        return dataStore.data.first()[key] ?: defaultValue
    }

    private suspend fun getInt(
        key: Preferences.Key<Int>,
        defaultValue: Int
    ): Int {
        return dataStore.data.first()[key] ?: defaultValue
    }

    private suspend fun getBoolean(
        key: Preferences.Key<Boolean>,
        defaultValue: Boolean
    ): Boolean {
        return dataStore.data.first()[key] ?: defaultValue
    }

    private suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T
    ) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    companion object {
        const val CONTENT_TYPE_LYRIC = 0
        const val CONTENT_TYPE_TITLE = 1
        const val GRAVITY_LEFT = Gravity.START or Gravity.CENTER_VERTICAL

        const val DEFAULT_X = 0.5f
        const val UNSET_Y = -1f
        const val DEFAULT_WIDTH = 0.35f
        const val DEFAULT_FONT_SIZE = 0.5f
        const val DEFAULT_ALPHA = 1.0f

        private val KEY_X = floatPreferencesKey("sbar_lyric_x")
        private val KEY_Y = floatPreferencesKey("sbar_lyric_y")
        private val KEY_WIDTH = floatPreferencesKey("sbar_lyric_width")
        private val KEY_FONT_SIZE = floatPreferencesKey("sbar_lyric_font_size")
        private val KEY_ALPHA = floatPreferencesKey("sbar_lyric_alpha")

        private val KEY_ENABLE = booleanPreferencesKey("sbar_lyric_enable")
        private val KEY_CONTENT_TYPE = intPreferencesKey("sbar_lyric_content_type")
        private val KEY_GRAVITY = intPreferencesKey("sbar_lyric_gravity")
        private val KEY_CLICKABLE = booleanPreferencesKey("sbar_lyric_clickable")
        private val KEY_SHOW_PAUSED = booleanPreferencesKey("sbar_lyric_show_paused")
        private val KEY_TEXT_COLOR = intPreferencesKey("sbar_lyric_text_color")

        private val KEY_PENDING_ENABLE_AFTER_PERMISSION =
            booleanPreferencesKey("sbar_lyric_pending_enable_after_permission")
    }
}