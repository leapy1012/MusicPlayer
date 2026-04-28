package gd.app.musicplayer.ui.common.viewholder

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.core.extension.appDependencies

class MusicSelectViewHolder(
    val binding: ActivityMusicSelectItemBinding,
    val onItemClick: ((Music) -> Unit)?
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(music: Music, selected: Boolean, locked: Boolean, highlightQuery: String) {
        val context = binding.root.context
        val accentColor = context.appDependencies.themeRepo.getAccentColor(context)

        binding.musicItemTitle.text = buildHighlightedTitle(
            title = music.title,
            query = highlightQuery,
            accentColor = accentColor
        )

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

    private fun buildHighlightedTitle(
        title: String,
        query: String,
        accentColor: Int
    ): CharSequence {
        if (query.isBlank() || title.isBlank()) return title

        var startIndex = title.indexOf(query, ignoreCase = true)
        if (startIndex == -1) return title

        val spannable = SpannableString(title)
        val queryLength = query.length

        while (startIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(accentColor),
                startIndex,
                startIndex + queryLength,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = title.indexOf(
                query,
                startIndex = startIndex + queryLength,
                ignoreCase = true
            )
        }

        return spannable
    }
}