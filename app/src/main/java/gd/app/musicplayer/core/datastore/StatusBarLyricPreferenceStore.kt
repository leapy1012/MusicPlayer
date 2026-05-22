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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class StatusBarLyricPreferenceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val themeSettingPreferenceStore: ThemeSettingPreferenceStore
) {

    val preference: Flow<StatusBarLyricPreference> =
        dataStore.data
            .map { preferences ->
                preferences.toStatusBarLyricPreference()
            }
            .distinctUntilChanged()

    suspend fun getSnapshot(): StatusBarLyricPreference {
        return dataStore.data.first().toStatusBarLyricPreference()
    }

    suspend fun setEnabled(enabled: Boolean) {
        set(KEY_ENABLED, enabled)
    }

    suspend fun setShowPaused(showPaused: Boolean) {
        set(KEY_SHOW_PAUSED, showPaused)
    }

    suspend fun setClickable(clickable: Boolean) {
        set(KEY_CLICKABLE, clickable)
    }

    suspend fun setContentType(contentType: Int) {
        set(
            key = KEY_CONTENT_TYPE,
            value = when (contentType) {
                CONTENT_TYPE_TITLE -> CONTENT_TYPE_TITLE
                else -> CONTENT_TYPE_LYRIC
            }
        )
    }

    suspend fun setGravity(gravity: Int) {
        set(
            key = KEY_GRAVITY,
            value = when (gravity) {
                GRAVITY_LEFT -> GRAVITY_LEFT
                else -> Gravity.CENTER
            }
        )
    }

    suspend fun setTextColor(color: Int) {
        set(KEY_TEXT_COLOR, color)
    }

    suspend fun setPendingEnableAfterPermission(pending: Boolean) {
        set(KEY_PENDING_ENABLE_AFTER_PERMISSION, pending)
    }

    suspend fun setXRatio(value: Float) {
        set(KEY_X_RATIO, value.asRatio())
    }

    suspend fun setYRatio(value: Float) {
        set(KEY_Y_RATIO, value.asRatio())
    }

    suspend fun setWidthRatio(value: Float) {
        set(KEY_WIDTH_RATIO, value.asRatio())
    }

    suspend fun setFontSizeRatio(value: Float) {
        set(KEY_FONT_SIZE_RATIO, value.asRatio())
    }

    suspend fun setAlphaRatio(value: Float) {
        set(KEY_ALPHA_RATIO, value.asRatio())
    }

    suspend fun updatePreference(
        enabled: Boolean? = null,
        showPaused: Boolean? = null,
        clickable: Boolean? = null,
        contentType: Int? = null,
        gravity: Int? = null,
        textColor: Int? = null,
        pendingEnableAfterPermission: Boolean? = null,
        xRatio: Float? = null,
        yRatio: Float? = null,
        widthRatio: Float? = null,
        fontSizeRatio: Float? = null,
        alphaRatio: Float? = null
    ) {
        dataStore.edit { preferences ->
            enabled?.let {
                preferences[KEY_ENABLED] = it
            }

            showPaused?.let {
                preferences[KEY_SHOW_PAUSED] = it
            }

            clickable?.let {
                preferences[KEY_CLICKABLE] = it
            }

            contentType?.let {
                preferences[KEY_CONTENT_TYPE] =
                    if (it == CONTENT_TYPE_TITLE) CONTENT_TYPE_TITLE else CONTENT_TYPE_LYRIC
            }

            gravity?.let {
                preferences[KEY_GRAVITY] =
                    if (it == GRAVITY_LEFT) GRAVITY_LEFT else Gravity.CENTER
            }

            textColor?.let {
                preferences[KEY_TEXT_COLOR] = it
            }

            pendingEnableAfterPermission?.let {
                preferences[KEY_PENDING_ENABLE_AFTER_PERMISSION] = it
            }

            xRatio?.let {
                preferences[KEY_X_RATIO] = it.asRatio()
            }

            yRatio?.let {
                preferences[KEY_Y_RATIO] = it.asRatio()
            }

            widthRatio?.let {
                preferences[KEY_WIDTH_RATIO] = it.asRatio()
            }

            fontSizeRatio?.let {
                preferences[KEY_FONT_SIZE_RATIO] = it.asRatio()
            }

            alphaRatio?.let {
                preferences[KEY_ALPHA_RATIO] = it.asRatio()
            }
        }
    }

    suspend fun disable() {
        updatePreference(
            enabled = false,
            pendingEnableAfterPermission = false
        )
    }

    suspend fun enableAfterPermissionGranted() {
        updatePreference(
            enabled = true,
            pendingEnableAfterPermission = false
        )
    }

    suspend fun markPendingEnableAfterPermission() {
        setPendingEnableAfterPermission(true)
    }

    suspend fun resetDisplayTuning() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_X_RATIO)
            preferences.remove(KEY_Y_RATIO)
            preferences.remove(KEY_WIDTH_RATIO)
            preferences.remove(KEY_FONT_SIZE_RATIO)
            preferences.remove(KEY_ALPHA_RATIO)
            preferences.remove(KEY_TEXT_COLOR)
        }
    }

    private suspend fun Preferences.toStatusBarLyricPreference(): StatusBarLyricPreference {
        val rawTextColor = this[KEY_TEXT_COLOR] ?: DEFAULT_TEXT_COLOR

        return StatusBarLyricPreference(
            xRatio = (this[KEY_X_RATIO] ?: DEFAULT_X).asRatio(),
            yRatio = resolveYRatio(this[KEY_Y_RATIO] ?: UNSET_Y),
            widthRatio = (this[KEY_WIDTH_RATIO] ?: DEFAULT_WIDTH).asRatio(),
            fontSizeRatio = (this[KEY_FONT_SIZE_RATIO] ?: DEFAULT_FONT_SIZE).asRatio(),
            alphaRatio = (this[KEY_ALPHA_RATIO] ?: DEFAULT_ALPHA).asRatio(),

            enabled = this[KEY_ENABLED] ?: false,
            contentType = this[KEY_CONTENT_TYPE] ?: CONTENT_TYPE_LYRIC,
            gravity = this[KEY_GRAVITY] ?: Gravity.CENTER,
            clickable = this[KEY_CLICKABLE] ?: false,
            showPaused = this[KEY_SHOW_PAUSED] ?: false,

            textColor = if (rawTextColor != DEFAULT_TEXT_COLOR) {
                rawTextColor
            } else {
                themeSettingPreferenceStore.getThemeColor()
            },
            pendingEnableAfterPermission =
                this[KEY_PENDING_ENABLE_AFTER_PERMISSION] ?: false
        )
    }

    private fun resolveYRatio(storedValue: Float): Float {
        return if (storedValue >= 0f) {
            storedValue.asRatio()
        } else {
            defaultYRatio()
        }
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
            ((DEFAULT_FONT_SIZE.asRatio() * 24f) + 8f) *
                    resources.displayMetrics.density

        val denominator = (screenHeight - fontHeight).coerceAtLeast(1f)

        return (statusBarHeight / denominator).asRatio()
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

    private suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T
    ) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private fun Float.asRatio(): Float {
        return coerceIn(0f, 1f)
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

        private const val DEFAULT_TEXT_COLOR = 0

        private val KEY_X_RATIO =
            floatPreferencesKey("sbar_lyric_x")

        private val KEY_Y_RATIO =
            floatPreferencesKey("sbar_lyric_y")

        private val KEY_WIDTH_RATIO =
            floatPreferencesKey("sbar_lyric_width")

        private val KEY_FONT_SIZE_RATIO =
            floatPreferencesKey("sbar_lyric_font_size")

        private val KEY_ALPHA_RATIO =
            floatPreferencesKey("sbar_lyric_alpha")

        private val KEY_ENABLED =
            booleanPreferencesKey("sbar_lyric_enable")

        private val KEY_CONTENT_TYPE =
            intPreferencesKey("sbar_lyric_content_type")

        private val KEY_GRAVITY =
            intPreferencesKey("sbar_lyric_gravity")

        private val KEY_CLICKABLE =
            booleanPreferencesKey("sbar_lyric_clickable")

        private val KEY_SHOW_PAUSED =
            booleanPreferencesKey("sbar_lyric_show_paused")

        private val KEY_TEXT_COLOR =
            intPreferencesKey("sbar_lyric_text_color")

        private val KEY_PENDING_ENABLE_AFTER_PERMISSION =
            booleanPreferencesKey("sbar_lyric_pending_enable_after_permission")
    }
}