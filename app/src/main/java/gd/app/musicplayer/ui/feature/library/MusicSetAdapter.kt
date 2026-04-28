package gd.app.musicplayer.ui.feature.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applyRoundedOutline
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentAlbumGridItemBinding
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding
import gd.app.musicplayer.databinding.FragmentFolderListItemBinding
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.FolderListMusicSetViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetGridViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetListViewHolder
import gd.app.musicplayer.ui.folder.isHiddenFoldersEntry
import gd.app.musicplayer.ui.theme.applyCurrentTheme
class MusicSetAdapter(
    private val musicSetType: MusicSet,
    private var viewMode: Int = VIEW_MODE_LIST,
    private val onItemClick: ((MusicSet) -> Unit)? = null,
    private val onItemLongClick: ((MusicSet) -> Unit)? = null,
    private val onItemMenuClick: ((MusicSet, View) -> Unit)? = null
) : ListAdapter<MusicSet, BaseViewHolder>(DiffCallback()) {

    companion object {
        const val VIEW_MODE_LIST = 0
        const val VIEW_MODE_GRID = 1

        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_FOLDER = 1
        private const val VIEW_TYPE_GRID = 2
        private const val PAYLOAD_VIEW_MODE = "payload_view_mode"
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            musicSetType is MusicSet.Folders -> VIEW_TYPE_FOLDER
            viewMode == VIEW_MODE_GRID -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_FOLDER -> createFolderViewHolder(inflater, parent)
            VIEW_TYPE_GRID -> createGridViewHolder(inflater, parent)
            else -> createListViewHolder(inflater, parent)
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder,
        position: Int
    ) {
        bindHolder(
            holder = holder,
            item = getItem(position)
        )
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            bindHolder(
                holder = holder,
                item = getItem(position)
            )
        }
    }

    fun setViewMode(mode: Int) {
        if (viewMode == mode) return

        viewMode = mode

        if (itemCount > 0) {
            notifyItemRangeChanged(
                0,
                itemCount,
                PAYLOAD_VIEW_MODE
            )
        }
    }

    private fun bindHolder(
        holder: BaseViewHolder,
        item: MusicSet
    ) {
        val supportsActions = !item.isHiddenFoldersEntry()

        holder.bind(ListItem.MusicSetItem(item))

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }

        holder.itemView.setOnLongClickListener {
            if (!supportsActions || onItemLongClick == null) {
                return@setOnLongClickListener false
            }

            onItemLongClick.invoke(item)
            true
        }

        bindMenuButton(
            holder = holder,
            item = item,
            supportsActions = supportsActions
        )
    }

    private fun bindMenuButton(
        holder: BaseViewHolder,
        item: MusicSet,
        supportsActions: Boolean
    ) {
        val menuView = holder.itemView.findViewById<View?>(R.id.music_item_menu) ?: return

        menuView.isVisible = supportsActions

        menuView.setOnClickListener(
            if (supportsActions && onItemMenuClick != null) {
                View.OnClickListener {
                    onItemMenuClick.invoke(item, menuView)
                }
            } else {
                null
            }
        )
    }

    private fun createFolderViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): FolderListMusicSetViewHolder {
        val binding = FragmentFolderListItemBinding.inflate(
            inflater,
            parent,
            false
        )

        binding.root.prepareItemRoot()

        return FolderListMusicSetViewHolder(binding)
    }

    private fun createGridViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): MusicSetGridViewHolder {
        val binding = FragmentAlbumGridItemBinding.inflate(
            inflater,
            parent,
            false
        )

        binding.root.prepareItemRoot()

        return MusicSetGridViewHolder(binding)
    }

    private fun createListViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): MusicSetListViewHolder {
        val binding = FragmentAlbumListItemBinding.inflate(
            inflater,
            parent,
            false
        )

        binding.root.prepareItemRoot()

        return MusicSetListViewHolder(binding)
    }

    private fun View.prepareItemRoot() {
        applyRoundedOutline(R.dimen.item_image_corner_radius)
        applyCurrentTheme(this)
    }

    class DiffCallback : DiffUtil.ItemCallback<MusicSet>() {

        override fun areItemsTheSame(
            oldItem: MusicSet,
            newItem: MusicSet
        ): Boolean {
            return when (oldItem) {
                is MusicSet.Artist -> {
                    newItem is MusicSet.Artist && oldItem.id == newItem.id
                }

                is MusicSet.Album -> {
                    newItem is MusicSet.Album && oldItem.id == newItem.id
                }

                is MusicSet.Genre -> {
                    newItem is MusicSet.Genre && oldItem.id == newItem.id
                }

                is MusicSet.Playlist -> {
                    newItem is MusicSet.Playlist && oldItem.id == newItem.id
                }

                is MusicSet.Folder -> {
                    newItem is MusicSet.Folder &&
                            oldItem.folderPath == newItem.folderPath
                }

                else -> {
                    oldItem == newItem
                }
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