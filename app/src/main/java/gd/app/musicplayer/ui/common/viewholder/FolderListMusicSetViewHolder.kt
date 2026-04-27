package gd.app.musicplayer.ui.common.viewholder

import android.content.res.Resources
import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentFolderListItemBinding
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes
import gd.app.musicplayer.ui.folder.isHiddenFoldersEntry

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
                binding.musicItemAlbum.setImageResource(musicSet.resolvePlaceholderRes(false))
                binding.musicItemTitle.text = musicSet.name
                binding.musicItemArtist.visibility = View.VISIBLE
                binding.musicItemDes.visibility = View.VISIBLE
                binding.musicItemArtist.text = musicSet.folderPath
                binding.musicItemDes.text = binding.root.resources.getQuantityString(R.plurals.plurals_track, musicSet.musicCount, musicSet.musicCount)
            }
        }
    }
}
