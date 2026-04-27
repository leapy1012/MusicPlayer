package gd.app.lib.view.loop

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

@Suppress("DEPRECATION")
class LoopViewPager : ViewPager {

    var mOuterPageChangeListener: OnPageChangeListener? = null
    private var mAdapter: LoopPagerAdapterWrapper? = null
    private var mBoundaryCaching = DEFAULT_BOUNDARY_CASHING

    companion object {
        private const val DEFAULT_BOUNDARY_CASHING = false

        /**
         * helper function which may be used when implementing FragmentPagerAdapter
         *
         * @param position
         * @param count
         * @return (position-1)%count
         */
        fun toRealPosition(position: Int, count: Int): Int {
            var position = position
            position -= 1
            if (position < 0) {
                position += count
            } else {
                position %= count
            }
            return position
        }
    }

    /**
     * If set to true, the boundary views (i.e. first and last) will never be destroyed
     * This may help to prevent "blinking" of some views
     *
     * @param flag
     */
    fun setBoundaryCaching(flag: Boolean) {
        mBoundaryCaching = flag
        mAdapter?.setBoundaryCaching(flag)
    }

    override fun setAdapter(adapter: PagerAdapter?) {
        mAdapter?.release()
        if (adapter == null) {
            mAdapter = null
            super.setAdapter(null)
            return
        }

        val wrapper = LoopPagerAdapterWrapper(adapter)
        wrapper.setBoundaryCaching(mBoundaryCaching)
        mAdapter = wrapper
        super.setAdapter(wrapper)
        setCurrentItem(0, false)
    }

    override fun getAdapter(): PagerAdapter? {
        return mAdapter?.getRealAdapter()
    }

    override fun getCurrentItem(): Int {
        return mAdapter?.toRealPosition(super.getCurrentItem()) ?: 0
    }

    override fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        val wrapper = mAdapter
        if (wrapper == null) {
            super.setCurrentItem(item, smoothScroll)
            return
        }

        super.setCurrentItem(wrapper.toInnerPosition(item), smoothScroll)
    }

    override fun setCurrentItem(item: Int) {
        if (currentItem != item) {
            setCurrentItem(item, true)
        }
    }

    @Deprecated("Uses legacy ViewPager listener API")
    override fun setOnPageChangeListener(listener: OnPageChangeListener?) {
        mOuterPageChangeListener = listener
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        super.setOnPageChangeListener(onPageChangeListener)
    }

    private val onPageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
        private var mPreviousOffset = -1f
        private var mPreviousPosition = -1f

        override fun onPageSelected(position: Int) {
            val wrapper = mAdapter ?: return
            val realPosition = wrapper.toRealPosition(position)
            if (mPreviousPosition != realPosition.toFloat()) {
                mPreviousPosition = realPosition.toFloat()
                mOuterPageChangeListener?.onPageSelected(realPosition)
            }
        }

        override fun onPageScrolled(
            position: Int, positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            var realPosition = position
            val wrapper = mAdapter
            if (wrapper != null) {
                realPosition = wrapper.toRealPosition(position)

                if (positionOffset == 0f && mPreviousOffset == 0f && (position == 0 || position == wrapper.count - 1)) {
                    setCurrentItem(realPosition, false)
                }
            }

            mPreviousOffset = positionOffset
            mOuterPageChangeListener?.let { listener ->
                if (wrapper == null || realPosition != wrapper.realCount - 1) {
                    listener.onPageScrolled(
                        realPosition,
                        positionOffset, positionOffsetPixels
                    )
                } else {
                    if (positionOffset > .5) {
                        listener.onPageScrolled(0, 0f, 0)
                    } else {
                        listener.onPageScrolled(
                            realPosition,
                            0f, 0
                        )
                    }
                }
            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            val wrapper = mAdapter
            if (wrapper != null) {
                val position = super@LoopViewPager.getCurrentItem()
                val realPosition = wrapper.toRealPosition(position)
                if (state == SCROLL_STATE_IDLE
                    && (position == 0 || position == wrapper.count - 1)
                ) {
                    setCurrentItem(realPosition, false)
                }
            }
            mOuterPageChangeListener?.onPageScrollStateChanged(state)
        }
    }
}
