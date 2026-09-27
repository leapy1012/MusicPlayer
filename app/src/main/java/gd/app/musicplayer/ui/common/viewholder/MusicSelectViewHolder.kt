package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.checkbox.COUICheckBox
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.highlight
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.domain.model.Music

class MusicSelectViewHolder(
    val binding: ActivityMusicSelectItemBinding,
    val onItemClick: ((Music) -> Unit)?
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        music: Music,
        selected: Boolean,
        locked: Boolean,
        highlightQuery: String,
        accentColor: Int
    ) {
        binding.musicItemTitle.text = music.title.highlight(highlightQuery, accentColor)
        binding.musicItemImage.loadMusicArtwork(music.albumArtSource())
        binding.musicItemArtist.text = music.artist
        binding.musicItemCheckbox.setState(
            if (selected) COUICheckBox.SELECT_ALL else COUICheckBox.SELECT_NONE
        )
        binding.root.isActivated = selected
        binding.root.isEnabled = !locked
        binding.musicItemCheckbox.alpha = if (locked) 0.2f else 1.0f
        itemView.setOnClickListener(
            if (locked) null else View.OnClickListener { onItemClick?.invoke(music) }
        )
    }
}
