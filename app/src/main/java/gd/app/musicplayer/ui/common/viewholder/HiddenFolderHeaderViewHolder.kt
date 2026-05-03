package gd.app.musicplayer.ui.common.viewholder

import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.databinding.ActivityHiddenFoldersSetHeaderBinding

class HiddenFolderHeaderViewHolder(val binding: ActivityHiddenFoldersSetHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(count: Int) {
        binding.musicItemCount.text = buildString {
            append("(")
            append(count)
            append(")")
        }
    }
}
