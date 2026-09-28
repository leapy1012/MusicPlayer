package gd.app.musicplayer.feature.home

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
import gd.app.musicplayer.databinding.FragmentMainPlaylistItemBinding
import gd.app.musicplayer.domain.model.MusicSet
import java.util.Collections

class MainPlaylistAdapter(
    private val onPlaylistClick: (MusicSet.Playlist) -> Unit,
    private val onAddClick: () -> Unit,
    private val onPlaylistOrderChanged: (List<Long>) -> Unit,
    private val applyTheme: (View) -> Unit = {}
) : RecyclerView.Adapter<MainPlaylistAdapter.ViewHolder>() {

    private val playlists = mutableListOf<MusicSet.Playlist>()
    private var pendingExternalList: List<MusicSet.Playlist>? = null
    private var isDragging = false
    private var hasPendingOrderChange = false
    private var itemSizePx = 0

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
            onAddClick = onAddClick,
            applyTheme = applyTheme
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.applySize(itemSizePx)
        if (isPlaylistPosition(position)) {
            holder.bindPlaylist(playlists[position])
        } else {
            holder.bindAdd()
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SIZE }) {
            holder.applySize(itemSizePx)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    fun setItemSize(sizePx: Int) {
        if (sizePx <= 0 || sizePx == itemSizePx) return
        itemSizePx = sizePx
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SIZE)
    }

    fun submitPlaylists(newItems: List<MusicSet.Playlist>) {
        if (isDragging) {
            pendingExternalList = newItems
            return
        }
        submitPlaylistsInternal(newItems)
    }

    fun canDrag(position: Int): Boolean = isPlaylistPosition(position)

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
        private val onAddClick: () -> Unit,
        private val applyTheme: (View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val playlistPlaceholder by lazy(LazyThreadSafetyMode.NONE) {
            PlaylistCardPlaceholderDrawable(
                context = binding.root.context,
                iconResId = R.drawable.main_list,
                iconTint = resolveThemeColor(
                    com.coui.appcompat.R.attr.couiColorLabelTheme,
                    0xFF2660F5.toInt()
                )
            )
        }

        private val addPlaceholder by lazy(LazyThreadSafetyMode.NONE) {
            PlaylistCardPlaceholderDrawable(
                context = binding.root.context,
                iconResId = R.drawable.main_new_list,
                iconTint = resolveThemeColor(
                    com.coui.appcompat.R.attr.couiColorSecondNeutral,
                    0xFF8A9199.toInt()
                )
            )
        }

        private var appliedSizePx = 0

        init {
            binding.root.applyRoundedOutline(com.coui.appcompat.R.dimen.coui_round_corner_m)
        }

        fun applySize(sizePx: Int) {
            if (sizePx <= 0 || sizePx == appliedSizePx) return
            appliedSizePx = sizePx
            binding.root.setSquare(FixedSizeMeasurePolicy(sizePx, sizePx))
        }

        fun bindPlaylist(playlist: MusicSet.Playlist) = with(binding) {
            mainItemName.visibility = View.VISIBLE
            mainItemLabelDivider.visibility = View.VISIBLE
            mainItemExtra.visibility = View.VISIBLE
            mainItemName.text = playlist.name
            mainItemExtra.text = playlist.musicCount.toString()
            root.contentDescription = playlist.name

            val artwork = playlist.albumArt?.takeIf { it.isNotBlank() }
            if (artwork == null) {
                Glide.with(mainItemImage).clear(mainItemImage)
                applyIconSizedImage(mainItemImage)
                mainItemImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                mainItemImage.setImageDrawable(playlistPlaceholder)
            } else {
                applyFillImage(mainItemImage)
                mainItemImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                Glide.with(mainItemImage)
                    .load(artwork)
                    .placeholder(playlistPlaceholder)
                    .error(playlistPlaceholder)
                    .centerCrop()
                    .into(mainItemImage)
            }

            root.setOnClickListener { onPlaylistClick(playlist) }
            applyTheme(root)
        }

        fun bindAdd() = with(binding) {
            mainItemName.visibility = View.GONE
            mainItemLabelDivider.visibility = View.GONE
            mainItemExtra.visibility = View.GONE
            mainItemName.text = null
            mainItemExtra.text = null
            root.contentDescription = "Create playlist"

            Glide.with(mainItemImage).clear(mainItemImage)
            applyIconSizedImage(mainItemImage)
            mainItemImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            mainItemImage.setImageDrawable(addPlaceholder)
            root.setOnClickListener { onAddClick() }
            applyTheme(root)
        }

        private fun applyIconSizedImage(imageView: android.widget.ImageView) {
            val size = imageView.resources.getDimensionPixelSize(R.dimen.main_item_image_size)
            val params = imageView.layoutParams
            if (params.width != size || params.height != size) {
                params.width = size
                params.height = size
                imageView.layoutParams = params
            }
        }

        private fun applyFillImage(imageView: android.widget.ImageView) {
            val params = imageView.layoutParams
            if (params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                params.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                imageView.layoutParams = params
            }
        }

        private fun resolveThemeColor(attr: Int, fallback: Int): Int {
            val typed = binding.root.context.obtainStyledAttributes(intArrayOf(attr))
            val color = typed.getColor(0, fallback)
            typed.recycle()
            return color
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

    /** Transparent fill — card ripple bg shows through; only the tinted icon is drawn. */
    private class PlaylistCardPlaceholderDrawable(
        context: Context,
        iconResId: Int,
        iconTint: Int
    ) : Drawable() {

        private val iconDrawable = AppCompatResources.getDrawable(context, iconResId)?.mutate()?.also {
            it.setTint(iconTint)
        }
        private val iconBounds = Rect()
        private val iconSize = context.resources.getDimensionPixelOffset(R.dimen.main_item_image_size)

        override fun draw(canvas: Canvas) {
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
        private const val PAYLOAD_SIZE = "size"
    }
}
