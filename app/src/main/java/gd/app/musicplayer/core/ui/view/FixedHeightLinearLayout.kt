package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class FixedHeightLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var fixedHeightPx: Int = NO_FIXED_HEIGHT

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val resolvedHeight = if (fixedHeightPx > 0) {
            fixedHeightPx
        } else {
            MeasureSpec.getSize(heightMeasureSpec)
        }

        val finalHeightMeasureSpec = if (resolvedHeight > 0) {
            MeasureSpec.makeMeasureSpec(resolvedHeight, MeasureSpec.EXACTLY)
        } else {
            heightMeasureSpec
        }

        super.onMeasure(widthMeasureSpec, finalHeightMeasureSpec)
    }

    fun setFixedHeight(heightPx: Int) {
        fixedHeightPx = heightPx
        requestLayout()
    }

    companion object {
        private const val NO_FIXED_HEIGHT = -1
    }
}
