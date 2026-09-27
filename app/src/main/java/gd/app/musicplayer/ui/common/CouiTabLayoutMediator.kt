package gd.app.musicplayer.ui.common

import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.coui.appcompat.tablayout.COUITab
import com.coui.appcompat.tablayout.COUITabLayout
import java.lang.ref.WeakReference

/**
 * COUI tab ↔ ViewPager2 glue (same idle-tap behavior as [MusicTabLayoutMediator],
 * but for [COUITabLayout] instead of Material TabLayout).
 */
class CouiTabLayoutMediator(
    private val tabLayout: COUITabLayout,
    private val viewPager: ViewPager2,
    private val autoRefresh: Boolean = true,
    private val tabConfigurationStrategy: TabConfigurationStrategy
) {
    fun interface TabConfigurationStrategy {
        fun onConfigureTab(tab: COUITab, position: Int)
    }

    private var adapter: RecyclerView.Adapter<*>? = null
    private var attached = false
    private var onPageChangeCallback: TabLayoutOnPageChangeCallback? = null
    private var onTabSelectedListener: COUITabLayout.OnTabSelectedListener? = null
    private var pagerAdapterObserver: RecyclerView.AdapterDataObserver? = null

    fun attach() {
        check(!attached) { "CouiTabLayoutMediator is already attached" }
        val pagerAdapter = viewPager.adapter
            ?: error("CouiTabLayoutMediator attached before ViewPager2 has an adapter")

        adapter = pagerAdapter
        attached = true

        val pageChangeCallback = TabLayoutOnPageChangeCallback(tabLayout).also {
            onPageChangeCallback = it
            viewPager.registerOnPageChangeCallback(it)
        }

        val tabSelectedListener = ViewPagerOnTabSelectedListener(viewPager, pageChangeCallback).also {
            onTabSelectedListener = it
            tabLayout.addOnTabSelectedListener(it)
        }

        if (autoRefresh) {
            val observer = PagerAdapterObserver().also { pagerAdapterObserver = it }
            pagerAdapter.registerAdapterDataObserver(observer)
        }

        populateTabsFromPagerAdapter()
        tabLayout.setScrollPosition(viewPager.currentItem, 0f, true)
    }

    fun detach() {
        if (autoRefresh) {
            pagerAdapterObserver?.let { observer ->
                adapter?.unregisterAdapterDataObserver(observer)
            }
            pagerAdapterObserver = null
        }

        onTabSelectedListener?.let(tabLayout::removeOnTabSelectedListener)
        onPageChangeCallback?.let(viewPager::unregisterOnPageChangeCallback)

        onTabSelectedListener = null
        onPageChangeCallback = null
        adapter = null
        attached = false
    }

    fun isAttached(): Boolean = attached

    private fun populateTabsFromPagerAdapter() {
        tabLayout.removeAllTabs()
        val pagerAdapter = adapter ?: return
        val itemCount = pagerAdapter.itemCount
        for (index in 0 until itemCount) {
            val tab = tabLayout.newTab()
            tabConfigurationStrategy.onConfigureTab(tab, index)
            tabLayout.addTab(tab, false)
        }
        if (itemCount > 0) {
            val selected = minOf(viewPager.currentItem, tabLayout.tabCount - 1)
            if (selected != tabLayout.selectedTabPosition) {
                tabLayout.selectTab(tabLayout.getTabAt(selected))
            }
        }
    }

    private inner class PagerAdapterObserver : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = populateTabsFromPagerAdapter()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = populateTabsFromPagerAdapter()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) =
            populateTabsFromPagerAdapter()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = populateTabsFromPagerAdapter()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = populateTabsFromPagerAdapter()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
            populateTabsFromPagerAdapter()
    }

    private class TabLayoutOnPageChangeCallback(
        tabLayout: COUITabLayout
    ) : ViewPager2.OnPageChangeCallback() {
        private val tabLayoutRef = WeakReference(tabLayout)
        private var previousScrollState = ViewPager2.SCROLL_STATE_IDLE
        private var scrollState = ViewPager2.SCROLL_STATE_IDLE
        private var isUserClickTriggered = false

        fun setUserClickTriggered(triggered: Boolean) {
            isUserClickTriggered = triggered
        }

        override fun onPageScrollStateChanged(state: Int) {
            previousScrollState = scrollState
            scrollState = state
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            if (isUserClickTriggered) return

            val tabLayout = tabLayoutRef.get() ?: return
            val updateSelectedTabView =
                scrollState != ViewPager2.SCROLL_STATE_SETTLING ||
                    previousScrollState == ViewPager2.SCROLL_STATE_DRAGGING
            val updateIndicator =
                !(scrollState == ViewPager2.SCROLL_STATE_SETTLING &&
                    previousScrollState == ViewPager2.SCROLL_STATE_IDLE)
            tabLayout.setScrollPosition(
                position,
                positionOffset,
                updateSelectedTabView,
                updateIndicator
            )
        }

        override fun onPageSelected(position: Int) {
            val tabLayout = tabLayoutRef.get() ?: return
            if (tabLayout.selectedTabPosition == position || position >= tabLayout.tabCount) {
                return
            }
            val updateIndicator =
                scrollState == ViewPager2.SCROLL_STATE_IDLE ||
                    (scrollState == ViewPager2.SCROLL_STATE_SETTLING &&
                        previousScrollState == ViewPager2.SCROLL_STATE_IDLE)
            tabLayout.selectTab(tabLayout.getTabAt(position), updateIndicator)
        }
    }

    private class ViewPagerOnTabSelectedListener(
        private val viewPager: ViewPager2,
        private val onPageChangeCallback: TabLayoutOnPageChangeCallback
    ) : COUITabLayout.OnTabSelectedListener {

        private val resetUserClickTriggered = Runnable {
            onPageChangeCallback.setUserClickTriggered(false)
        }

        override fun onTabSelected(tab: COUITab) {
            if (viewPager.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
                viewPager.setCurrentItem(tab.position, true)
                return
            }
            onPageChangeCallback.setUserClickTriggered(true)
            viewPager.setCurrentItem(tab.position, false)
            viewPager.post(resetUserClickTriggered)
        }

        override fun onTabUnselected(tab: COUITab) = Unit

        override fun onTabReselected(tab: COUITab) = Unit
    }
}
