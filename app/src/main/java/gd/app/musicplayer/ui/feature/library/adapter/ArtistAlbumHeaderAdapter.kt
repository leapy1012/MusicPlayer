package gd.app.musicplayer.ui.feature.library.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.data.model.MusicSet.Album
import gd.app.musicplayer.ui.feature.library.ArtistAlbumHeaderView

class ArtistAlbumHeaderAdapter(
    private val onAlbumClick: (Album) -> Unit
) : RecyclerView.Adapter<ArtistAlbumHeaderAdapter.HeaderViewHolder>() {

    private var albums: List<Album> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HeaderViewHolder {
        return HeaderViewHolder(
            ArtistAlbumHeaderView(parent.context)
        )
    }

    override fun onBindViewHolder(
        holder: HeaderViewHolder,
        position: Int
    ) {
        holder.bind(
            albums = albums,
            onAlbumClick = onAlbumClick
        )
    }

    override fun getItemCount(): Int {
        return if (albums.isEmpty()) 0 else 1
    }

    fun submitAlbums(newAlbums: List<Album>) {
        val oldAlbums = albums
        val hadHeader = oldAlbums.isNotEmpty()
        val hasHeader = newAlbums.isNotEmpty()

        albums = newAlbums

        when {
            !hadHeader && hasHeader -> {
                notifyItemInserted(0)
            }

            hadHeader && !hasHeader -> {
                notifyItemRemoved(0)
            }

            hadHeader && oldAlbums != newAlbums -> {
                notifyItemChanged(0)
            }
        }
    }

    class HeaderViewHolder(
        private val headerView: ArtistAlbumHeaderView
    ) : RecyclerView.ViewHolder(headerView) {

        fun bind(
            albums: List<Album>,
            onAlbumClick: (Album) -> Unit
        ) {
            headerView.setOnAlbumClickListener(onAlbumClick)
            headerView.submitAlbums(albums)
        }
    }
}
