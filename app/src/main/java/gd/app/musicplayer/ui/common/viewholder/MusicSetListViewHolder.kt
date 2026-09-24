package gd.app.musicplayer.ui.common.viewholder

import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding

class MusicSetListViewHolder(
    val binding: FragmentAlbumListItemBinding
) : BaseViewHolder(binding.root) {

    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet = (item as ListItem.MusicSetItem).musicSet
        musicSet.bindTitleSubtitleArtwork(
            titleView = binding.musicItemTitle,
            subtitleView = binding.musicItemArtist,
            artworkView = binding.musicItemAlbum
        )
    }
}
