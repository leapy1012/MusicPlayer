package gd.app.lib.view.viewpager

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.viewpager.widget.ViewPager

class CircleIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var viewPager: ViewPager? = null
    private var externalListener: ViewPager.OnPageChangeListener? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun setViewPager(pager: ViewPager) {
        viewPager = pager
        refreshDots(pager.currentItem)
        pager.addOnPageChangeListener(internalListener)
    }

    fun setOnPageChangeListener(listener: ViewPager.OnPageChangeListener?) {
        externalListener = listener
    }

    private val internalListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            externalListener?.onPageScrolled(position, positionOffset, positionOffsetPixels)
        }

        override fun onPageSelected(position: Int) {
            refreshDots(position)
            externalListener?.onPageSelected(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            externalListener?.onPageScrollStateChanged(state)
        }
    }

    private fun refreshDots(selected: Int) {
        val count = viewPager?.adapter?.count ?: 0
        if (count <= 0) {
            removeAllViews()
            return
        }
        if (childCount != count) {
            removeAllViews()
            repeat(count) { addView(createDot()) }
        }
        for (i in 0 until childCount) {
            val color = if (i == selected) 0xFFFFFFFF.toInt() else 0x66FFFFFF
            (getChildAt(i).background as GradientDrawable).setColor(color)
        }
    }

    private fun createDot(): View {
        val size = (context.resources.displayMetrics.density * 6f).toInt()
        val margin = (context.resources.displayMetrics.density * 4f).toInt()
        return View(context).apply {
            layoutParams = LayoutParams(size, size).apply {
                marginStart = margin
                marginEnd = margin
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FFFFFF)
            }
            setPadding(0, 0, 0, 0)
        }
    }
}
