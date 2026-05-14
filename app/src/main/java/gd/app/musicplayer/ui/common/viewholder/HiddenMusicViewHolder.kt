package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.databinding.ActivityHiddenFoldersMusicItemBinding

class HiddenMusicViewHolder(
    private val binding: ActivityHiddenFoldersMusicItemBinding,
    private val onRemoveClick: (Music) -> Unit
) :
    RecyclerView.ViewHolder(binding.root), View.OnClickListener {

    private lateinit var item: Music

    init {
        binding.musicItemMenu.setOnClickListener(this)
    }

    fun bind(item: Music) {
        this.item = item
        binding.musicItemTitle.text = item.title
        binding.musicItemArtist.text = item.artist
        binding.musicItemAlbum.loadMusicArtwork(item.albumArtSource())
    }

    override fun onClick(v: View) {
        onRemoveClick(item)
    }
}
