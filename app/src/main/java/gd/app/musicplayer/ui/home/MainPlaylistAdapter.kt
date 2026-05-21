package gd.app.musicplayer.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import gd.app.lib.view.square.FixedSizeMeasurePolicy
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.databinding.FragmentMainPlaylistItemBinding
import gd.app.musicplayer.domain.model.MusicSet
import java.util.Collections

class MainPlaylistAdapter(
    private val onPlaylistClick: (MusicSet.Playlist) -> Unit,
    private val onAddClick: () -> Unit,
    private val onPlaylistOrderChanged: (List<Long>) -> Unit
) : RecyclerView.Adapter<MainPlaylistAdapter.ViewHolder>() {

    private val playlists = mutableListOf<MusicSet.Playlist>()
    private var pendingExternalList: List<MusicSet.Playlist>? = null
    private var isDragging = false
    private var hasPendingOrderChange = false

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = playlists.size + ADD_ITEM_COUNT

    override fun getItemId(position: Int): Long {
        return if (isPlaylistPosition(position)) playlists[position].id else ADD_ITEM_ID
    }

    override fun getItemViewType(position: Int): Int {
        return if (isPlaylistPosition(position)) VIEW_TYPE_PLAYLIST else VIEW_TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            binding = FragmentMainPlaylistItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onPlaylistClick = onPlaylistClick,
            onAddClick = onAddClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (isPlaylistPosition(position)) {
            holder.bindPlaylist(playlists[position])
        } else {
            holder.bindAdd()
        }
    }

    fun submitPlaylists(newItems: List<MusicSet.Playlist>) {
        if (isDragging) {
            pendingExternalList = newItems
            return
        }
        submitPlaylistsInternal(newItems)
    }

    fun canMove(fromPosition: Int, toPosition: Int): Boolean {
        return isPlaylistPosition(fromPosition) &&
                isPlaylistPosition(toPosition) &&
                fromPosition != toPosition
    }

    fun startDrag() {
        isDragging = true
        pendingExternalList = null
        hasPendingOrderChange = false
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!canMove(fromPosition, toPosition)) return false

        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) {
                Collections.swap(playlists, index, index + 1)
            }
        } else {
            for (index in fromPosition downTo toPosition + 1) {
                Collections.swap(playlists, index, index - 1)
            }
        }

        hasPendingOrderChange = true
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun finishDrag() {
        isDragging = false

        if (hasPendingOrderChange) {
            hasPendingOrderChange = false
            pendingExternalList = null
            onPlaylistOrderChanged(playlists.map { it.id })
            return
        }

        pendingExternalList?.let(::submitPlaylistsInternal)
        pendingExternalList = null
    }

    private fun submitPlaylistsInternal(newItems: List<MusicSet.Playlist>) {
        if (playlists == newItems) return

        val oldItems = playlists.toList()
        val diffResult = DiffUtil.calculateDiff(
            PlaylistDiffCallback(
                oldItems = oldItems,
                newItems = newItems
            )
        )

        playlists.clear()
        playlists.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    private fun isPlaylistPosition(position: Int): Boolean {
        return position in playlists.indices
    }

    class ViewHolder(
        private val binding: FragmentMainPlaylistItemBinding,
        private val onPlaylistClick: (MusicSet.Playlist) -> Unit,
        private val onAddClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val playlistPlaceholder by lazy(LazyThreadSafetyMode.NONE) {
            PlaylistCardPlaceholderDrawable(
                context = binding.root.context,
                iconResId = R.drawable.main_list,
                backgroundColor = PLAYLIST_BACKGROUND_COLOR
            )
        }

        private val addPlaceholder by lazy(LazyThreadSafetyMode.NONE) {
            PlaylistCardPlaceholderDrawable(
                context = binding.root.context,
                iconResId = R.drawable.main_new_list,
                backgroundColor = ADD_BACKGROUND_COLOR
            )
        }

        init {
            binding.root.setSquare(
                FixedSizeMeasurePolicy(
                    calculateItemSize(binding.root.context),
                    calculateItemSize(binding.root.context)
                )
            )
            binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)
        }

        fun bindPlaylist(playlist: MusicSet.Playlist) = with(binding) {
            mainItemBanner.visibility = View.VISIBLE
            mainItemName.text = playlist.name
            mainItemExtra.text = playlist.musicCount.toString()
            root.contentDescription = playlist.name

            val artwork = playlist.albumArt?.takeIf { it.isNotBlank() }
            if (artwork == null) {
                Glide.with(mainItemImage).clear(mainItemImage)
                mainItemImage.setImageDrawable(playlistPlaceholder)
            } else {
                Glide.with(mainItemImage)
                    .load(artwork)
                    .placeholder(playlistPlaceholder)
                    .error(playlistPlaceholder)
                    .centerCrop()
                    .into(mainItemImage)
            }

            root.setOnClickListener { onPlaylistClick(playlist) }
        }

        fun bindAdd() = with(binding) {
            mainItemBanner.visibility = View.GONE
            mainItemName.text = null
            mainItemExtra.text = null
            root.contentDescription = "Create playlist"

            Glide.with(mainItemImage).clear(mainItemImage)
            mainItemImage.setImageDrawable(addPlaceholder)
            root.setOnClickListener { onAddClick() }
        }

        private fun calculateItemSize(context: Context): Int {
            val columnCount = if (context.isTablet()) TABLET_COLUMN_COUNT else PHONE_COLUMN_COUNT
            val spacingPx = context.dpToPx(GRID_SPACING_DP)
            return (context.screenWidth - spacingPx * (columnCount + 1)) / columnCount
        }
    }

    private class PlaylistDiffCallback(
        private val oldItems: List<MusicSet.Playlist>,
        private val newItems: List<MusicSet.Playlist>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldItems.size + ADD_ITEM_COUNT

        override fun getNewListSize(): Int = newItems.size + ADD_ITEM_COUNT

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPlaylist = oldItems.getOrNull(oldItemPosition)
            val newPlaylist = newItems.getOrNull(newItemPosition)

            return when {
                oldPlaylist != null && newPlaylist != null -> oldPlaylist.id == newPlaylist.id
                oldPlaylist == null && newPlaylist == null -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems.getOrNull(oldItemPosition) == newItems.getOrNull(newItemPosition)
        }
    }

    private class PlaylistCardPlaceholderDrawable(
        context: Context,
        iconResId: Int,
        private val backgroundColor: Int
    ) : Drawable() {

        private val iconDrawable = AppCompatResources.getDrawable(context, iconResId)?.mutate()
        private val iconBounds = Rect()
        private val iconSize = context.resources.getDimensionPixelOffset(R.dimen.main_image_size)

        override fun draw(canvas: Canvas) {
            canvas.drawColor(backgroundColor)
            iconDrawable?.let { drawable ->
                drawable.bounds = iconBounds
                drawable.draw(canvas)
            }
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            val left = bounds.left + (bounds.width() - iconSize) / 2
            val top = bounds.top + (bounds.height() - iconSize) / 2
            iconBounds.set(left, top, left + iconSize, top + iconSize)
        }

        override fun setAlpha(alpha: Int) {
            iconDrawable?.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            iconDrawable?.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val VIEW_TYPE_PLAYLIST = 0
        private const val VIEW_TYPE_ADD = 1
        private const val ADD_ITEM_COUNT = 1
        private const val ADD_ITEM_ID = Long.MIN_VALUE

        private const val PHONE_COLUMN_COUNT = 3
        private const val TABLET_COLUMN_COUNT = 6
        private const val GRID_SPACING_DP = 8f

        private const val PLAYLIST_BACKGROUND_COLOR = 0xCC88C6EC.toInt()
        private const val ADD_BACKGROUND_COLOR = 0x997CAACA.toInt()
    }
}
