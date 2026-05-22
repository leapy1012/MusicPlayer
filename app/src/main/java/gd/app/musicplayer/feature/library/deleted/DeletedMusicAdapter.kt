package gd.app.musicplayer.feature.library.deleted

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.isRtl
import gd.app.musicplayer.core.common.extension.isRtlLayoutSupported
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.databinding.ActivityMusicEditListItemBinding
import gd.app.musicplayer.domain.model.Music

class DeletedMusicAdapter(
    private val accentColor: Int,
    private val onSelectionCountChanged: (Int) -> Unit
) : ListAdapter<Music, DeletedMusicAdapter.ViewHolder>(DiffCallback) {

    private val selectedIds = linkedSetOf<Long>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ActivityMusicEditListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun submitList(list: List<Music>?) {
        val validIds = list.orEmpty().mapTo(hashSetOf(), Music::id)
        selectedIds.retainAll(validIds)
        super.submitList(list)
        onSelectionCountChanged(selectedIds.size)
    }

    fun getSelectedItems(): List<Music> {
        return currentList.filter { music -> music.id in selectedIds }
    }

    fun clearSelection() {
        if (selectedIds.isEmpty()) return
        val changedIds = selectedIds.toSet()
        selectedIds.clear()
        notifyChangedIds(changedIds)
        onSelectionCountChanged(0)
    }

    fun setAllSelected(selected: Boolean) {
        val changedIds = currentList.mapTo(hashSetOf(), Music::id)
        selectedIds.clear()
        if (selected) {
            selectedIds.addAll(changedIds)
        }
        notifyChangedIds(changedIds)
        onSelectionCountChanged(selectedIds.size)
    }

    fun areAllItemsSelected(): Boolean {
        return currentList.isNotEmpty() && currentList.all { music -> music.id in selectedIds }
    }

    private fun toggleSelection(music: Music) {
        if (!selectedIds.add(music.id)) {
            selectedIds.remove(music.id)
        }
        val index = currentList.indexOfFirst { item -> item.id == music.id }
        if (index >= 0) {
            notifyItemChanged(index, PAYLOAD_SELECTION)
        }
        onSelectionCountChanged(selectedIds.size)
    }

    private fun notifyChangedIds(ids: Set<Long>) {
        currentList.forEachIndexed { index, music ->
            if (music.id in ids) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    inner class ViewHolder(
        private val binding: ActivityMusicEditListItemBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        private var boundMusic: Music? = null

        init {
            binding.root.setOnClickListener(this)
            binding.musicItemDrag.visibility = View.GONE
            setupTextDirection()
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

        fun bind(music: Music) {
            boundMusic = music
            binding.musicItemAlbum.loadMusicArtwork(music.albumArtSource())
            binding.musicItemTitle.text = music.title
            binding.musicItemArtist.text = music.artist
            binding.musicItemSize.visibility = View.GONE
            bindSelection(music)
        }

        fun bindSelection(music: Music) {
            boundMusic = music
            val selected = music.id in selectedIds
            binding.musicItemMenu.isSelected = selected
            binding.musicItemMenu.setColorFilter(
                if (selected) {
                    accentColor
                } else {
                    ContextCompat.getColor(binding.root.context, R.color.item_artist_color)
                }
            )
        }

        override fun onClick(v: View?) {
            boundMusic?.let(::toggleSelection)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Music>() {
        override fun areItemsTheSame(oldItem: Music, newItem: Music): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Music, newItem: Music): Boolean =
            oldItem == newItem
    }

    private companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }
}
