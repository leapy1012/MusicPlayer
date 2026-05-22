package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentFolderListItemBinding
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes
import gd.app.musicplayer.feature.library.folder.isHiddenFoldersEntry

class FolderListMusicSetViewHolder(
    private val binding: FragmentFolderListItemBinding
) : BaseViewHolder(binding.root) {

    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {

        val musicSet: MusicSet = (item as ListItem.MusicSetItem).musicSet

        if (musicSet is MusicSet.Folder) {
            if (musicSet.isHiddenFoldersEntry()) {
                binding.musicItemAlbum.setImageResource(R.drawable.main_hidden_folder_simple)
                binding.musicItemTitle.setText(R.string.hidden_folders)
                binding.musicItemArtist.visibility = View.GONE
                binding.musicItemDes.visibility = View.GONE
            } else {
                musicSet.loadArtwork(binding.musicItemAlbum, musicSet.resolvePlaceholderRes(false))
                binding.musicItemTitle.text = musicSet.name
                binding.musicItemArtist.visibility = View.VISIBLE
                binding.musicItemDes.visibility = View.VISIBLE
                binding.musicItemArtist.text = musicSet.folderPath
                binding.musicItemDes.text = binding.root.resources.getQuantityString(R.plurals.plurals_track, musicSet.musicCount, musicSet.musicCount)
            }
        }
    }
}
