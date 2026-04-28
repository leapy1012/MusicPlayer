package gd.app.musicplayer.ui.feature.selection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import gd.app.musicplayer.ui.common.viewholder.MusicSelectViewHolder

class MusicSelectAdapter(
    private val onSelectionToggle: (Music) -> Unit
) : ListAdapter<Music, MusicSelectViewHolder>(DiffCallback()) {

    private var selectedIds: Set<Long> = emptySet()
    private var lockedIds: Set<Long> = emptySet()
    private var highlightQuery: String = ""

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicSelectViewHolder {
        val binding = ActivityMusicSelectItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyCurrentTheme(binding.root)
        return MusicSelectViewHolder(binding, onSelectionToggle)
    }

    override fun onBindViewHolder(holder: MusicSelectViewHolder, position: Int) {
        bindHolder(holder, getItem(position))
    }

    override fun onBindViewHolder(
        holder: MusicSelectViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        bindHolder(holder, getItem(position))
    }

    fun submitMusicList(
        items: List<Music>,
        selectedIds: Set<Long>,
        lockedIds: Set<Long>,
        highlightQuery: String
    ) {
        val queryChanged = this.highlightQuery != highlightQuery
        val lockedChanged = this.lockedIds != lockedIds
        this.selectedIds = selectedIds.toSet()
        this.lockedIds = lockedIds.toSet()
        this.highlightQuery = highlightQuery
        submitList(items.toList()) {
            if ((queryChanged || lockedChanged) && currentList.isNotEmpty()) {
                notifyItemRangeChanged(0, currentList.size, PAYLOAD_HIGHLIGHT)
            }
        }
    }

    fun updateSelection(selectedIds: Set<Long>) {
        val previousIds = this.selectedIds
        this.selectedIds = selectedIds.toSet()
        val changedIds = (previousIds + this.selectedIds) - (previousIds intersect this.selectedIds)
        if (changedIds.isEmpty()) return
        currentList.forEachIndexed { index, music ->
            if (music.id in changedIds) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    fun toggleSelectAllVisible(selectAll: Boolean) {
        if (currentList.isEmpty()) return
        val selectableVisibleIds = currentList
            .asSequence()
            .map { it.id }
            .filterNot { it in lockedIds }
            .toSet()
        if (selectableVisibleIds.isEmpty()) return

        selectedIds = if (selectAll) {
            selectedIds + selectableVisibleIds
        } else {
            selectedIds - selectableVisibleIds
        }
        notifyItemRangeChanged(0, currentList.size, PAYLOAD_SELECTION)
    }

    fun areAllSelectableVisibleSongsSelected(): Boolean {
        val selectableVisibleIds = currentList
            .asSequence()
            .map { it.id }
            .filterNot { it in lockedIds }
            .toSet()
        return selectableVisibleIds.isNotEmpty() && selectedIds.containsAll(selectableVisibleIds)
    }

    private fun bindHolder(holder: MusicSelectViewHolder, music: Music) {
        val locked = lockedIds.contains(music.id)
        holder.bind(
            music = music,
            selected = selectedIds.contains(music.id) || locked,
            locked = locked,
            highlightQuery = highlightQuery
        )
    }

    class DiffCallback : DiffUtil.ItemCallback<Music>() {
        override fun areItemsTheSame(oldItem: Music, newItem: Music): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Music, newItem: Music): Boolean =
            oldItem == newItem
    }

    private companion object {
        private const val PAYLOAD_SELECTION = "selection"
        private const val PAYLOAD_HIGHLIGHT = "highlight"
    }
}
