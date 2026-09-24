package gd.app.musicplayer.feature.equalizer

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.isRtl
import gd.app.musicplayer.core.designsystem.view.SelectBox
import gd.app.musicplayer.ui.common.base.BaseActivity
import kotlin.math.sqrt

/**
 * EQ-off touch shield matching Music Player 8.1.5 `m5.f` + tip popup `u7.b`.
 *
 * While the equalizer toggle is off, a tap on registered shield views shows
 * [R.layout.popup_enable_equalizer] anchored under the toggle.
 */
class EqualizerEnableTipGuard(
    private val activity: BaseActivity
) {
    private val shieldViews = mutableListOf<View>()
    private var equalizerToggle: SelectBox? = null
    private var tipAccentColor: Int = 0
    private var enabled: Boolean = true
    private var pendingTapInShield: Boolean = false
    private var downRawX: Float = 0f
    private var downRawY: Float = 0f
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()

    fun setEqualizerToggle(toggle: SelectBox) {
        equalizerToggle = toggle
    }

    fun addShieldViews(vararg views: View) {
        shieldViews.addAll(views)
    }

    fun clearShieldViews() {
        shieldViews.clear()
    }

    fun setTipAccentColor(color: Int) {
        tipAccentColor = color
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val toggle = equalizerToggle
        if (!enabled || toggle == null || toggle.isSelected) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                pendingTapInShield = !isInToolbarArea(downRawX, downRawY) &&
                    isInAnyShield(downRawX, downRawY)
            }

            MotionEvent.ACTION_UP -> {
                if (pendingTapInShield) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (sqrt(dx * dx + dy * dy) <= touchSlop) {
                        showTip(toggle)
                    }
                }
                pendingTapInShield = false
            }

            MotionEvent.ACTION_CANCEL -> {
                pendingTapInShield = false
            }
        }
        return false
    }

    private fun isInAnyShield(rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        for (view in shieldViews) {
            if (!view.isShown) continue
            view.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val right = left + view.width
            val bottom = top + view.height
            if (rawX >= left && rawX <= right && rawY >= top && rawY <= bottom) {
                return true
            }
        }
        return false
    }

    private fun isInToolbarArea(rawX: Float, rawY: Float): Boolean {
        val toolbar = activity.findViewById<View>(R.id.equalizer_back)?.parent as? View
            ?: return false
        if (!toolbar.isShown) return false
        val location = IntArray(2)
        toolbar.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + toolbar.width
        val bottom = top + toolbar.height
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
    }

    private fun showTip(anchor: View) {
        val content = LayoutInflater.from(activity)
            .inflate(R.layout.popup_enable_equalizer, null, false)
        val accent = if (tipAccentColor != 0) {
            tipAccentColor
        } else {
            activity.themeRepo.getCorePalette().getAccentColor()
        }
        content.background = GradientDrawable().apply {
            cornerRadius = activity.dpToPx(4f).toFloat()
            setColor(accent)
        }

        val popup = PopupWindow(
            content,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            animationStyle = R.style.EditMorePopupAnim
            isOutsideTouchable = true
            elevation = activity.dpToPx(4f).toFloat()
        }

        if (anchor.top < 0) {
            var scrollCandidate: View? = anchor
            while (true) {
                val parent = scrollCandidate?.parent
                if (parent !is View) break
                scrollCandidate = parent
                if (parent.canScrollVertically(1)) {
                    parent.scrollTo(0, 0)
                    break
                }
            }
        }

        val xOff = if (activity.isRtl()) -anchor.width else 0
        val yOff = activity.dpToPx(8f)
        popup.showAsDropDown(anchor, xOff, yOff, Gravity.START)
    }
}
