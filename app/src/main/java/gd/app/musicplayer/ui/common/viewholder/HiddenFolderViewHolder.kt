package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityHiddenFoldersItemBinding
import gd.app.musicplayer.ui.common.model.loadArtwork

class HiddenFolderViewHolder(
    private val binding: ActivityHiddenFoldersItemBinding,
    private val onRemoveClick: (MusicSet.Folder) -> Unit
) :
    RecyclerView.ViewHolder(binding.root), View.OnClickListener {

    private lateinit var item: MusicSet.Folder

    init {
        binding.musicItemMenu.setOnClickListener(this)
        binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
    }

    fun bind(item: MusicSet.Folder) {
        this.item = item
        binding.musicItemTitle.text = item.name
        binding.musicItemArtist.text = item.folderPath
        item.loadArtwork(binding.musicItemAlbum, R.drawable.main_folder_simple)
    }

    override fun onClick(v: View) {
        onRemoveClick(item)
    }
}
