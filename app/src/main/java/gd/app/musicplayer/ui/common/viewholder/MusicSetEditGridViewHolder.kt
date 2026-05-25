package gd.app.musicplayer.ui.common.viewholder

import android.graphics.Color
import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSetEditGridBinding
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes

class MusicSetEditGridViewHolder(
    val binding: ActivityMusicSetEditGridBinding,
    private val accentColor: Int,
    val onItemClick: ((MusicSet) -> Unit)?
) :
    BaseViewHolder(binding.root) {
    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet: MusicSet = (item as ListItem.MusicSetItem).musicSet

        binding.musicItemTitle.text = musicSet.name
        musicSet.toDisplayInfo(binding.root.resources)?.let { info ->
            musicSet.loadArtwork(binding.musicItemImage, musicSet.resolvePlaceholderRes(false))
            binding.musicItemArtist.text = info.subtitle
        }

        binding.musicItemCheckbox.setImageResource(if (selected) R.drawable.vector_multi_checked else R.drawable.vector_multi_unchecked)

        binding.musicItemCheckbox.setColorFilter(
            if (selected) accentColor else Color.WHITE
        )
        binding.root.setOnClickListener { onItemClick?.invoke(musicSet) }
    }
}
