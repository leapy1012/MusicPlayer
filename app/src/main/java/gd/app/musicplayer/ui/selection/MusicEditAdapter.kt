package gd.app.musicplayer.ui.selection

import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.databinding.ActivityMusicEditListItemBinding
import gd.app.musicplayer.core.common.extension.highlightText
import gd.app.musicplayer.core.common.extension.isRtl
import gd.app.musicplayer.core.common.extension.isRtlLayoutSupported
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import java.util.Collections

class MusicEditAdapter(
    private val recyclerView: MusicRecyclerView,
    private val accentColor: Int,
    private val musicSet: MusicSet,
    private val dragEnabled: Boolean,
    private val onOrderChanged: (MusicSet, List<Music>) -> Unit
) : ScrollAwareAdapter<MusicEditAdapter.MusicEditViewHolder>(), ItemMoveListener {
    private companion object {
        private const val PAYLOAD_SELECTION = "payload_selection"

        private fun musicKey(music: Music): String = "${music.id}|${music.data.orEmpty()}"
    }

    data class RowEntry(
        val token: Long,
        val music: Music
    )

    interface SelectionCountChangedListener {
        fun onSelectionCountChanged(count: Int)
    }
    private var searchKeyword: String? = null
    private var allRows: MutableList<RowEntry> = mutableListOf()
    private val filteredRows = mutableListOf<RowEntry>()
    private val selectedRowTokens = LinkedHashSet<Long>()
    private var selectionCountListener: SelectionCountChangedListener? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var nextRowToken = 1L

    init {
        setHasStableIds(true)
        if (dragEnabled) {
            val callback = DragItemTouchHelperCallback.Builder(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            )
                .setDragEnabled(false)
                .onItemDragListener(::onItemMove)
                .onDragFinishedListener(::persistSortedMusic)
                .build()
            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }
    }

    inner class MusicEditViewHolder(
        val binding: ActivityMusicEditListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root),
        View.OnClickListener,
        View.OnTouchListener {

        private var currentMusic: Music? = null
        private var currentToken: Long? = null

        init {
            val context = binding.root.context
            if (context.isRtl() && context.isRtlLayoutSupported()) {
                binding.musicItemTitle.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                binding.musicItemTitle.textDirection = View.TEXT_DIRECTION_LOCALE
                binding.musicItemTitle.gravity = Gravity.START or Gravity.CENTER_VERTICAL

                binding.musicItemArtist.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                binding.musicItemArtist.textDirection = View.TEXT_DIRECTION_LOCALE
                binding.musicItemArtist.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            binding.root.setOnClickListener(this)
            if (dragEnabled) {
                binding.musicItemDrag.visibility = View.VISIBLE
                binding.musicItemDrag.setOnTouchListener (this)
            } else {
                binding.musicItemDrag.visibility = View.GONE
            }
        }
        override fun onClick(v: View?) {
            val token = currentToken ?: return
            toggleRowSelection(token)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN || !searchKeyword.isNullOrEmpty()) {
                return false
            }

            val animator = recyclerView.itemAnimator
            if (animator != null && animator.isRunning) {
                return true
            }

            itemTouchHelper?.startDrag(this)
            return true
        }
        fun bind(row: RowEntry) {
            currentToken = row.token
            bindMusic(row.music)
        }

        fun bindSelection(row: RowEntry) {
            currentToken = row.token
            currentMusic = row.music
            renderSelectionState(isRowSelected(row.token))
        }

        private fun bindMusic(music: Music) {
            currentMusic = music
            val context = binding.root.context

            binding.musicItemAlbum.loadMusicArtwork(music.albumArtSource())
            binding.musicItemTitle.text = context.highlightText(music.title, searchKeyword, accentColor, "")
            binding.musicItemArtist.text = context.highlightText(music.artist, searchKeyword, accentColor, "")
            renderSelectionState(currentToken?.let(::isRowSelected) == true)
            binding.root.alpha = 1.0f

            if (dragEnabled) {
                binding.musicItemDrag.isEnabled = searchKeyword.isNullOrEmpty()
            }
        }

        private fun renderSelectionState(selected: Boolean) {
            val context = binding.root.context
            binding.musicItemMenu.isSelected = selected
            val tint = if (selected) {
                accentColor
            } else {
                ContextCompat.getColor(context, R.color.item_artist_color)
            }
            binding.musicItemMenu.setColorFilter(tint)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicEditViewHolder {
        val binding = ActivityMusicEditListItemBinding.inflate(LayoutInflater.from(parent.context), parent,false)
        return MusicEditViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicEditViewHolder, position: Int) =
        holder.bind(filteredRows[position])

    override fun onBindViewHolder(
        holder: MusicEditViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(filteredRows[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = filteredRows.size

    override fun getItemId(position: Int): Long {
        return filteredRows.getOrNull(position)?.token ?: RecyclerView.NO_ID
    }

    override fun getItemExtent(viewType: Int): Int = recyclerView.resources.getDimensionPixelOffset(R.dimen.item_recycler_height)

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (
            fromPosition !in filteredRows.indices ||
            toPosition !in filteredRows.indices ||
            !searchKeyword.isNullOrEmpty()
        ) {
            return
        }

        Collections.swap(filteredRows, fromPosition, toPosition)
        Collections.swap(allRows, fromPosition, toPosition)
    }


    fun submitList(items: List<Music>) {
        val previouslySelectedRows = allRows.filter { it.token in selectedRowTokens }
        allRows = items.map { music ->
            RowEntry(
                token = nextRowToken++,
                music = music
            )
        }.toMutableList()
        restoreSelection(previouslySelectedRows)
        applyFilter(searchKeyword, dispatchDiff = true)
        selectionCountListener?.onSelectionCountChanged(selectedRowTokens.size)
    }

    fun setSearchKeyword(keyword: String?) {
        searchKeyword = keyword
        applyFilter(keyword, dispatchDiff = true)
    }

    fun setSelectionCountListener(listener: SelectionCountChangedListener?) {
        selectionCountListener = listener
    }

    fun getSelectedItems(): List<Music> = allRows
        .filter { it.token in selectedRowTokens }
        .map { it.music }

    fun getFilteredItems(): List<Music> = filteredRows.map { it.music }

    fun clearSelection() {
        if (selectedRowTokens.isEmpty()) return
        val changedPositions = filteredRows.mapIndexedNotNull { index, row ->
            index.takeIf { row.token in selectedRowTokens }
        }
        selectedRowTokens.clear()
        notifySelectionPayload(changedPositions)
        selectionCountListener?.onSelectionCountChanged(selectedRowTokens.size)
    }

    fun selectItem(music: Music) {
        val row = allRows.firstOrNull { it.music.id == music.id && it.music.data == music.data } ?: return
        if (!setRowSelection(row.token, selected = true)) return
        findFilteredIndex(row.token)?.let { notifyItemChanged(it, PAYLOAD_SELECTION) }
        selectionCountListener?.onSelectionCountChanged(selectedRowTokens.size)
    }

    fun areAllFilteredItemsSelected(): Boolean {
        if (filteredRows.isEmpty()) return false
        return filteredRows.all { it.token in selectedRowTokens }
    }

    fun setAllSelected(selected: Boolean) {
        if (filteredRows.isEmpty()) return

        val changedPositions = mutableListOf<Int>()
        if (selected) {
            filteredRows.forEachIndexed { index, row ->
                if (selectedRowTokens.add(row.token)) {
                    changedPositions.add(index)
                }
            }
        } else {
            filteredRows.forEachIndexed { index, row ->
                if (selectedRowTokens.remove(row.token)) {
                    changedPositions.add(index)
                }
            }
        }

        notifySelectionPayload(changedPositions)
        selectionCountListener?.onSelectionCountChanged(selectedRowTokens.size)
    }

    private fun applyFilter(keyword: String?, dispatchDiff: Boolean) {
        val nextFiltered = buildFilteredList(keyword)
        if (dispatchDiff) {
            dispatchFilteredDiff(nextFiltered)
        } else {
            filteredRows.clear()
            filteredRows.addAll(nextFiltered)
        }
    }

    private fun buildFilteredList(keyword: String?): List<RowEntry> {
        if (keyword.isNullOrEmpty()) {
            return allRows.toList()
        }
        val query = keyword.lowercase()
        return allRows.filter { it.music.title.lowercase().contains(query) }
    }

    private fun dispatchFilteredDiff(nextFiltered: List<RowEntry>) {
        val oldFiltered = filteredRows.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldFiltered.size
            override fun getNewListSize(): Int = nextFiltered.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldFiltered[oldItemPosition]
                val newItem = nextFiltered[newItemPosition]
                return oldItem.token == newItem.token
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldFiltered[oldItemPosition]
                val newItem = nextFiltered[newItemPosition]
                return oldItem.music == newItem.music &&
                    isRowSelected(oldItem.token) == isRowSelected(newItem.token)
            }
        })
        filteredRows.clear()
        filteredRows.addAll(nextFiltered)
        diff.dispatchUpdatesTo(this)
    }

    private fun findFilteredIndex(token: Long): Int? {
        val index = filteredRows.indexOfFirst { it.token == token }
        return if (index >= 0) index else null
    }

    private fun notifySelectionPayload(changedPositions: List<Int>) {
        changedPositions.distinct().forEach { position ->
            if (position in 0 until itemCount) {
                notifyItemChanged(position, PAYLOAD_SELECTION)
            }
        }
    }

    private fun isRowSelected(token: Long): Boolean = token in selectedRowTokens

    private fun toggleRowSelection(token: Long) {
        val changed = setRowSelection(token, selected = !isRowSelected(token))
        if (!changed) return
        findFilteredIndex(token)?.let { notifyItemChanged(it, PAYLOAD_SELECTION) }
        selectionCountListener?.onSelectionCountChanged(selectedRowTokens.size)
    }

    private fun setRowSelection(token: Long, selected: Boolean): Boolean {
        return if (selected) {
            selectedRowTokens.add(token)
        } else {
            selectedRowTokens.remove(token)
        }
    }

    private fun restoreSelection(previouslySelectedRows: List<RowEntry>) {
        selectedRowTokens.clear()
        if (previouslySelectedRows.isEmpty()) return
        val matched = BooleanArray(allRows.size)
        previouslySelectedRows.forEach { selectedRow ->
            val key = musicKey(selectedRow.music)
            val matchedIndex = allRows.indices.firstOrNull { index ->
                !matched[index] && musicKey(allRows[index].music) == key
            } ?: -1
            if (matchedIndex >= 0) {
                matched[matchedIndex] = true
                selectedRowTokens.add(allRows[matchedIndex].token)
            }
        }
    }

    private fun persistSortedMusic() {
        onOrderChanged(musicSet, allRows.map { it.music })
    }
}
