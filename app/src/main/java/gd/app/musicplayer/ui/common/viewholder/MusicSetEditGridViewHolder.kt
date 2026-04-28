package gd.app.musicplayer.ui.common.viewholder

import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSetEditGridBinding
import gd.app.musicplayer.core.extension.appDependencies

class MusicSetEditGridViewHolder(
    val binding: ActivityMusicSetEditGridBinding,
    val onItemClick: ((MusicSet) -> Unit)?
) :
    BaseViewHolder(binding.root) {
    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet: MusicSet = (item as ListItem.MusicSetItem).musicSet
        val context = binding.root.context
        val accentColor = context.appDependencies.themeRepo.getAccentColor(context)

        musicSet.toDisplayInfo(binding.root.resources)?.let { info ->
            binding.musicItemImage.setImageResource(info.iconRes)
            binding.musicItemArtist.text = info.subtitle
        }

        binding.musicItemCheckbox.setImageResource(if (selected) R.drawable.vector_multi_checked else R.drawable.vector_multi_unchecked)

        binding.musicItemCheckbox.setColorFilter(
            if (selected) accentColor else ContextCompat.getColor(context, R.color.white)
        )
//        binding.root.isActivated = selected
        binding.root.setOnClickListener { onItemClick?.invoke(musicSet) }
//        binding.musicItemMenu.setOnClickListener { onToggleSelection(item) }
    }
}
