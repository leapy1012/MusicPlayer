package gd.app.musicplayer.ui.selection

import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.highlight
import gd.app.musicplayer.core.common.extension.isRtl
import gd.app.musicplayer.core.common.extension.isRtlLayoutSupported
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.databinding.ActivityMusicEditListItemBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import java.util.Collections

class MusicEditAdapter(
    private val recyclerView: MusicRecyclerView,
    private val accentColor: Int,
    private val musicSet: MusicSet,
    private val dragEnabled: Boolean,
    private val onOrderChanged: (MusicSet, List<Music>) -> Unit
) : ScrollAwareAdapter<MusicEditAdapter.MusicEditViewHolder>(),
    ItemMoveListener {

    data class RowEntry(
        val key: String,
        val music: Music
    )

    interface SelectionCountChangedListener {
        fun onSelectionCountChanged(count: Int)
    }

    private var rawSearchQuery: String = ""
    private var normalizedSearchQuery: String = ""

    private var allRows: MutableList<RowEntry> = mutableListOf()
    private val filteredRows = mutableListOf<RowEntry>()
    private val selectedKeys = LinkedHashSet<String>()

    private var selectionCountListener: SelectionCountChangedListener? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    init {
        setHasStableIds(true)

        if (dragEnabled) {
            val callback = DragItemTouchHelperCallback.Builder(
                dragDirs = ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                swipeDirs = 0
            )
                .setDragEnabled(false)
                .dragEligibilityChecker { position ->
                    normalizedSearchQuery.isEmpty() && position in filteredRows.indices
                }
                .onItemDragListener(::onItemMove)
                .onDragFinishedListener(::persistSortedMusic)
                .build()

            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }
    }

    inner class MusicEditViewHolder(
        private val binding: ActivityMusicEditListItemBinding
    ) : RecyclerView.ViewHolder(binding.root),
        View.OnClickListener,
        View.OnTouchListener {

        private var currentRow: RowEntry? = null

        init {
            setupTextDirection()
            setupInteractions()
        }

        private fun setupTextDirection() {
            val context = binding.root.context

            if (!context.isRtl() || !context.isRtlLayoutSupported()) return

            binding.musicItemTitle.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            binding.musicItemTitle.textDirection = View.TEXT_DIRECTION_LOCALE
            binding.musicItemTitle.gravity = Gravity.START or Gravity.CENTER_VERTICAL

            binding.musicItemArtist.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            binding.musicItemArtist.textDirection = View.TEXT_DIRECTION_LOCALE
            binding.musicItemArtist.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        private fun setupInteractions() {
            binding.root.setOnClickListener(this)

            if (dragEnabled) {
                binding.musicItemDrag.visibility = View.VISIBLE
                binding.musicItemDrag.setOnTouchListener(this)
            } else {
                binding.musicItemDrag.visibility = View.GONE
                binding.musicItemDrag.setOnTouchListener(null)
            }
        }

        override fun onClick(v: View?) {
            val row = currentRow ?: return
            toggleRowSelection(row.key)
        }

        override fun onTouch(
            v: View,
            event: MotionEvent
        ): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
            if (!dragEnabled) return false
            if (normalizedSearchQuery.isNotEmpty()) return false
            if (bindingAdapterPosition == RecyclerView.NO_POSITION) return false

            itemTouchHelper?.startDrag(this)
            return true
        }

        fun bind(row: RowEntry) {
            currentRow = row

            val context = binding.root.context
            val music = row.music

            binding.musicItemAlbum.loadMusicArtwork(music.albumArtSource())

            binding.musicItemTitle.text = music.title.highlight(rawSearchQuery, accentColor)
            binding.musicItemArtist.text = music.artist.highlight(rawSearchQuery, accentColor)

            binding.root.alpha = DragItemTouchHelperCallback.ALPHA_FULL

            if (dragEnabled) {
                binding.musicItemDrag.isEnabled = normalizedSearchQuery.isEmpty()
            }

            renderSelectionState(isRowSelected(row.key))
        }

        fun bindSelection(row: RowEntry) {
            currentRow = row
            renderSelectionState(isRowSelected(row.key))
        }

        private fun renderSelectionState(selected: Boolean) {
            val context = binding.root.context

            binding.musicItemMenu.isSelected = selected

            val tint = if (selected) {
                accentColor
            } else {
                ContextCompat.getColor(
                    context,
                    R.color.item_artist_color
                )
            }

            binding.musicItemMenu.setColorFilter(tint)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MusicEditViewHolder {
        val binding = ActivityMusicEditListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)

        return MusicEditViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MusicEditViewHolder,
        position: Int
    ) {
        holder.bind(filteredRows[position])
    }

    override fun onBindViewHolder(
        holder: MusicEditViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(filteredRows[position])
            return
        }

        super.onBindViewHolder(
            holder,
            position,
            payloads
        )
    }

    override fun getItemCount(): Int {
        return filteredRows.size
    }

    override fun getItemId(position: Int): Long {
        return filteredRows.getOrNull(position)
            ?.key
            ?.let(::stableLongId)
            ?: RecyclerView.NO_ID
    }

    override fun getItemExtent(viewType: Int): Int {
        return recyclerView.resources.getDimensionPixelOffset(
            R.dimen.item_recycler_height
        )
    }

    override fun onItemMove(
        fromPosition: Int,
        toPosition: Int
    ) {
        if (fromPosition !in filteredRows.indices) return
        if (toPosition !in filteredRows.indices) return
        if (normalizedSearchQuery.isNotEmpty()) return
        if (fromPosition == toPosition) return

        val movedRow = filteredRows[fromPosition]
        val targetRow = filteredRows[toPosition]

        Collections.swap(
            filteredRows,
            fromPosition,
            toPosition
        )

        val movedAllIndex = allRows.indexOfFirst { row ->
            row.key == movedRow.key
        }

        val targetAllIndex = allRows.indexOfFirst { row ->
            row.key == targetRow.key
        }

        if (movedAllIndex >= 0 && targetAllIndex >= 0) {
            Collections.swap(
                allRows,
                movedAllIndex,
                targetAllIndex
            )
        }

        notifyItemMoved(
            fromPosition,
            toPosition
        )
    }

    fun submitList(items: List<Music>) {
        val nextRows = items.map { music ->
            RowEntry(
                key = musicKey(music),
                music = music
            )
        }

        val validKeys = nextRows.mapTo(HashSet()) { row ->
            row.key
        }

        selectedKeys.retainAll(validKeys)
        allRows = nextRows.toMutableList()

        applyFilter(
            keyword = normalizedSearchQuery,
            dispatchDiff = true
        )

        selectionCountListener?.onSelectionCountChanged(selectedKeys.size)
    }

    fun setSearchKeyword(keyword: String?) {
        rawSearchQuery = keyword.orEmpty().trim()
        normalizedSearchQuery = rawSearchQuery.lowercase()

        applyFilter(
            keyword = normalizedSearchQuery,
            dispatchDiff = true
        )
    }

    fun setSelectionCountListener(listener: SelectionCountChangedListener?) {
        selectionCountListener = listener
    }

    fun getSelectedItems(): List<Music> {
        return allRows
            .filter { row -> row.key in selectedKeys }
            .map { row -> row.music }
    }

    fun getFilteredItems(): List<Music> {
        return filteredRows.map { row ->
            row.music
        }
    }

    fun clearSelection() {
        if (selectedKeys.isEmpty()) return

        val changedPositions = filteredRows.mapIndexedNotNull { index, row ->
            index.takeIf { row.key in selectedKeys }
        }

        selectedKeys.clear()

        notifySelectionPayload(changedPositions)
        selectionCountListener?.onSelectionCountChanged(selectedKeys.size)
    }

    fun selectItem(music: Music) {
        val key = musicKey(music)

        val row = allRows.firstOrNull { candidate ->
            candidate.key == key
        } ?: return

        if (!setRowSelection(row.key, selected = true)) return

        findFilteredIndex(row.key)?.let { index ->
            notifyItemChanged(
                index,
                PAYLOAD_SELECTION
            )
        }

        selectionCountListener?.onSelectionCountChanged(selectedKeys.size)
    }

    fun areAllFilteredItemsSelected(): Boolean {
        if (filteredRows.isEmpty()) return false

        return filteredRows.all { row ->
            row.key in selectedKeys
        }
    }

    fun setAllSelected(selected: Boolean) {
        if (filteredRows.isEmpty()) return

        val changedPositions = mutableListOf<Int>()

        filteredRows.forEachIndexed { index, row ->
            val changed = if (selected) {
                selectedKeys.add(row.key)
            } else {
                selectedKeys.remove(row.key)
            }

            if (changed) {
                changedPositions.add(index)
            }
        }

        notifySelectionPayload(changedPositions)
        selectionCountListener?.onSelectionCountChanged(selectedKeys.size)
    }

    private fun applyFilter(
        keyword: String?,
        dispatchDiff: Boolean
    ) {
        val nextFiltered = buildFilteredRows(keyword)

        if (dispatchDiff) {
            dispatchFilteredDiff(nextFiltered)
            return
        }

        filteredRows.clear()
        filteredRows.addAll(nextFiltered)
    }

    private fun buildFilteredRows(keyword: String?): List<RowEntry> {
        if (keyword.isNullOrEmpty()) {
            return allRows.toList()
        }

        return allRows.filter { row ->
            row.music.title.contains(keyword, ignoreCase = true) ||
                    row.music.artist.contains(keyword, ignoreCase = true)
        }
    }

    private fun dispatchFilteredDiff(nextFiltered: List<RowEntry>) {
        val oldFiltered = filteredRows.toList()

        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int {
                    return oldFiltered.size
                }

                override fun getNewListSize(): Int {
                    return nextFiltered.size
                }

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    return oldFiltered[oldItemPosition].key ==
                            nextFiltered[newItemPosition].key
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    val oldItem = oldFiltered[oldItemPosition]
                    val newItem = nextFiltered[newItemPosition]

                    return oldItem.music == newItem.music &&
                            isRowSelected(oldItem.key) == isRowSelected(newItem.key)
                }

                override fun getChangePayload(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Any? {
                    val oldItem = oldFiltered[oldItemPosition]
                    val newItem = nextFiltered[newItemPosition]

                    val sameMusic = oldItem.music == newItem.music
                    val selectionChanged =
                        isRowSelected(oldItem.key) != isRowSelected(newItem.key)

                    return if (sameMusic && selectionChanged) {
                        PAYLOAD_SELECTION
                    } else {
                        null
                    }
                }
            }
        )

        filteredRows.clear()
        filteredRows.addAll(nextFiltered)

        diff.dispatchUpdatesTo(this)
    }

    private fun findFilteredIndex(key: String): Int? {
        val index = filteredRows.indexOfFirst { row ->
            row.key == key
        }

        return index.takeIf { it >= 0 }
    }

    private fun notifySelectionPayload(changedPositions: List<Int>) {
        changedPositions.distinct().forEach { position ->
            if (position in 0 until itemCount) {
                notifyItemChanged(
                    position,
                    PAYLOAD_SELECTION
                )
            }
        }
    }

    private fun isRowSelected(key: String): Boolean {
        return key in selectedKeys
    }

    private fun toggleRowSelection(key: String) {
        val changed = setRowSelection(
            key = key,
            selected = !isRowSelected(key)
        )

        if (!changed) return

        findFilteredIndex(key)?.let { index ->
            notifyItemChanged(
                index,
                PAYLOAD_SELECTION
            )
        }

        selectionCountListener?.onSelectionCountChanged(selectedKeys.size)
    }

    private fun setRowSelection(
        key: String,
        selected: Boolean
    ): Boolean {
        return if (selected) {
            selectedKeys.add(key)
        } else {
            selectedKeys.remove(key)
        }
    }

    private fun persistSortedMusic() {
        if (!dragEnabled || normalizedSearchQuery.isNotEmpty()) return

        onOrderChanged(
            musicSet,
            allRows.map { row ->
                row.music
            }
        )
    }

    private companion object {
        private const val PAYLOAD_SELECTION = "payload_selection"

        private fun musicKey(music: Music): String {
            return "${music.id}|${music.data.orEmpty()}"
        }

        private fun stableLongId(key: String): Long {
            var result = 1125899906842597L

            key.forEach { char ->
                result = 31 * result + char.code
            }

            return if (result == RecyclerView.NO_ID) {
                result + 1L
            } else {
                result
            }
        }
    }
}