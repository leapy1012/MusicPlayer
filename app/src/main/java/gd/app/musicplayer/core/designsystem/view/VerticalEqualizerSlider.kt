package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.coui.appcompat.seekbar.COUISeekBar
import kotlin.math.roundToInt

/** Horizontal COUI slider rotated as a child, preserving its native thumb and touch animation. */
class VerticalEqualizerSlider @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val seekBar = COUISeekBar(context).apply {
        max = 1000
        layoutDirection = View.LAYOUT_DIRECTION_LTR
    }

    init {
        clipChildren = false
        addView(seekBar)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize((32 * resources.displayMetrics.density).roundToInt(), widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        seekBar.measure(MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        seekBar.layout(0, 0, height, width)
        seekBar.pivotX = 0f
        seekBar.pivotY = 0f
        seekBar.rotation = -90f
        seekBar.translationY = height.toFloat()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // Also used when a whole EQ band is disabled.
        getChildAt(0)?.isEnabled = enabled
    }
}
