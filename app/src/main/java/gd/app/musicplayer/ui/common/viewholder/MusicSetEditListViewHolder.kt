package gd.app.musicplayer.ui.common.viewholder

import com.coui.appcompat.checkbox.COUICheckBox
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSetEditItemBinding

class MusicSetEditListViewHolder(
    val binding: ActivityMusicSetEditItemBinding,
    val onItemClick: ((MusicSet) -> Unit)?
) : BaseViewHolder(binding.root) {
    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet = (item as ListItem.MusicSetItem).musicSet

        musicSet.bindTitleSubtitleArtwork(
            titleView = binding.musicItemTitle,
            subtitleView = binding.musicItemArtist,
            artworkView = binding.musicItemImage
        )
        binding.musicItemCheckbox.setState(
            if (selected) COUICheckBox.SELECT_ALL else COUICheckBox.SELECT_NONE
        )
        binding.root.setOnClickListener { onItemClick?.invoke(musicSet) }
    }
}
