package gd.app.musicplayer.ui.selection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.common.viewholder.MusicSelectViewHolder

class MusicSelectAdapter(
    private val accentColor: Int,
    private val onSelectionToggle: (Music) -> Unit
) : ListAdapter<MusicSelectAdapter.SelectableMusicItem, MusicSelectViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MusicSelectViewHolder {
        val binding = ActivityMusicSelectItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return MusicSelectViewHolder(
            binding = binding, onSelectionToggle
        )
    }

    override fun onBindViewHolder(
        holder: MusicSelectViewHolder,
        position: Int
    ) {
        holder.bindItem(getItem(position))
    }

    override fun onBindViewHolder(
        holder: MusicSelectViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        holder.bindItem(getItem(position))
    }

    fun submitMusicList(
        items: List<Music>,
        selectedIds: Set<Long>,
        lockedIds: Set<Long>,
        highlightQuery: String
    ) {
        val models = items.map { music ->
            val locked = music.id in lockedIds

            SelectableMusicItem(
                music = music,
                selected = locked || music.id in selectedIds,
                locked = locked,
                highlightQuery = highlightQuery,
                accentColor = accentColor
            )
        }

        submitList(models)
    }

    private fun MusicSelectViewHolder.bindItem(item: SelectableMusicItem) {
        bind(
            music = item.music,
            selected = item.selected,
            locked = item.locked,
            highlightQuery = item.highlightQuery,
            accentColor = item.accentColor
        )
    }

    data class SelectableMusicItem(
        val music: Music,
        val selected: Boolean,
        val locked: Boolean,
        val highlightQuery: String,
        val accentColor: Int
    )

    private object DiffCallback : DiffUtil.ItemCallback<SelectableMusicItem>() {
        override fun areItemsTheSame(
            oldItem: SelectableMusicItem,
            newItem: SelectableMusicItem
        ): Boolean {
            return oldItem.music.id == newItem.music.id
        }

        override fun areContentsTheSame(
            oldItem: SelectableMusicItem,
            newItem: SelectableMusicItem
        ): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(
            oldItem: SelectableMusicItem,
            newItem: SelectableMusicItem
        ): Any? {
            return if (
                oldItem.selected != newItem.selected ||
                oldItem.locked != newItem.locked ||
                oldItem.highlightQuery != newItem.highlightQuery ||
                oldItem.accentColor != newItem.accentColor
            ) {
                PAYLOAD_SELECTION_STATE
            } else {
                null
            }
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION_STATE = "selection_state"
    }
}