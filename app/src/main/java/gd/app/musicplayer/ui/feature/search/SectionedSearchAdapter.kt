package gd.app.musicplayer.ui.feature.search

import android.util.SparseArray
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder

abstract class SectionedSearchAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val positionCache = SparseArray<PositionInfo>()
    private var cachedItemCount: Int? = null

    protected fun clearPositionCache() {
        positionCache.clear()
        cachedItemCount = null
    }

    private fun mapAdapterPosition(position: Int): PositionInfo {
        positionCache[position]?.let { return it }

        var adapterOffset = 0
        for (sectionIndex in 0 until getSectionCount()) {
            if (position == adapterOffset) {
                return PositionInfo.Header(sectionIndex).also { positionCache.put(position, it) }
            }

            val childCount = getChildCount(sectionIndex)
            val childOffset = position - adapterOffset - 1
            if (childOffset in 0 until childCount) {
                return PositionInfo.Item(sectionIndex, childOffset).also {
                    positionCache.put(position, it)
                }
            }

            adapterOffset += childCount + 1
        }

        error("Invalid adapter position: $position")
    }

    protected fun adapterPositionForSection(sectionIndex: Int): Int {
        var position = 0
        for (index in 0 until sectionIndex) {
            position += getChildCount(index) + 1
        }
        return position
    }

    override fun getItemCount(): Int {
        cachedItemCount?.let { return it }

        val itemCount = buildSectionIndex().sumOf { indexedSection ->
            1 + indexedSection.childCount
        }
        cachedItemCount = itemCount
        return itemCount
    }

    override fun getItemViewType(position: Int): Int {
        return when (val info = mapAdapterPosition(position)) {
            is PositionInfo.Header -> VIEW_TYPE_HEADER
            is PositionInfo.Item -> getItemViewType(info.sectionIndex, info.childIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            onCreateHeaderViewHolder(parent)
        } else {
            onCreateItemViewHolder(parent, viewType)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(holder, position, emptyList())
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        bindViewHolder(holder, position, payloads)
    }

    fun reload() {
        val oldCount = getItemCount()
        clearPositionCache()
        val newCount = getItemCount()

        when {
            oldCount == 0 && newCount == 0 -> Unit
            oldCount == 0 -> notifyItemRangeInserted(0, newCount)
            newCount == 0 -> notifyItemRangeRemoved(0, oldCount)
            oldCount == newCount -> notifyItemRangeChanged(0, newCount)
            else -> {
                val overlap = minOf(oldCount, newCount)
                if (overlap > 0) notifyItemRangeChanged(0, overlap)
                if (newCount > oldCount) {
                    notifyItemRangeInserted(overlap, newCount - oldCount)
                } else {
                    notifyItemRangeRemoved(overlap, oldCount - newCount)
                }
            }
        }
    }

    private fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        when (val info = mapAdapterPosition(position)) {
            is PositionInfo.Header -> onBindHeaderViewHolder(holder, info.sectionIndex, payloads)
            is PositionInfo.Item -> onBindItemViewHolder(
                holder,
                info.sectionIndex,
                info.childIndex,
                payloads
            )
        }
    }

    private fun buildSectionIndex(): List<IndexedSection> =
        List(getSectionCount()) { sectionIndex ->
            IndexedSection(
                sectionIndex = sectionIndex,
                childCount = getChildCount(sectionIndex)
            )
        }

    protected abstract fun getChildCount(sectionIndex: Int): Int
    protected abstract fun getSectionCount(): Int
    protected abstract fun onBindItemViewHolder(
        holder: RecyclerView.ViewHolder,
        sectionIndex: Int,
        childIndex: Int,
        payloads: List<Any>
    )
    protected abstract fun onBindHeaderViewHolder(
        holder: RecyclerView.ViewHolder,
        sectionIndex: Int,
        payloads: List<Any>
    )
    protected abstract fun onCreateItemViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder
    protected abstract fun onCreateHeaderViewHolder(parent: ViewGroup): RecyclerView.ViewHolder
    protected open fun getItemViewType(sectionIndex: Int, childIndex: Int): Int = VIEW_TYPE_ITEM

    private sealed interface PositionInfo {
        val sectionIndex: Int

        data class Header(
            override val sectionIndex: Int
        ) : PositionInfo

        data class Item(
            override val sectionIndex: Int,
            val childIndex: Int
        ) : PositionInfo
    }

    private data class IndexedSection(
        val sectionIndex: Int,
        val childCount: Int
    )

    companion object {
        const val VIEW_TYPE_HEADER = 1
        const val VIEW_TYPE_ITEM = 2
    }
}
