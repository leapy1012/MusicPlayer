package gd.app.musicplayer.ui.common.viewholder

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes


class FolderSelectViewHolder(
    val binding: ActivityMusicSelectItemBinding,
    val onItemClick: ((MusicSet) -> Unit)?
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(musicSet: MusicSet.Folder, highlightQuery: String) {
        val context = binding.root.context
        val accentColor = context.appDependencies.themeRepo.getAccentColor(context)

        binding.musicItemTitle.text = buildHighlightedTitle(
            title = musicSet.name,
            query = highlightQuery,
            accentColor = accentColor
        )

        binding.musicItemImage.setImageResource(musicSet.resolvePlaceholderRes(false))
        binding.musicItemArtist.text = buildHighlightedTitle(
            musicSet.folderPath,
            highlightQuery,
            accentColor
        )
        binding.musicItemMenu.visibility = View.GONE

        itemView.setOnClickListener { onItemClick?.invoke(musicSet) }

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
