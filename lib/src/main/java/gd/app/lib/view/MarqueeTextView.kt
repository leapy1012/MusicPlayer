package gd.app.lib.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import android.text.TextUtils

class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var horizontalScrollable: Boolean = true

    init {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = 1
        isSingleLine = true
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        return horizontalScrollable && super.canScrollHorizontally(direction)
    }

    override fun isSelected(): Boolean = true

    fun setHorizontalScrollable(enable: Boolean) {
        horizontalScrollable = enable
    }
}