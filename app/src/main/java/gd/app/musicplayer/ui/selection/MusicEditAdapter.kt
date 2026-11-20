package gd.app.musicplayer.ui.selection

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.checkbox.COUICheckBox
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.formatAddedDate
import gd.app.musicplayer.core.common.extension.formatFileSize
import gd.app.musicplayer.core.common.extension.highlight
import gd.app.musicplayer.core.common.extension.installCouiPressFeedback
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

    /** Sort style of [musicSet]; "size" / "date" drive the music_item_size column (original o5.j0). */
    var metadataSortStyle: String = ""
        set(value) {
            if (field == value) return
            field = value
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_METADATA)
            }
        }

    private var currentTrackId: Long? = null
    private var isCurrentTrackPlaying: Boolean = false

    private val persistHandler = Handler(Looper.getMainLooper())
    private var persistPending = false
    private val persistRunnable = Runnable { persistSortedMusic() }

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
                .onDragFinishedListener(::schedulePersistSortedMusic)
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

        private val defaultTitleColors: ColorStateList = binding.musicItemTitle.textColors
        private val defaultArtistColors: ColorStateList = binding.musicItemArtist.textColors
        private val defaultSizeColors: ColorStateList = binding.musicItemSize.textColors

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

            val music = row.music

            binding.musicItemAlbum.loadMusicArtwork(music.albumArtSource())

            binding.musicItemTitle.text = music.title.highlight(rawSearchQuery, accentColor)
            binding.musicItemArtist.text = music.artist.highlight(rawSearchQuery, accentColor)

            bindMetadata(music)
            renderPlaybackHighlight(music)

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

        fun bindMetadataAndPlayback(row: RowEntry) {
            currentRow = row
            bindMetadata(row.music)
            renderPlaybackHighlight(row.music)
        }

        fun bindPlayback(row: RowEntry) {
            currentRow = row
            renderPlaybackHighlight(row.music)
        }

        private fun bindMetadata(music: Music) {
            val text = when (metadataSortStyle) {
                SORT_STYLE_SIZE -> music.formatFileSize(binding.root.context)
                SORT_STYLE_DATE -> music.formatAddedDate()
                else -> ""
            }
            binding.musicItemSize.text = text
            binding.musicItemSize.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }

        /** Original o5.j0: accent tint on title/artist/size only while the current track is playing. */
        private fun renderPlaybackHighlight(music: Music) {
            val highlighted = isCurrentTrackPlaying && currentTrackId == music.id
            if (highlighted) {
                binding.musicItemTitle.setTextColor(accentColor)
                binding.musicItemArtist.setTextColor(accentColor)
                binding.musicItemSize.setTextColor(accentColor)
            } else {
                binding.musicItemTitle.setTextColor(defaultTitleColors)
                binding.musicItemArtist.setTextColor(defaultArtistColors)
                binding.musicItemSize.setTextColor(defaultSizeColors)
            }
        }

        private fun renderSelectionState(selected: Boolean) {
            binding.musicItemCheckbox.setState(
                if (selected) COUICheckBox.SELECT_ALL else COUICheckBox.SELECT_NONE
            )
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
        binding.root.installCouiPressFeedback()

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

        if (payloads.contains(PAYLOAD_METADATA)) {
            holder.bindMetadataAndPlayback(filteredRows[position])
            return
        }

        if (payloads.contains(PAYLOAD_PLAYBACK)) {
            holder.bindPlayback(filteredRows[position])
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

        schedulePersistSortedMusic()
    }

    /** Mirrors TrackAdapter.submitPlaybackHighlight; tint applies to the current track while playing. */
    fun submitPlaybackHighlight(
        currentTrackId: Long?,
        isPlaying: Boolean
    ) {
        if (this.currentTrackId == currentTrackId && isCurrentTrackPlaying == isPlaying) return

        val previousId = this.currentTrackId
        this.currentTrackId = currentTrackId
        this.isCurrentTrackPlaying = isPlaying

        filteredRows.forEachIndexed { index, row ->
            if (row.music.id == previousId || row.music.id == currentTrackId) {
                notifyItemChanged(index, PAYLOAD_PLAYBACK)
            }
        }
    }

    /** Flush a pending debounced reorder immediately (call from onStop so it is not lost). */
    fun flushPendingPersist() {
        if (!persistPending) return
        persistHandler.removeCallbacks(persistRunnable)
        persistSortedMusic()
    }

    fun cancelPendingPersist() {
        persistPending = false
        persistHandler.removeCallbacks(persistRunnable)
    }

    private fun schedulePersistSortedMusic() {
        if (!dragEnabled || normalizedSearchQuery.isNotEmpty()) return
        persistPending = true
        persistHandler.removeCallbacks(persistRunnable)
        persistHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS)
    }

    /**
     * Full list replace matching original o5.j0.H:
     * assign rows, optional preselect (j0.y), rebuild filter (j0.z), notifyDataSetChanged.
     */
    fun replaceAll(
        items: List<Music>,
        preselected: Music? = null
    ) {
        val nextRows = items.map { music ->
            RowEntry(
                key = musicKey(music),
                music = music
            )
        }

        val validKeys = nextRows.mapTo(HashSet(), RowEntry::key)
        selectedKeys.retainAll(validKeys)
        preselected?.let { music ->
            selectedKeys.add(musicKey(music))
        }

        allRows = nextRows.toMutableList()
        rebuildFilteredRows()
        notifyDataSetChanged()
        selectionCountListener?.onSelectionCountChanged(selectedKeys.size)
    }

    fun setSearchKeyword(keyword: String?) {
        rawSearchQuery = keyword.orEmpty().trim()
        normalizedSearchQuery = rawSearchQuery.lowercase()
        // Original j0.z + notifyDataSetChanged on text change.
        rebuildFilteredRows()
        notifyDataSetChanged()
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

    private fun rebuildFilteredRows() {
        filteredRows.clear()
        if (normalizedSearchQuery.isEmpty()) {
            filteredRows.addAll(allRows)
            return
        }
        allRows.forEach { row ->
            // Original o5.j0 filters on title only.
            if (row.music.title.contains(normalizedSearchQuery, ignoreCase = true)) {
                filteredRows += row
            }
        }
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
        persistPending = false
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
        private const val PAYLOAD_METADATA = "payload_metadata"
        private const val PAYLOAD_PLAYBACK = "payload_playback"
        private const val SORT_STYLE_SIZE = "size"
        private const val SORT_STYLE_DATE = "date"
        private const val PERSIST_DEBOUNCE_MS = 1500L

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
