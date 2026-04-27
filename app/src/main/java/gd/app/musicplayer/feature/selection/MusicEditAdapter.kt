package gd.app.musicplayer.feature.selection

import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.loadMusicArtwork
import gd.app.musicplayer.databinding.ActivityMusicEditListItemBinding
import gd.app.musicplayer.ui.common.view.MusicRecyclerView
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.ui.extension.highlightText
import gd.app.musicplayer.core.ui.extension.isRtl
import gd.app.musicplayer.core.ui.extension.isRtlLayoutSupported

class MusicEditAdapter(
    private val recyclerView: MusicRecyclerView,
    private val musicSet: MusicSet,
    private val dragEnabled: Boolean
) : ScrollAwareAdapter<MusicEditAdapter.MusicEditViewHolder>() {
    private companion object {
        private const val PAYLOAD_SELECTION = "payload_selection"

        private fun musicKey(music: Music): String = "${music._id}|${music.data.orEmpty()}"
    }

    interface SelectionCountChangedListener {
        fun onSelectionCountChanged(count: Int)
    }
    private var searchKeyword: String? = null
    private var allMusicItems: MutableList<Music> = mutableListOf()
    private val filteredMusicItems = mutableListOf<Music>()
    private val selectedMusicKeys = LinkedHashSet<String>()
    private var selectionCountListener: SelectionCountChangedListener? = null
    private var itemTouchHelper: ItemTouchHelper? = null

//    private val highlightColor: Int = appContainer.themeRepo.getAccentColor()

    init {
        setHasStableIds(true)
        if (dragEnabled) {
            val callback = DragSwipeCallback(null)
            callback.setLongPressDragEnabled(false)
            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }
    }

    inner class MusicEditViewHolder(
        val binding: ActivityMusicEditListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener, View.OnTouchListener {

        private var currentMusic: Music? = null

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
        fun bind(music: Music) {
            currentMusic = music
            val context = binding.root.context
            val accentColor = context.appContainer.themeRepo.getAccentColor(context)


            music.loadMusicArtwork(binding.musicItemAlbum)
            binding.musicItemTitle.text = context.highlightText(music.title, searchKeyword,accentColor, "")
            binding.musicItemArtist.text = context.highlightText(music.artist, searchKeyword,accentColor, "")
            binding.musicItemMenu.isSelected = isMusicSelected(music)
            binding.root.alpha = 1.0f

            if (dragEnabled) {
                binding.musicItemDrag.isEnabled = searchKeyword.isNullOrEmpty()
            }

//            when (infoMode) {
//                "size" -> {
//                    binding.musicItemSize.text =
//                        z6.m0.e(binding.musicItemSize.context, music.w())
//                    binding.musicItemSize.visibility = View.VISIBLE
//                }
//
//                "date" -> {
//                    binding.musicItemSize.text = z6.m0.d(music.k())
//                    binding.musicItemSize.visibility = View.VISIBLE
//                }
//
//                else -> {
//                    binding.musicItemSize.visibility = View.GONE
//                }
//            }

        }

        fun bindSelection(music: Music) {
            currentMusic = music
            binding.musicItemMenu.isSelected = isMusicSelected(music)
        }

        override fun onClick(v: View?) {
            val music = currentMusic ?: return

            val isSelected = !binding.musicItemMenu.isSelected
            binding.musicItemMenu.isSelected = isSelected

            if (isSelected) {
                selectedMusicKeys.add(musicKey(music))
            } else {
                selectedMusicKeys.remove(musicKey(music))
            }

            selectionCountListener?.onSelectionCountChanged(selectedMusicKeys.size)
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicEditViewHolder {
        val binding = ActivityMusicEditListItemBinding.inflate(LayoutInflater.from(parent.context), parent,false)
        return MusicEditViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicEditViewHolder, position: Int) {
        holder.bind(filteredMusicItems[position])
    }

    override fun onBindViewHolder(
        holder: MusicEditViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(filteredMusicItems[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = filteredMusicItems.size

    override fun getItemExtent(viewType: Int): Int = recyclerView.resources.getDimensionPixelOffset(R.dimen.item_recycler_height)


    fun submitList(items: List<Music>) {
        allMusicItems = items.toMutableList()
        removeSelectionsThatNoLongerExist()
        applyFilter(searchKeyword, dispatchDiff = true)
        selectionCountListener?.onSelectionCountChanged(selectedMusicKeys.size)
    }

    fun setSearchKeyword(keyword: String?) {
        searchKeyword = keyword
        applyFilter(keyword, dispatchDiff = true)
    }

    fun setSelectionCountListener(listener: SelectionCountChangedListener?) {
        selectionCountListener = listener
    }

    fun getSelectedItems(): Set<Music> = allMusicItems.filterTo(linkedSetOf()) { isMusicSelected(it) }

    fun getFilteredItems(): List<Music> = filteredMusicItems

    fun clearSelection() {
        if (selectedMusicKeys.isEmpty()) return
        val changedPositions = getSelectedItems()
            .mapNotNull { findFilteredIndex(it) }
        selectedMusicKeys.clear()
        notifySelectionPayload(changedPositions)
        selectionCountListener?.onSelectionCountChanged(selectedMusicKeys.size)
    }

    fun selectItem(music: Music) {
        if (!selectedMusicKeys.add(musicKey(music))) return
        findFilteredIndex(music)?.let { notifyItemChanged(it, PAYLOAD_SELECTION) }
        selectionCountListener?.onSelectionCountChanged(selectedMusicKeys.size)
    }

    fun areAllFilteredItemsSelected(): Boolean {
        if (filteredMusicItems.isEmpty()) return false
        return filteredMusicItems.all(::isMusicSelected)
    }

    fun setAllSelected(selected: Boolean) {
        if (filteredMusicItems.isEmpty()) return

        val changedPositions = mutableListOf<Int>()
        if (selected) {
            filteredMusicItems.forEachIndexed { index, item ->
                if (selectedMusicKeys.add(musicKey(item))) {
                    changedPositions.add(index)
                }
            }
        } else {
            filteredMusicItems.forEachIndexed { index, item ->
                if (selectedMusicKeys.remove(musicKey(item))) {
                    changedPositions.add(index)
                }
            }
        }

        notifySelectionPayload(changedPositions)
        selectionCountListener?.onSelectionCountChanged(selectedMusicKeys.size)
    }

    private fun applyFilter(keyword: String?, dispatchDiff: Boolean) {
        val nextFiltered = buildFilteredList(keyword)
        if (dispatchDiff) {
            dispatchFilteredDiff(nextFiltered)
        } else {
            filteredMusicItems.clear()
            filteredMusicItems.addAll(nextFiltered)
        }
    }

    private fun removeSelectionsThatNoLongerExist() {
        val validKeys = allMusicItems.mapTo(hashSetOf(), ::musicKey)
        selectedMusicKeys.retainAll(validKeys)
    }

    private fun buildFilteredList(keyword: String?): List<Music> {
        if (keyword.isNullOrEmpty()) {
            return allMusicItems.toList()
        }
        val query = keyword.lowercase()
        return allMusicItems.filter { it.title.lowercase().contains(query) }
    }

    private fun dispatchFilteredDiff(nextFiltered: List<Music>) {
        val oldFiltered = filteredMusicItems.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldFiltered.size
            override fun getNewListSize(): Int = nextFiltered.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldFiltered[oldItemPosition]
                val newItem = nextFiltered[newItemPosition]
                return oldItem._id == newItem._id && oldItem.data == newItem.data
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldFiltered[oldItemPosition]
                val newItem = nextFiltered[newItemPosition]
                return oldItem == newItem &&
                        isMusicSelected(oldItem) == isMusicSelected(newItem)
            }
        })
        filteredMusicItems.clear()
        filteredMusicItems.addAll(nextFiltered)
        diff.dispatchUpdatesTo(this)
    }

    private fun findFilteredIndex(music: Music): Int? {
        val index = filteredMusicItems.indexOfFirst {
            it._id == music._id && it.data == music.data
        }
        return if (index >= 0) index else null
    }

    private fun notifySelectionPayload(changedPositions: List<Int>) {
        changedPositions.distinct().forEach { position ->
            if (position in 0 until itemCount) {
                notifyItemChanged(position, PAYLOAD_SELECTION)
            }
        }
    }

    private fun isMusicSelected(music: Music): Boolean = musicKey(music) in selectedMusicKeys

    private fun persistSortedMusic() {
//        val copiedItems = ArrayList(allMusicItems)
//        u5.a.a(
//            Runnable {
//                u5.d.y().y0(copiedItems, musicSet.j())
//                z6.y.Y().B0(d6.j(0))
//            }
//        )
    }
}
