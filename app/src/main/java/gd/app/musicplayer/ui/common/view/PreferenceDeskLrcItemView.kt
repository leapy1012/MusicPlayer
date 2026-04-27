package gd.app.musicplayer.ui.common.view

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.core.ui.extension.dpToPx

class PreferenceDeskLrcItemView(
    context: Context,
    attrs: AttributeSet?
) : ConstraintLayout(context, attrs), View.OnClickListener {

    private val summaryView: TextView
    private val lockButton: ImageView
    private val toggleButton: SelectBox

    private val preferences: PreferenceUtil
        get() = PreferenceUtil.getInstance(context)

    init {
        View.inflate(context, R.layout.preference_desk_lrc_item, this)

        val verticalPadding = context.dpToPx(12f)
        setPadding(0, verticalPadding, 0, verticalPadding)

        findViewById<TextView>(R.id.title).setText(R.string.desktop_lrc)

        summaryView = findViewById(R.id.summary)
        lockButton = findViewById<ImageView>(R.id.desk_lrc_lock).also {
            it.setOnClickListener(this)
        }
        toggleButton = findViewById<SelectBox>(R.id.checkbox).also {
            it.setOnClickListener(this)
            it.setImageResource(R.drawable.vector_toggle_selector)
        }

        setOnClickListener(this)
        syncUi()
    }

    fun disableDesktopLyricsIfOverlayPermissionWasRevoked() {
        if (!preferences.isDesktopLyricsVisible() || hasOverlayPermission()) return

        preferences.setDesktopLyricsVisible(false)
        preferences.putBooleanPreference(KEY_PENDING_ENABLE_AFTER_PERMISSION, false)
        syncUi()
    }

    fun resumeDesktopLyricsAfterOverlayPermissionChange() {
        if (!preferences.getBooleanPreference(KEY_PENDING_ENABLE_AFTER_PERMISSION, false)) return

        preferences.putBooleanPreference(KEY_PENDING_ENABLE_AFTER_PERMISSION, false)
        if (hasOverlayPermission()) {
            preferences.setDesktopLyricsVisible(true)
        }
        syncUi()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncUi()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.desk_lrc_lock -> toggleLockState()
            else -> toggleDesktopLyrics()
        }
    }

    private fun toggleDesktopLyrics() {
        val currentlyVisible = preferences.isDesktopLyricsVisible()
        if (currentlyVisible) {
            preferences.setDesktopLyricsVisible(false)
            syncUi()
            return
        }

        if (!hasOverlayPermission()) {
            preferences.putBooleanPreference(KEY_PENDING_ENABLE_AFTER_PERMISSION, true)
            ToastUtil.show(context, R.string.float_window_permission_tip)
            openOverlayPermissionSettings()
            return
        }

        preferences.putBooleanPreference(KEY_PENDING_ENABLE_AFTER_PERMISSION, false)
        preferences.setDesktopLyricsVisible(true)
        syncUi()
    }

    private fun toggleLockState() {
        if (!preferences.isDesktopLyricsVisible()) return

        preferences.setDesktopLyricsLocked(!preferences.isDesktopLyricsLocked())
        syncUi()
    }

    private fun syncUi() {
        val isVisible = preferences.isDesktopLyricsVisible() && hasOverlayPermission()
        if (preferences.isDesktopLyricsVisible() != isVisible) {
            preferences.setDesktopLyricsVisible(isVisible)
        }

        val isLocked = isVisible && preferences.isDesktopLyricsLocked()
        toggleButton.isSelected = isVisible
        lockButton.visibility = if (isVisible) View.VISIBLE else View.GONE
        lockButton.isSelected = isLocked

        if (isLocked) {
            summaryView.visibility = View.VISIBLE
            summaryView.setText(R.string.desk_lrc_locked_tips_2)
        } else {
            summaryView.visibility = View.GONE
            summaryView.text = null
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    private fun openOverlayPermissionSettings() {
        val activity = context as? Activity ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )

        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            ToastUtil.show(context, R.string.permission_open_failed)
        }
    }

    companion object {
        private const val KEY_PENDING_ENABLE_AFTER_PERMISSION =
            "desktop_lyric_pending_enable_after_permission"
    }
}
