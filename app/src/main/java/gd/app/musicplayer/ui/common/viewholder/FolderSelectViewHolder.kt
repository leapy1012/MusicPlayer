package gd.app.musicplayer.ui.common.viewholder

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.core.extension.highlight
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes


class FolderSelectViewHolder(
    val binding: ActivityMusicSelectItemBinding,
    val onItemClick: ((MusicSet) -> Unit)?
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(musicSet: MusicSet.Folder, highlightQuery: String, accentColor: Int) {
        binding.musicItemTitle.text = musicSet.name.highlight(highlightQuery, accentColor)
        binding.musicItemImage.setImageResource(musicSet.resolvePlaceholderRes(false))
        binding.musicItemArtist.text = musicSet.folderPath.highlight(highlightQuery, accentColor)
        binding.musicItemMenu.visibility = View.GONE

        itemView.setOnClickListener { onItemClick?.invoke(musicSet) }

    }
}
