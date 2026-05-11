package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.highlight
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding

class MusicSelectViewHolder(
    val binding: ActivityMusicSelectItemBinding,
    val onItemClick: ((Music) -> Unit)?
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(music: Music, selected: Boolean, locked: Boolean, highlightQuery: String, accentColor: Int) {
        val context = binding.root.context

        binding.musicItemTitle.text = music.title.highlight(highlightQuery, accentColor)
        binding.musicItemImage.setImageResource(R.drawable.default_album_identify)
        binding.musicItemArtist.text = music.artist
        binding.musicItemMenu.setImageResource(
            if (selected) R.drawable.vector_multi_checked else R.drawable.vector_multi_unchecked
        )
        binding.musicItemMenu.setColorFilter(
            if (selected) accentColor else ContextCompat.getColor(context, R.color.white)
        )
        binding.root.isActivated = selected
        binding.root.isEnabled = !locked
        if (locked) {
            binding.musicItemMenu.alpha = 0.2f
        } else {
            binding.musicItemMenu.alpha = 1.0f
        }
        itemView.setOnClickListener(
            if (locked) null else View.OnClickListener { onItemClick?.invoke(music) }
        )

    }
}
