package gd.app.musicplayer.ui.library.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentArtistAlbumItemBinding
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes

class ArtistAlbumAdapter(
    private val onItemClick: ((MusicSet.Album) -> Unit)? = null
) :
    ListAdapter<ArtistAlbumAdapter.ArtistAlbumItem, ArtistAlbumAdapter.ArtistAlbumViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistAlbumViewHolder {
        val binding = FragmentArtistAlbumItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ArtistAlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtistAlbumViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item.album)
        }
    }

    class ArtistAlbumViewHolder(
        private val binding: FragmentArtistAlbumItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArtistAlbumItem) {
            item.album.loadArtwork(
                binding.musicItemAlbum,
                item.album.resolvePlaceholderRes(false)
            )
            binding.musicItemTitle.text = item.album.name
            binding.musicItemArtist.text = item.subtitle
        }
    }

    data class ArtistAlbumItem(
        val album: MusicSet.Album,
        val subtitle: String
    )
    class DiffCallback : DiffUtil.ItemCallback<ArtistAlbumItem>() {
        override fun areItemsTheSame(oldItem: ArtistAlbumItem, newItem: ArtistAlbumItem): Boolean {
            return oldItem.album.id == newItem.album.id
        }

        override fun areContentsTheSame(oldItem: ArtistAlbumItem, newItem: ArtistAlbumItem): Boolean {
            return oldItem == newItem
        }
    }
}
