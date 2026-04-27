package gd.app.musicplayer.feature.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.applyRoundedOutline
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentAlbumGridItemBinding
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding
import gd.app.musicplayer.databinding.FragmentFolderListItemBinding
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.FolderListMusicSetViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetGridViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetListViewHolder
import gd.app.musicplayer.ui.folder.isHiddenFoldersEntry

class MusicSetAdapter(
    private val musicSetType: MusicSet,
    private var viewMode: Int = VIEW_MODE_LIST,
    private val onItemClick: ((MusicSet) -> Unit)? = null,
    private val onItemLongClick: ((MusicSet) -> Unit)? = null,
    private val onItemMenuClick: ((MusicSet, View) -> Unit)? = null
) : ListAdapter<MusicSet, BaseViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_DEFAULT = 0
        private const val VIEW_TYPE_GRID = 4
        private const val VIEW_TYPE_FOLDER = 1
        private const val PAYLOAD_VIEW_MODE = "view_mode"
        const val VIEW_MODE_LIST = 0
        const val VIEW_MODE_GRID = 1
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when(musicSetType)  {
            is MusicSet.Folders -> {
                val binding = FragmentFolderListItemBinding.inflate(inflater, parent, false)
                binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)
                applyCurrentTheme(binding.root)
                FolderListMusicSetViewHolder(binding)
            }
            else -> if (viewType == VIEW_TYPE_GRID) {
                val binding = FragmentAlbumGridItemBinding.inflate(inflater, parent, false)
                binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)
                applyCurrentTheme(binding.root)
                MusicSetGridViewHolder(binding = binding)
            } else {
                val binding = FragmentAlbumListItemBinding.inflate(inflater, parent, false)
                binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)
                applyCurrentTheme(binding.root)
                MusicSetListViewHolder(binding = binding)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (musicSetType is MusicSet.Folders)
            return VIEW_TYPE_FOLDER

        if (viewMode == VIEW_MODE_GRID) {
            return VIEW_TYPE_GRID
        }

        return VIEW_TYPE_DEFAULT
    }

    fun setViewMode(mode: Int) {
        if (viewMode == mode) return
        viewMode = mode
        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE)
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val item = getItem(position)
        val supportsMenu = !item.isHiddenFoldersEntry()
        val supportsLongClick = !item.isHiddenFoldersEntry()

        holder.bind(ListItem.MusicSetItem(item))
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
        holder.itemView.setOnLongClickListener {
            if (!supportsLongClick) {
                return@setOnLongClickListener false
            }
            onItemLongClick?.invoke(item)
            onItemLongClick != null
        }
        holder.itemView.findViewById<View?>(R.id.music_item_menu)?.let { menuView ->
            menuView.isVisible = supportsMenu
            menuView.setOnClickListener(
                if (supportsMenu) {
                    View.OnClickListener { onItemMenuClick?.invoke(item, menuView) }
                } else {
                    null
                }
            )
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        onBindViewHolder(holder, position)
    }

    class DiffCallback : DiffUtil.ItemCallback<MusicSet>() {
        override fun areItemsTheSame(oldItem: MusicSet, newItem: MusicSet): Boolean {
            return when (oldItem) {
                is MusicSet.Artist -> newItem is MusicSet.Artist && oldItem.id == newItem.id
                is MusicSet.Album -> newItem is MusicSet.Album && oldItem.id == newItem.id
                is MusicSet.Genre -> newItem is MusicSet.Genre && oldItem.id == newItem.id
                is MusicSet.Playlist -> newItem is MusicSet.Playlist && oldItem.id == newItem.id
                is MusicSet.Folder -> newItem is MusicSet.Folder && oldItem.folderPath == newItem.folderPath
                else -> oldItem == newItem
            }
        }

        override fun areContentsTheSame(
            oldItem: MusicSet,
            newItem: MusicSet
        ): Boolean {
            return oldItem == newItem
        }
    }
}
