package gd.app.musicplayer.ui.common.viewholder

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding

class MusicSetListViewHolder(
    val binding: FragmentAlbumListItemBinding
) : BaseViewHolder(binding.root) {

    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet: MusicSet = (item as ListItem.MusicSetItem).musicSet
        binding.musicItemTitle.text = musicSet.name
        musicSet.toDisplayInfo(binding.root.resources)?.let { info ->
            binding.musicItemAlbum.setImageResource(info.iconRes)
            binding.musicItemArtist.text = info.subtitle
        }
    }
}
