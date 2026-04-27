package gd.app.musicplayer.feature.playlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentPlaylistAddHeaderBinding
import gd.app.musicplayer.databinding.FragmentPlaylistAddItemBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import gd.app.musicplayer.ui.common.viewholder.toDisplayInfo
import gd.app.musicplayer.core.ui.extension.appContainer

class PlaylistSelectAdapter(
    private val inflater: LayoutInflater
) : ListAdapter<PlaylistRow, RecyclerView.ViewHolder>(RowDiffCallback) {

    private val accentColor: Int by lazy(LazyThreadSafetyMode.NONE) {
        inflater.context.appContainer.themeRepo.getAccentColor(inflater.context)
    }

    private val selectedPlaylistIds = linkedSetOf<Long>()
    private var selectionCountListener: OnSelectionCountChangedListener? = null
    private var createPlaylistClickListener: (() -> Unit)? = null
    private var selectionChangedListener: ((Set<MusicSet>) -> Unit)? = null

    init {
        submitList(listOf(PlaylistRow.Header))
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PlaylistRow.Header -> VIEW_TYPE_CREATE_PLAYLIST_HEADER
            is PlaylistRow.Item -> VIEW_TYPE_PLAYLIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CREATE_PLAYLIST_HEADER -> {
                val binding = FragmentPlaylistAddHeaderBinding.inflate(
                    inflater,
                    parent,
                    false
                )
                applyCurrentTheme(binding.root)
                CreatePlaylistHeaderViewHolder(binding)
            }

            else -> {
                val binding = FragmentPlaylistAddItemBinding.inflate(inflater, parent, false)
                applyCurrentTheme(binding.root)
                PlaylistViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PlaylistViewHolder -> {
                val item = (getItem(position) as PlaylistRow.Item).playlist
                holder.bind(item, item.id in selectedPlaylistIds)
            }
        }
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

        if (holder is PlaylistViewHolder && payloads.contains(PAYLOAD_SELECTION)) {
            val item = (getItem(position) as PlaylistRow.Item).playlist
            holder.bindSelection(item.id in selectedPlaylistIds)
            return
        }

        super.onBindViewHolder(holder, position, payloads)
    }

    fun submitPlaylists(
        items: List<MusicSet>?,
        selectedItems: Set<MusicSet> = emptySet()
    ) {
        val rows = ArrayList<PlaylistRow>(1 + (items?.size ?: 0))
        rows += PlaylistRow.Header
        items.orEmpty().forEach { set ->
            val playlist = set as? MusicSet.Playlist ?: return@forEach
            rows += PlaylistRow.Item(playlist)
        }

        val selectedIds = selectedItems.mapNotNullTo(linkedSetOf()) { set ->
            (set as? MusicSet.Playlist)?.id
        }

        selectedPlaylistIds.retainAll(rows.mapNotNullTo(hashSetOf()) {
            (it as? PlaylistRow.Item)?.playlist?.id
        })
        selectedPlaylistIds.clear()
        selectedPlaylistIds.addAll(selectedIds)
        selectedPlaylistIds.retainAll(rows.mapNotNullTo(hashSetOf()) {
            (it as? PlaylistRow.Item)?.playlist?.id
        })

        submitList(rows) {
            notifySelectionStateChanged(fullRefresh = true)
        }
    }

    fun getSelectedPlaylists(items: Set<MusicSet>) {
        selectedPlaylistIds.clear()
        items.forEach { set ->
            (set as? MusicSet.Playlist)?.id?.let(selectedPlaylistIds::add)
        }
        currentList
            .mapNotNullTo(hashSetOf()) { (it as? PlaylistRow.Item)?.playlist?.id }
            .also(selectedPlaylistIds::retainAll)
        notifySelectionStateChanged(fullRefresh = true)
    }

    fun selectPlaylist(playlist: MusicSet) {
        val id = (playlist as? MusicSet.Playlist)?.id ?: return
        if (selectedPlaylistIds.add(id)) {
            notifySelectionStateChanged(changedIds = setOf(id))
        }
    }

    fun setOnSelectionCountChangedListener(listener: OnSelectionCountChangedListener?) {
        selectionCountListener = listener
    }

    fun setOnCreatePlaylistClickListener(listener: (() -> Unit)?) {
        createPlaylistClickListener = listener
    }

    fun setOnSelectionChangedListener(listener: ((Set<MusicSet>) -> Unit)?) {
        selectionChangedListener = listener
    }

    private fun notifySelectionStateChanged(
        changedIds: Set<Long> = emptySet(),
        fullRefresh: Boolean = false
    ) {
        if (fullRefresh) {
            currentList.forEachIndexed { index, row ->
                if (row is PlaylistRow.Item) {
                    notifyItemChanged(index, PAYLOAD_SELECTION)
                }
            }
        } else if (changedIds.isNotEmpty()) {
            currentList.forEachIndexed { index, row ->
                val id = (row as? PlaylistRow.Item)?.playlist?.id ?: return@forEachIndexed
                if (id in changedIds) {
                    notifyItemChanged(index, PAYLOAD_SELECTION)
                }
            }
        }

        selectionCountListener?.onSelectionCountChanged(selectedPlaylistIds.size)
        selectionChangedListener?.invoke(
            currentList.mapNotNullTo(linkedSetOf()) { row ->
                val playlist = (row as? PlaylistRow.Item)?.playlist ?: return@mapNotNullTo null
                if (playlist.id in selectedPlaylistIds) playlist else null
            }
        )
    }

    private fun togglePlaylistSelection(playlist: MusicSet.Playlist) {
        val isSelected = if (selectedPlaylistIds.remove(playlist.id)) {
            false
        } else {
            selectedPlaylistIds.add(playlist.id)
            true
        }
        notifySelectionStateChanged(changedIds = setOf(playlist.id))
    }

    private inner class PlaylistViewHolder(
        private val binding: FragmentPlaylistAddItemBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        private lateinit var playlist: MusicSet.Playlist

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(item: MusicSet.Playlist, selected: Boolean) {
            playlist = item
            val context = binding.root.context

            val fallbackResId = item.resolvePlaceholderRes(false)
            if (item.id == 1L) {
                setImageResourceSafely(binding.musicItemAlbum, fallbackResId)
            } else {
                item.loadArtwork(binding.musicItemAlbum, fallbackResId)
            }

            binding.musicItemTitle.text = item.name
            binding.musicItemArtist.text = item.toDisplayInfo(binding.root.resources)?.subtitle
            bindSelection(selected)

            val disabled = item.disabled
            binding.root.alpha = if (disabled) 0.4f else 1f
            binding.root.isEnabled = !disabled
            binding.musicItemMenu.isEnabled = !disabled
        }

        fun bindSelection(selected: Boolean) {
            val context = binding.root.context
            binding.musicItemMenu.isSelected = selected
            binding.musicItemMenu.setImageResource(
                if (selected) R.drawable.vector_multi_checked else R.drawable.vector_multi_unchecked
            )
            binding.musicItemMenu.setColorFilter(
                if (selected) accentColor else ContextCompat.getColor(context, R.color.white)
            )
        }

        override fun onClick(v: View) {
            if (playlist.disabled) return
            togglePlaylistSelection(playlist)
        }
    }

    private inner class CreatePlaylistHeaderViewHolder(binding: FragmentPlaylistAddHeaderBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            createPlaylistClickListener?.invoke()
        }
    }

    fun setImageResourceSafely(imageView: ImageView, @DrawableRes resId: Int) {
        if (imageView.context is BaseActivity && (imageView.context as BaseActivity).isDestroyed) return
        Glide.with(imageView.context).clear(imageView)
        imageView.setImageResource(resId)
    }

    fun interface OnSelectionCountChangedListener {
        fun onSelectionCountChanged(count: Int)
    }

    private companion object {
        const val VIEW_TYPE_CREATE_PLAYLIST_HEADER = 0
        const val VIEW_TYPE_PLAYLIST = 1
        const val PAYLOAD_SELECTION = "selection"
    }
}

sealed class PlaylistRow {
    data object Header : PlaylistRow()
    data class Item(val playlist: MusicSet.Playlist) : PlaylistRow()
}

private object RowDiffCallback : DiffUtil.ItemCallback<PlaylistRow>() {
    override fun areItemsTheSame(oldItem: PlaylistRow, newItem: PlaylistRow): Boolean {
        return when {
            oldItem is PlaylistRow.Header && newItem is PlaylistRow.Header -> true
            oldItem is PlaylistRow.Item && newItem is PlaylistRow.Item ->
                oldItem.playlist.id == newItem.playlist.id

            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: PlaylistRow, newItem: PlaylistRow): Boolean {
        return oldItem == newItem
    }
}
