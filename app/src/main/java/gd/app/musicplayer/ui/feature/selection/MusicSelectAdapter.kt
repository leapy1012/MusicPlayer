package gd.app.musicplayer.ui.feature.selection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.ui.common.viewholder.MusicSelectViewHolder

class MusicSelectAdapter(
    private val accentColor: Int,
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
        return MusicSelectViewHolder(binding, onSelectionToggle)
    }

    override fun onBindViewHolder(holder: MusicSelectViewHolder, position: Int) {
        bindHolder(holder, getItem(position))
    }

    fun submitMusicList(
        items: List<Music>,
        selectedIds: Set<Long>,
        lockedIds: Set<Long>,
        highlightQuery: String
    ) {
        this.selectedIds = selectedIds
        this.lockedIds = lockedIds
        this.highlightQuery = highlightQuery
        submitList(items.toList())
    }

    fun areAllSelectableVisibleSongsSelected(): Boolean {
        val selectableVisibleIds = currentList
            .asSequence()
            .map(Music::id)
            .filterNot { it in lockedIds }
            .toSet()
        return selectableVisibleIds.isNotEmpty() && selectedIds.containsAll(selectableVisibleIds)
    }

    private fun bindHolder(holder: MusicSelectViewHolder, music: Music) {
        val locked = music.id in lockedIds
        holder.bind(
            music = music,
            selected = locked || (music.id in selectedIds),
            locked = locked,
            highlightQuery = highlightQuery,
            accentColor = accentColor
        )
    }

    private class DiffCallback : DiffUtil.ItemCallback<Music>() {
        override fun areItemsTheSame(oldItem: Music, newItem: Music): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Music, newItem: Music): Boolean = oldItem == newItem
    }
}
