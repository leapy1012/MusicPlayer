package gd.app.musicplayer.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.lib.view.square.FixedSizeMeasurePolicy
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentMainPlaylistItemBinding

class MainPlaylistAdapter(
    context: Context,
    private val onPlaylistClick: (MusicSet.Playlist) -> Unit,
    private val onAddClick: () -> Unit,
    private val onPlaylistOrderChanged: (List<Long>) -> Unit
) : RecyclerView.Adapter<MainPlaylistAdapter.ViewHolder>() {

    private val playlists = mutableListOf<MusicSet.Playlist>()

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = playlists.size + 1

    override fun getItemId(position: Int): Long {
        if (position < playlists.size)
            return playlists[position].id

        return Long.MIN_VALUE
    }

    override fun getItemViewType(position: Int): Int =
        if (position < playlists.size) VIEW_TYPE_PLAYLIST else VIEW_TYPE_ADD


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FragmentMainPlaylistItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ADD) {
            holder.bindAdd(onAddClick)
        } else {
            holder.bindPlaylist(playlists[position], onPlaylistClick)
        }
    }

    fun submitPlaylists(items: List<MusicSet.Playlist>) {
        if (playlists == items) return

        playlists.clear()
        playlists.addAll(items)
        notifyDataSetChanged()
    }

    fun canMove(fromPosition: Int, toPosition: Int): Boolean {
        return fromPosition in playlists.indices && toPosition in playlists.indices
    }

    fun onItemDragged(fromPosition: Int, toPosition: Int) {
        if (!canMove(fromPosition, toPosition) || fromPosition == toPosition) return

        val movedItem = playlists.removeAt(fromPosition)
        playlists.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
        onPlaylistOrderChanged(playlists.map(MusicSet.Playlist::id))
    }

    class ViewHolder(
        private val binding: FragmentMainPlaylistItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            val itemSizePx: Int = run {
                val columnCount = if (binding.root.context.isTablet()) 6 else 3
                val spacingPx = binding.root.context.dpToPx(8f)
                (binding.root.context.screenWidth - (spacingPx * (columnCount + 1))) / columnCount
            }

            binding.root.setSquare(FixedSizeMeasurePolicy(itemSizePx, itemSizePx))
            binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)
        }

        fun bindPlaylist(
            playlist: MusicSet.Playlist,
            onPlaylistClick: (MusicSet.Playlist) -> Unit
        ) {
            binding.mainItemImageParent.setBackgroundColor(PLAYLIST_BACKGROUND_COLOR)
            binding.mainItemBanner.visibility = View.VISIBLE
            binding.mainItemName.text = playlist.name
            binding.mainItemExtra.text = playlist.musicCount.toString()
            binding.mainItemImage.setImageResource(R.drawable.main_list)
            binding.root.setOnClickListener { onPlaylistClick(playlist) }
        }

        fun bindAdd(onAddClick: () -> Unit) {
            binding.mainItemImageParent.setBackgroundColor(ADD_BACKGROUND_COLOR)
            binding.mainItemBanner.visibility = View.GONE
            binding.mainItemName.text = ""
            binding.mainItemExtra.text = ""
            binding.mainItemImage.setImageResource(R.drawable.main_new_list)
            binding.root.setOnClickListener { onAddClick() }
        }
    }

    companion object {
        private const val VIEW_TYPE_PLAYLIST = 0
        private const val VIEW_TYPE_ADD = 1
        private const val PLAYLIST_BACKGROUND_COLOR = 0xCC88C6EC.toInt()
        private const val ADD_BACKGROUND_COLOR = 0x997CAACA.toInt()
    }
}
