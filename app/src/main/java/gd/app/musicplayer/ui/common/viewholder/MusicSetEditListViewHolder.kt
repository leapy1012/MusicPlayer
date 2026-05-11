package gd.app.musicplayer.ui.common.viewholder

import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSetEditItemBinding

class MusicSetEditListViewHolder(
    val binding: ActivityMusicSetEditItemBinding,
    private val accentColor: Int,
    val onItemClick: ((MusicSet) -> Unit)?
) : BaseViewHolder(binding.root) {
    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet: MusicSet = (item as ListItem.MusicSetItem).musicSet
        val context = binding.root.context

        binding.musicItemTitle.text = musicSet.name
        musicSet.toDisplayInfo(binding.root.resources)?.let { info ->
            binding.musicItemImage.setImageResource(info.iconRes)
            binding.musicItemArtist.text = info.subtitle
        }

        binding.musicItemCheckbox.setImageResource(if (selected) R.drawable.vector_multi_checked else R.drawable.vector_multi_unchecked)

        binding.musicItemCheckbox.setColorFilter(
            if (selected) accentColor else ContextCompat.getColor(context, R.color.white)
        )

        binding.root.setOnClickListener { onItemClick?.invoke(musicSet) }
    }
}
