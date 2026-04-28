package gd.app.musicplayer.ui.feature.library

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.LayoutArtistHeaderBinding

class ArtistAlbumHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = LayoutArtistHeaderBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )
    private var onAlbumClick: ((MusicSet.Album) -> Unit)? = null
    private val adapter = ArtistAlbumAdapter { album ->
        onAlbumClick?.invoke(album)
    }

    init {
        binding.recyclerview.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerview.adapter = adapter
        binding.recyclerview.itemAnimator = null
        isVisible = false
    }

    fun setOnAlbumClickListener(listener: ((MusicSet.Album) -> Unit)?) {
        onAlbumClick = listener
    }

    fun submitAlbums(albums: List<MusicSet.Album>) {
        val rows = albums.map { album ->
            ArtistAlbumAdapter.ArtistAlbumItem(
                album = album,
                subtitle = resources.getQuantityString(
                    R.plurals.plurals_track,
                    album.musicCount,
                    album.musicCount
                )
            )
        }
        adapter.submitList(rows)
        isVisible = rows.isNotEmpty()
    }
}
