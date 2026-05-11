package gd.app.musicplayer.core.designsystem.view

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
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.data.local.preference.DesktopLyricPreference

class PreferenceDeskLrcItemView(
    context: Context,
    attrs: AttributeSet?
) : ConstraintLayout(context, attrs), View.OnClickListener {

    private val summaryView: TextView
    private val lockButton: ImageView
    private val toggleButton: gd.app.musicplayer.core.designsystem.view.SelectBox

    private var state: DesktopLyricPreference = DesktopLyricPreference()

    var onVisibleChanged: ((Boolean) -> Unit)? = null
    var onLockedChanged: ((Boolean) -> Unit)? = null
    var onPendingEnableAfterPermissionChanged: ((Boolean) -> Unit)? = null

    init {
        inflate(context, R.layout.preference_desk_lrc_item, this)

        val verticalPadding = context.dpToPx(12f)
        setPadding(0, verticalPadding, 0, verticalPadding)

        findViewById<TextView>(R.id.title).setText(R.string.desktop_lrc)

        summaryView = findViewById(R.id.summary)

        lockButton = findViewById<ImageView>(R.id.desk_lrc_lock).also {
            it.setOnClickListener(this)
        }

        toggleButton = findViewById<gd.app.musicplayer.core.designsystem.view.SelectBox>(R.id.checkbox).also {
            it.setOnClickListener(this)
            it.setImageResource(R.drawable.vector_toggle_selector)
        }

        setOnClickListener(this)
        render(state)
    }

    fun render(preference: DesktopLyricPreference) {
        state = preference

        val isVisible = preference.visible && hasOverlayPermission()
        val isLocked = isVisible && preference.locked

        toggleButton.isSelected = isVisible
        lockButton.visibility = if (isVisible) VISIBLE else GONE
        lockButton.isSelected = isLocked

        if (isLocked) {
            summaryView.visibility = VISIBLE
            summaryView.setText(R.string.desk_lrc_locked_tips_2)
        } else {
            summaryView.visibility = GONE
            summaryView.text = null
        }
    }

    fun disableDesktopLyricsIfOverlayPermissionWasRevoked() {
        if (!state.visible || hasOverlayPermission()) return

        onVisibleChanged?.invoke(false)
        onPendingEnableAfterPermissionChanged?.invoke(false)

        render(
            state.copy(
                visible = false,
                pendingEnableAfterPermission = false
            )
        )
    }

    fun resumeDesktopLyricsAfterOverlayPermissionChange() {
        if (!state.pendingEnableAfterPermission) return

        onPendingEnableAfterPermissionChanged?.invoke(false)

        if (hasOverlayPermission()) {
            onVisibleChanged?.invoke(true)
            render(
                state.copy(
                    visible = true,
                    pendingEnableAfterPermission = false
                )
            )
        } else {
            render(
                state.copy(
                    pendingEnableAfterPermission = false
                )
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        render(state)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.desk_lrc_lock -> toggleLockState()
            else -> toggleDesktopLyrics()
        }
    }

    private fun toggleDesktopLyrics() {
        val currentlyVisible = state.visible && hasOverlayPermission()

        if (currentlyVisible) {
            onVisibleChanged?.invoke(false)
            render(state.copy(visible = false))
            return
        }

        if (!hasOverlayPermission()) {
            onPendingEnableAfterPermissionChanged?.invoke(true)
            ToastUtil.show(context, R.string.float_window_permission_tip)
            openOverlayPermissionSettings()
            return
        }

        onPendingEnableAfterPermissionChanged?.invoke(false)
        onVisibleChanged?.invoke(true)

        render(
            state.copy(
                visible = true,
                pendingEnableAfterPermission = false
            )
        )
    }

    private fun toggleLockState() {
        val isVisible = state.visible && hasOverlayPermission()
        if (!isVisible) return

        val newLocked = !state.locked
        onLockedChanged?.invoke(newLocked)

        render(state.copy(locked = newLocked))
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)
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
}