package gd.app.musicplayer.feature.selection

import android.util.SparseIntArray
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

abstract class ScrollAwareAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    private var scrollCalculator: ScrollCalculator? = null
    private var attachedRecyclerView: RecyclerView? = null
    private var scrollListener: OnScrollCalculatorReadyListener? = null

    private val dataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            rebuildScrollCalculator()
        }
    }

    abstract fun getItemExtent(viewType: Int): Int

    fun getScrollProgress(): Float {
        return scrollCalculator?.getScrollProgress() ?: 0f
    }

    fun refreshScrollCalculator() {
        if (attachedRecyclerView == null || scrollCalculator == null) return
        rebuildScrollCalculator()
    }

    fun scrollToProgress(progress: Float) {
        scrollCalculator?.scrollToProgress(progress)
    }

    fun setOnScrollCalculatorReadyListener(listener: OnScrollCalculatorReadyListener?) {
        scrollListener = listener
        val calculator = scrollCalculator ?: return
        listener?.onReady(calculator)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        registerAdapterDataObserver(dataObserver)
        attachedRecyclerView = recyclerView
        rebuildScrollCalculator()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        unregisterAdapterDataObserver(dataObserver)
        attachedRecyclerView = null
    }

    private fun rebuildScrollCalculator() {
        scrollCalculator = null

        val recyclerView = attachedRecyclerView ?: return
        val layoutManager = recyclerView.layoutManager

        scrollCalculator = when (layoutManager) {
            is GridLayoutManager -> GridScrollCalculator(recyclerView, layoutManager, this)
            is LinearLayoutManager -> LinearScrollCalculator(recyclerView, layoutManager, this)
            else -> null
        }

        scrollCalculator?.build()
        scrollCalculator?.let { scrollListener?.onReady(it) }
    }

    abstract class ScrollCalculator(
        protected val recyclerView: RecyclerView,
        protected val layoutManager: LinearLayoutManager,
        protected val adapter: ScrollAwareAdapter<*>
    ) {
        protected lateinit var itemOffsets: IntArray
        protected var totalContentSize: Int = 0

        protected abstract fun accumulateOffset(
            position: Int,
            viewType: Int,
            itemCount: Int,
            itemExtent: Int
        )

        fun getScrollProgress(): Float {
            if (!::itemOffsets.isInitialized) return 0f

            recyclerView.setPadding(0, 0, 0, 0)

            val viewportHeight = recyclerView.height
            if (viewportHeight == 0 || totalContentSize <= viewportHeight) {
                return 0f
            }

            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            if (firstVisiblePosition < 0 || firstVisiblePosition >= itemOffsets.size) {
                return 0f
            }

            var topOffset = itemOffsets[firstVisiblePosition]
            val firstChild = layoutManager.getChildAt(0)
            if (firstChild != null) {
                topOffset -= firstChild.top
            }

            return topOffset.toFloat() / (totalContentSize - viewportHeight).toFloat()
        }

        fun build() {
            val itemCount = adapter.itemCount
            itemOffsets = IntArray(itemCount)
            totalContentSize = 0

            val extentCache = SparseIntArray()

            for (position in 0 until itemCount) {
                val viewType = adapter.getItemViewType(position)
                val itemExtent = if (extentCache.indexOfKey(viewType) >= 0) {
                    extentCache.get(viewType)
                } else {
                    adapter.getItemExtent(viewType).also {
                        extentCache.put(viewType, it)
                    }
                }

                accumulateOffset(position, viewType, itemCount, itemExtent)
            }
        }

        fun scrollToProgress(progress: Float) {
            if (!::itemOffsets.isInitialized) return

            val viewportHeight = recyclerView.height
            if (viewportHeight == 0 || totalContentSize <= viewportHeight) return

            val targetOffset = (progress * (totalContentSize - viewportHeight)).toInt()

            var targetPosition = itemOffsets.indexOfFirst { it >= targetOffset }
            if (targetPosition == -1) {
                targetPosition = itemOffsets.lastIndex
            }

            layoutManager.scrollToPositionWithOffset(
                targetPosition,
                abs(itemOffsets[targetPosition] - targetOffset)
            )
        }
    }

    private class GridScrollCalculator(
        recyclerView: RecyclerView,
        gridLayoutManager: GridLayoutManager,
        adapter: ScrollAwareAdapter<*>
    ) : ScrollCalculator(recyclerView, gridLayoutManager, adapter) {

        private val spanCount = gridLayoutManager.spanCount
        private val spanSizeLookup = gridLayoutManager.spanSizeLookup
        private var currentRowSpan = 0

        override fun accumulateOffset(
            position: Int,
            viewType: Int,
            itemCount: Int,
            itemExtent: Int
        ) {
            val itemSpan = spanSizeLookup?.getSpanSize(position) ?: 1

            if (currentRowSpan + itemSpan > spanCount) {
                currentRowSpan = itemSpan
                totalContentSize += itemExtent
                itemOffsets[position] = totalContentSize
            } else {
                currentRowSpan += itemSpan
                itemOffsets[position] = totalContentSize
            }

            if (position == itemCount - 1 && currentRowSpan > 0) {
                totalContentSize += itemExtent
            }
        }
    }

    private class LinearScrollCalculator(
        recyclerView: RecyclerView,
        linearLayoutManager: LinearLayoutManager,
        adapter: ScrollAwareAdapter<*>
    ) : ScrollCalculator(recyclerView, linearLayoutManager, adapter) {

        override fun accumulateOffset(
            position: Int,
            viewType: Int,
            itemCount: Int,
            itemExtent: Int
        ) {
            itemOffsets[position] = totalContentSize
            totalContentSize += itemExtent
        }
    }

    interface OnScrollCalculatorReadyListener {
        fun onReady(calculator: ScrollCalculator)
    }
}