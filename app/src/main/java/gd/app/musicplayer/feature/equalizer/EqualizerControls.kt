package gd.app.musicplayer.feature.equalizer

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.CompoundButton
import com.coui.appcompat.seekbar.COUISeekBar
import gd.app.musicplayer.core.common.extension.installCouiPressFeedback
import gd.app.musicplayer.core.designsystem.view.SeekBar
import kotlin.math.abs
import kotlin.math.roundToInt

/** Card row that toggles [switch] on tap, like a COUI switch preference. */
internal fun View.bindSwitchRow(switch: CompoundButton) {
    // The press mask is rectangular; the rounded card has to clip it.
    (parent as? View)?.clipToOutline = true
    installCouiPressFeedback()
    // Prefer row taps over thumb-drags so ViewPager2 cannot steal the gesture.
    switch.isClickable = false
    switch.isFocusable = false
    setOnClickListener {
        if (switch.isEnabled) switch.toggle()
    }
}

/**
 * Claims the gesture on touch-down so ViewPager2 can't turn a slider drag into a page swipe,
 * then hands it back to the ancestors if the finger moves mostly across the slider axis.
 */
@SuppressLint("ClickableViewAccessibility")
internal fun View.claimSliderDrags(vertical: Boolean) {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var decided = false
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                decided = false
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> if (!decided) {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    decided = true
                    val acrossAxis = if (vertical) dx > dy else dy > dx
                    if (acrossAxis) view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        false
    }
}

/** Also claims horizontal drags, see [claimSliderDrags]. */
internal fun COUISeekBar.setOnSliderChangeListener(
    onTrackingChanged: (tracking: Boolean) -> Unit,
    onProgressChanged: (progress: Int, fromUser: Boolean) -> Unit
) {
    claimSliderDrags(vertical = false)
    setOnSeekBarChangeListener(object : COUISeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: COUISeekBar, progress: Int, fromUser: Boolean) {
            onProgressChanged(progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: COUISeekBar) {
            onTrackingChanged(true)
        }

        override fun onStopTrackingTouch(seekBar: COUISeekBar) {
            onTrackingChanged(false)
        }
    })
}

/** Pictured-theme horizontal skeuomorphic seek. */
internal fun SeekBar.setOnSliderChangeListener(
    onTrackingChanged: (tracking: Boolean) -> Unit,
    onProgressChanged: (progress: Int, fromUser: Boolean) -> Unit
) {
    claimSliderDrags(vertical = false)
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            onProgressChanged(progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            onTrackingChanged(true)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            onTrackingChanged(false)
        }
    })
}

internal fun Float.toSliderProgress(max: Int): Int = (coerceIn(0f, 1f) * max).roundToInt()

internal fun Int.toPercentText(max: Int): String {
    val percent = if (max <= 0) 0 else (this * 100f / max).roundToInt()
    return "$percent%"
}
