package gd.app.musicplayer.feature.library.musicset

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.databinding.FragmentAlbumGridItemBinding
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding
import gd.app.musicplayer.databinding.FragmentFolderFooterBinding
import gd.app.musicplayer.databinding.FragmentFolderListItemBinding
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.feature.library.folder.isHiddenFoldersEntry
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.FolderListMusicSetViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetGridViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetListViewHolder

/**
 * Music-set list/grid adapter.
 *
 * Folders match original `l5.d` / `w7.x`: footer is a trailing view-type in the **same**
 * adapter, omitted when the folder list is empty (`w7.x.c(0) == 0`), and list updates use
 * [notifyDataSetChanged] so RecyclerView re-lays out from the top (no ConcatAdapter
 * footer-anchor scroll-to-end).
 */
class MusicSetAdapter(
    private val musicSetType: MusicSet,
    private var viewMode: Int = VIEW_MODE_LIST,
    private val onItemClick: ((MusicSet) -> Unit)? = null,
    private val onItemLongClick: ((MusicSet) -> Unit)? = null,
    private val onItemMenuClick: ((MusicSet, View) -> Unit)? = null,
    private val onFolderScanClick: (() -> Unit)? = null,
    private val applyTheme: ((View) -> Unit)? = null
) : ListAdapter<MusicSet, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        const val VIEW_MODE_LIST = 0
        const val VIEW_MODE_GRID = 1

        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_FOLDER = 1
        private const val VIEW_TYPE_GRID = 2
        private const val VIEW_TYPE_FOLDER_FOOTER = 12
    }

    private val isFolders: Boolean = musicSetType is MusicSet.Folders

    /** Folder rows only; footer is not included (original adapter data list). */
    private var folderItems: List<MusicSet> = emptyList()

    init {
        if (isFolders) {
            // Original l5.d adapter: setHasStableIds(true) with getItemId = position.
            setHasStableIds(true)
        }
    }

    fun displayedItems(): List<MusicSet> {
        return if (isFolders) folderItems else currentList
    }

    fun submitItems(items: List<MusicSet>) {
        if (isFolders) {
            folderItems = items
            notifyDataSetChanged()
            return
        }
        submitList(items)
    }

    override fun getItemCount(): Int {
        if (!isFolders) {
            return super.getItemCount()
        }
        val size = folderItems.size
        // Original w7.x.c: empty data → 0 items (no footer).
        return if (size == 0) 0 else size + 1
    }

    override fun getItemId(position: Int): Long {
        if (!isFolders) {
            return RecyclerView.NO_ID
        }
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        if (isFolders) {
            return if (isFolderFooterPosition(position)) {
                VIEW_TYPE_FOLDER_FOOTER
            } else {
                VIEW_TYPE_FOLDER
            }
        }
        return when {
            viewMode == VIEW_MODE_GRID -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_FOLDER_FOOTER -> createFolderFooterViewHolder(inflater, parent)
            VIEW_TYPE_FOLDER -> createFolderViewHolder(inflater, parent)
            VIEW_TYPE_GRID -> createGridViewHolder(inflater, parent)
            else -> createListViewHolder(inflater, parent)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder) {
            is FolderFooterViewHolder -> applyTheme?.invoke(holder.itemView)
            is BaseViewHolder -> {
                bindHolder(
                    holder = holder,
                    item = folderOrListItem(position)
                )
                applyTheme?.invoke(holder.itemView)
            }
        }
    }

    fun setViewMode(mode: Int) {
        if (viewMode == mode) return

        viewMode = mode

        notifyDataSetChanged()
    }

    private fun isFolderFooterPosition(position: Int): Boolean {
        return isFolders && folderItems.isNotEmpty() && position == folderItems.size
    }

    private fun folderOrListItem(position: Int): MusicSet {
        return if (isFolders) {
            folderItems[position]
        } else {
            getItem(position)
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
        val menuView = holder.itemView.findViewById<View?>(R.id.music_item_menu)
            ?: return

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

    private fun createFolderFooterViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): FolderFooterViewHolder {
        val binding = FragmentFolderFooterBinding.inflate(inflater, parent, false)
        applyTheme?.invoke(binding.root)
        return FolderFooterViewHolder(binding, onFolderScanClick)
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

        binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
        applyTheme?.invoke(binding.root)

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

        binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
        applyTheme?.invoke(binding.root)

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

        binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
        applyTheme?.invoke(binding.root)

        return MusicSetListViewHolder(binding)
    }

    class FolderFooterViewHolder(
        binding: FragmentFolderFooterBinding,
        onScanClick: (() -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.folderFooterScan.setOnClickListener { onScanClick?.invoke() }
        }
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
