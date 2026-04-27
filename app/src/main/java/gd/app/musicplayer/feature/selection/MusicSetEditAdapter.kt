package gd.app.musicplayer.feature.selection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSetEditGridBinding
import gd.app.musicplayer.databinding.ActivityMusicSetEditItemBinding
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetEditGridViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetEditListViewHolder
import kotlin.math.absoluteValue

class MusicSetEditAdapter(
    private val viewMode: Int,
    private val onToggleSelection: (MusicSet) -> Unit
) : ListAdapter<MusicSet, RecyclerView.ViewHolder>(DiffCallback()) {

    private var selectedKeys: Set<String> = emptySet()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = stableIdFor(getItem(position))

    override fun getItemViewType(position: Int): Int =
        if (viewMode == MUSIC_SET_VIEW_MODE_GRID) VIEW_TYPE_GRID else VIEW_TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == VIEW_TYPE_GRID) {
            val binding = ActivityMusicSetEditGridBinding.inflate(inflater, parent, false)
            applyCurrentTheme(binding.root)
            MusicSetEditGridViewHolder(binding, onItemClick = onToggleSelection)
        } else {
            val binding = ActivityMusicSetEditItemBinding.inflate(inflater, parent, false)
            applyCurrentTheme(binding.root)
            MusicSetEditListViewHolder(binding, onItemClick = onToggleSelection)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        val selected = musicSetKey(item) in selectedKeys

        holder as BaseViewHolder
        holder.bind(ListItem.MusicSetItem(item), selected)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val item = getItem(position)
        val selected = musicSetKey(item) in selectedKeys
        holder as BaseViewHolder
        holder.bind(ListItem.MusicSetItem(item), selected)
    }

    fun submitMusicSets(items: List<MusicSet>, selectedItems: Collection<MusicSet>) {
        selectedKeys = selectedItems.mapTo(linkedSetOf(), ::musicSetKey)
        submitList(items.toList())
    }

    fun updateSelection(selectedItems: Collection<MusicSet>) {
        val previous = selectedKeys
        selectedKeys = selectedItems.mapTo(linkedSetOf(), ::musicSetKey)
        val changed = (previous + selectedKeys) - (previous intersect selectedKeys)
        if (changed.isEmpty()) return
        currentList.forEachIndexed { index, item ->
            if (musicSetKey(item) in changed) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MusicSet>() {
        override fun areItemsTheSame(oldItem: MusicSet, newItem: MusicSet): Boolean =
            musicSetKey(oldItem) == musicSetKey(newItem)

        override fun areContentsTheSame(oldItem: MusicSet, newItem: MusicSet): Boolean =
            oldItem == newItem
    }

    private companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val PAYLOAD_SELECTION = "selection"

        fun stableIdFor(item: MusicSet): Long =
            when (item) {
                is MusicSet.Folder -> item.folderPath.hashCode().toLong()
                else -> musicSetKey(item).hashCode().absoluteValue.toLong()
            }
    }
}

internal fun musicSetKey(item: MusicSet): String =
    when (item) {
        is MusicSet.Artist -> "artist:${item.id}"
        is MusicSet.Album -> "album:${item.id}"
        is MusicSet.Genre -> "genre:${item.id}:${item.name}"
        is MusicSet.Folder -> "folder:${item.folderPath}"
        is MusicSet.Playlist -> "playlist:${item.id}"
        is MusicSet.Tracks -> "tracks:${item.id}"
        is MusicSet.Artists -> "artists:${item.id}"
        is MusicSet.Albums -> "albums:${item.id}"
        is MusicSet.Genres -> "genres:${item.id}"
        is MusicSet.Folders -> "folders:${item.id}"
        is MusicSet.Playlists -> "playlists"
        is MusicSet.RecentlyAdded -> "recently_added:${item.id}"
        is MusicSet.RecentlyPlayed -> "recently_played:${item.id}"
        is MusicSet.MostPlayed -> "most_played"
        is MusicSet.Favorites -> "favorites"
        is MusicSet.Queue -> "queue"
    }

internal const val MUSIC_SET_VIEW_MODE_LIST = 0
internal const val MUSIC_SET_VIEW_MODE_GRID = 1
