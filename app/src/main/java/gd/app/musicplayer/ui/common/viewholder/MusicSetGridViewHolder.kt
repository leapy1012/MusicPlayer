package gd.app.musicplayer.ui.common.viewholder

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.databinding.FragmentAlbumGridItemBinding
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes

class MusicSetGridViewHolder(
    private val binding: FragmentAlbumGridItemBinding
) : BaseViewHolder(binding.root) {

    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        val musicSet: MusicSet = (item as ListItem.MusicSetItem).musicSet
        binding.musicItemTitle.text = musicSet.name
        musicSet.toDisplayInfo(binding.root.resources)?.let { info ->
            musicSet.loadArtwork(binding.musicItemAlbum, musicSet.resolvePlaceholderRes(false))
            binding.musicItemArtist.text = info.subtitle
        }
    }
}
