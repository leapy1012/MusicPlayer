package gd.app.musicplayer.ui.common.viewholder

import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSetEditItemBinding

class MusicSetEditListViewHolder(
    val binding: ActivityMusicSetEditItemBinding,
    private val accentColor: Int,
    val onItemClick: ((MusicSet) -> Unit)?
) : BaseViewHolder(binding.root) {
    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet = (item as ListItem.MusicSetItem).musicSet

        musicSet.bindTitleSubtitleArtwork(
            titleView = binding.musicItemTitle,
            subtitleView = binding.musicItemArtist,
            artworkView = binding.musicItemImage
        )
        binding.musicItemCheckbox.bindMultiSelectCheckbox(selected, accentColor)
        binding.root.setOnClickListener { onItemClick?.invoke(musicSet) }
    }
}
