package gd.app.musicplayer.ui.selection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.core.common.extension.stableId
import gd.app.musicplayer.databinding.ActivityMusicSetEditGridBinding
import gd.app.musicplayer.databinding.ActivityMusicSetEditItemBinding
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetEditGridViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetEditListViewHolder
import kotlin.math.absoluteValue

internal class MusicSetEditAdapter(
    private val viewMode: Int,
    private val accentColor: Int,
    private val onToggleSelection: (MusicSet) -> Unit
) : ListAdapter<MusicSetSelectionRow, RecyclerView.ViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).stableId
    }

    override fun getItemViewType(position: Int): Int {
        return when (viewMode) {
            MUSIC_SET_VIEW_MODE_GRID -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_GRID -> {
                MusicSetEditGridViewHolder(
                    binding = ActivityMusicSetEditGridBinding.inflate(
                        inflater,
                        parent,
                        false
                    ),
                    accentColor = accentColor,
                    onItemClick = onToggleSelection
                )
            }

            else -> {
                MusicSetEditListViewHolder(
                    binding = ActivityMusicSetEditItemBinding.inflate(
                        inflater,
                        parent,
                        false
                    ),
                    accentColor = accentColor,
                    onItemClick = onToggleSelection
                )
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        bindRow(holder, getItem(position))
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            bindRow(holder, getItem(position))
            return
        }

        super.onBindViewHolder(holder, position, payloads)
    }

    fun submitMusicSets(
        items: List<MusicSet>,
        selectedKeys: Set<String>
    ) {
        submitList(
            items.map { item ->
                MusicSetSelectionRow(
                    item = item,
                    selected = item.stableId in selectedKeys
                )
            }
        )
    }

    private fun bindRow(
        holder: RecyclerView.ViewHolder,
        row: MusicSetSelectionRow
    ) {
        (holder as BaseViewHolder).bind(
            item = ListItem.MusicSetItem(row.item),
            selected = row.selected
        )
    }

    private object DiffCallback : DiffUtil.ItemCallback<MusicSetSelectionRow>() {
        override fun areItemsTheSame(
            oldItem: MusicSetSelectionRow,
            newItem: MusicSetSelectionRow
        ): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(
            oldItem: MusicSetSelectionRow,
            newItem: MusicSetSelectionRow
        ): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(
            oldItem: MusicSetSelectionRow,
            newItem: MusicSetSelectionRow
        ): Any? {
            return if (
                oldItem.item == newItem.item &&
                oldItem.selected != newItem.selected
            ) {
                PAYLOAD_SELECTION
            } else {
                null
            }
        }
    }

    private companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val PAYLOAD_SELECTION = "payload_selection"
    }
}

internal data class MusicSetSelectionRow(
    val item: MusicSet,
    val selected: Boolean
) {
    val key: String = item.stableId

    val stableId: Long =
        when (item) {
            is MusicSet.Folder -> item.folderPath.hashCode().toLong()
            else -> key.hashCode().absoluteValue.toLong()
        }
}

internal const val MUSIC_SET_VIEW_MODE_LIST = 0
internal const val MUSIC_SET_VIEW_MODE_GRID = 1