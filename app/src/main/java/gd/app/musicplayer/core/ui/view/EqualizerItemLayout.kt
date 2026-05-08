package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import kotlin.math.max

class EqualizerItemLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var bandCount: Int = DEFAULT_BAND_COUNT
        set(value) {
            val safeValue = max(1, value)
            if (field == safeValue) return

            field = safeValue
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalAvailableWidth = MeasureSpec.getSize(widthMeasureSpec)

        val itemWidth = if (totalAvailableWidth > 0) {
            totalAvailableWidth / bandCount
        } else {
            0
        }

        val exactItemWidthSpec = MeasureSpec.makeMeasureSpec(
            itemWidth,
            MeasureSpec.EXACTLY
        )

        super.onMeasure(exactItemWidthSpec, heightMeasureSpec)
    }

    companion object {
        private const val DEFAULT_BAND_COUNT = 5
    }
}