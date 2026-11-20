package gd.app.musicplayer.feature.library.directory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.databinding.FragmentFolderListItemBinding
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.viewholder.MusicViewHolder

/**
 * Port of `o5.f0`: folder rows (view type 0) and track rows (view type 1) in one adapter, with the
 * track row carrying the playing-state indicator.
 */
class MusicDirectoryAdapter(
    private val theme: ThemePalette,
    private val onEntryClick: (DirectoryEntry) -> Unit,
    private val onTrackMenuClick: (DirectoryTrack) -> Unit,
    private val applyTheme: ((View) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var entries: List<DirectoryEntry> = emptyList()
    private var currentTrackId: Long = NO_TRACK_ID
    private var isCurrentTrackPlaying: Boolean = false

    override fun getItemCount(): Int = entries.size

    override fun getItemViewType(position: Int): Int {
        return if (entries[position] is DirectoryNode) VIEW_TYPE_FOLDER else VIEW_TYPE_TRACK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == VIEW_TYPE_FOLDER) {
            val binding = FragmentFolderListItemBinding.inflate(inflater, parent, false)
            binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
            applyTheme?.invoke(binding.root)
            DirectoryFolderViewHolder(binding, onEntryClick)
        } else {
            val binding = FragmentMusicListItemBinding.inflate(inflater, parent, false)
            binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
            applyTheme?.invoke(binding.root)
            MusicViewHolder(
                binding = binding,
                musicSet = MusicSet.Tracks,
                theme = theme,
                onItemClick = null,
                onItemLongClick = null,
                onMenuClick = null
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is DirectoryNode -> (holder as DirectoryFolderViewHolder).bind(entry)

            is DirectoryTrack -> {
                val musicHolder = holder as MusicViewHolder
                musicHolder.bind(
                    item = ListItem.MusicItem(entry.music),
                    isCurrentTrack = entry.music.id == currentTrackId,
                    isPlaying = entry.music.id == currentTrackId && isCurrentTrackPlaying,
                    viewInfo = VIEW_INFO_DURATION
                )
                // MusicViewHolder.bind installs its own listeners, so rebind ours afterwards.
                musicHolder.itemView.setOnClickListener { onEntryClick(entry) }
                musicHolder.binding.musicItemMenu.setOnClickListener { onTrackMenuClick(entry) }
            }
        }
    }

    fun submitEntries(items: List<DirectoryEntry>) {
        entries = items
        notifyDataSetChanged()
    }

    fun submitPlaybackHighlight(currentTrackId: Long?, isPlaying: Boolean) {
        val nextTrackId = currentTrackId ?: NO_TRACK_ID
        if (this.currentTrackId == nextTrackId && isCurrentTrackPlaying == isPlaying) return

        this.currentTrackId = nextTrackId
        this.isCurrentTrackPlaying = isPlaying

        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_PLAYBACK_STATE)
        }
    }

    private class DirectoryFolderViewHolder(
        private val binding: FragmentFolderListItemBinding,
        private val onEntryClick: (DirectoryEntry) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(node: DirectoryNode) {
            val resources = binding.root.resources

            binding.musicItemTitle.text = node.name
            binding.musicItemAlbum.setImageResource(
                node.iconRes.takeIf { it != 0 } ?: R.drawable.main_folder_simple
            )

            // Original w7.w.c: track count once scanned, otherwise the sub-folder count.
            binding.musicItemArtist.visibility = View.VISIBLE
            binding.musicItemArtist.text = when {
                !node.isScanned -> ""
                node.trackCount > 0 -> resources.getQuantityString(
                    R.plurals.plurals_track,
                    node.trackCount,
                    node.trackCount
                )

                else -> resources.getQuantityString(
                    R.plurals.plurals_folder_item,
                    node.folderCount,
                    node.folderCount
                )
            }
            binding.musicItemDes.visibility = View.GONE
            binding.musicItemMenu.visibility = View.GONE

            binding.root.setOnClickListener { onEntryClick(node) }
        }
    }

    private companion object {
        private const val VIEW_TYPE_FOLDER = 0
        private const val VIEW_TYPE_TRACK = 1
        private const val NO_TRACK_ID = -1L
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playback_state"
        private const val VIEW_INFO_DURATION = "duration"
    }
}
