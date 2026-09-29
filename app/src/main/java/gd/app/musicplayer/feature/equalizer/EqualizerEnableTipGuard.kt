package gd.app.musicplayer.feature.equalizer

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.CompoundButton
import com.coui.appcompat.tooltips.COUIToolTips
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.common.base.BaseActivity
import kotlin.math.sqrt

/**
 * EQ-off touch shield matching Music Player 8.1.5 `m5.f` + tip popup `u7.b`.
 *
 * While the equalizer toggle is off, a tap on registered shield views shows a
 * [COUIToolTips] pointing at the toggle.
 */
class EqualizerEnableTipGuard(
    private val activity: BaseActivity
) {
    private val shieldViews = mutableListOf<View>()
    private var equalizerToggle: CompoundButton? = null
    private var toolTips: COUIToolTips? = null
    private var enabled: Boolean = true
    private var pendingTapInShield: Boolean = false
    private var downRawX: Float = 0f
    private var downRawY: Float = 0f
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()

    fun setEqualizerToggle(toggle: CompoundButton) {
        equalizerToggle = toggle
    }

    fun addShieldViews(vararg views: View) {
        shieldViews.addAll(views)
    }

    fun clearShieldViews() {
        shieldViews.clear()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val toggle = equalizerToggle
        if (!enabled || toggle == null || toggle.isChecked) {
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
        val toolbar = activity.findViewById<View>(R.id.equalizer_app_bar) ?: return false
        if (!toolbar.isShown) return false
        val location = IntArray(2)
        toolbar.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + toolbar.width
        val bottom = top + toolbar.height
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
    }

    fun dismissTip() {
        toolTips?.dismissImmediately()
        toolTips = null
    }

    private fun showTip(anchor: View) {
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

        val tips = toolTips ?: COUIToolTips(activity).also {
            it.setContentRes(R.string.equalizer_toggle_tip)
            it.hideDismissButton()
            toolTips = it
        }
        // Beside the switch rather than below it: this COUIToolTips centres the arrow on the
        // bubble, so a bubble clamped to the screen edge would point away from the switch.
        tips.showWithDirection(anchor, COUIToolTips.ALIGN_START)
    }
}
