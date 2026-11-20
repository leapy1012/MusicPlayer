package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.checkbox.COUICheckBox
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.formatAddedDate
import gd.app.musicplayer.core.common.extension.formatFileSize
import gd.app.musicplayer.core.common.extension.highlight
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.common.extension.toDurationString
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
        accentColor: Int,
        sortStyle: String = ""
    ) {
        binding.musicItemTitle.text = music.title.highlight(highlightQuery, accentColor)
        binding.musicItemImage.loadMusicArtwork(music.albumArtSource())
        binding.musicItemArtist.text = music.artist.highlight(highlightQuery, accentColor)
        bindMetadata(music, sortStyle)
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

    /** Original music-select row: size / date / duration column follows the current sort style. */
    private fun bindMetadata(music: Music, sortStyle: String) {
        val text = when (sortStyle) {
            SORT_STYLE_SIZE -> music.formatFileSize(binding.root.context)
            SORT_STYLE_DATE -> music.formatAddedDate()
            SORT_STYLE_DURATION -> music.durationMs.toDurationString()
            else -> ""
        }
        binding.musicItemSize.text = text
        binding.musicItemSize.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private companion object {
        private const val SORT_STYLE_SIZE = "size"
        private const val SORT_STYLE_DATE = "date"
        private const val SORT_STYLE_DURATION = "duration"
    }
}
