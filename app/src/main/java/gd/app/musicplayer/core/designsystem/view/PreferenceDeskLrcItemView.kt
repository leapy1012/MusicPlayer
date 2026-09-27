package gd.app.musicplayer.core.designsystem.view

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.coui.appcompat.cardlist.COUICardListHelper
import com.coui.appcompat.couiswitch.COUISwitch
import com.coui.appcompat.preference.COUICustomListSelectedLinearLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.datastore.DesktopLyricPreference

/**
 * Desktop lyrics row that **is** a COUI card preference row.
 */
class PreferenceDeskLrcItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : COUICustomListSelectedLinearLayout(context, attrs), View.OnClickListener {

    private val summaryView: TextView
    private val widgetFrame: LinearLayout
    private val lockButton: ImageView
    private val couiSwitch: COUISwitch

    private var state: DesktopLyricPreference = DesktopLyricPreference()

    var onVisibleChanged: ((Boolean) -> Unit)? = null
    var onLockedChanged: ((Boolean) -> Unit)? = null
    var onPendingEnableAfterPermissionChanged: ((Boolean) -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false
        adoptCouiPreferenceChrome()

        findViewById<TextView>(android.R.id.title).setText(R.string.desktop_lrc)
        summaryView = findViewById(android.R.id.summary)
        widgetFrame = findViewById(android.R.id.widget_frame)

        val density = resources.displayMetrics.density
        val lockSize = (40 * density).toInt()
        val lockPad = (7 * density).toInt()
        lockButton = ImageView(context).apply {
            id = R.id.desk_lrc_lock
            layoutParams = LinearLayout.LayoutParams(lockSize, lockSize).also {
                it.marginEnd = (4 * density).toInt()
            }
            setPadding(lockPad, lockPad, lockPad, lockPad)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.vector_desktop_lrc_lock_setting_selector)
            setOnClickListener(this@PreferenceDeskLrcItemView)
        }

        couiSwitch = LayoutInflater.from(context).inflate(
            com.coui.appcompat.R.layout.coui_preference_widget_switch,
            widgetFrame,
            false
        ) as COUISwitch
        couiSwitch.isClickable = false
        couiSwitch.isFocusable = false

        widgetFrame.orientation = HORIZONTAL
        widgetFrame.addView(lockButton)
        widgetFrame.addView(couiSwitch)

        ensureInteractive()
        render(state)
    }

    /**
     * PreferenceFragment rebinds overwrite clickable/OnClickListener when
     * [androidx.preference.Preference.isSelectable] is false. Call after bind.
     */
    fun ensureInteractive() {
        isClickable = true
        isFocusable = true
        isEnabled = true
        lockButton.setOnClickListener(this)
        super.setOnClickListener(this)
    }

    private fun adoptCouiPreferenceChrome() {
        val source = LayoutInflater.from(context).inflate(
            com.coui.appcompat.R.layout.coui_preference,
            null,
            false
        ) as COUICustomListSelectedLinearLayout

        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = source.minimumHeight
        setPaddingRelative(
            source.paddingStart,
            source.paddingTop,
            source.paddingEnd,
            source.paddingBottom
        )
        clipChildren = source.clipChildren
        clipToPadding = source.clipToPadding

        while (source.childCount > 0) {
            val child = source.getChildAt(0)
            source.removeViewAt(0)
            addView(child)
        }
    }

    fun setCardPositionInGroup(position: Int) {
        COUICardListHelper.setItemCardBackground(this, position)
    }

    fun render(preference: DesktopLyricPreference) {
        state = preference

        val isVisible = preference.visible && hasOverlayPermission()
        val isLocked = isVisible && preference.locked

        couiSwitch.isChecked = isVisible
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

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isEventInside(lockButton, event)) {
            return false
        }
        return true
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.desk_lrc_lock -> toggleLockState()
            else -> toggleDesktopLyrics()
        }
    }

    private fun isEventInside(child: View, event: MotionEvent): Boolean {
        if (child.visibility != VISIBLE) return false
        val loc = IntArray(2)
        child.getLocationOnScreen(loc)
        val x = event.rawX
        val y = event.rawY
        return x >= loc[0] && x < loc[0] + child.width && y >= loc[1] && y < loc[1] + child.height
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
