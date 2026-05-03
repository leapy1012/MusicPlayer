package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.ActivityHiddenFoldersMusicHeaderBinding

class HiddenMusicHeaderViewHolder(val binding: ActivityHiddenFoldersMusicHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(count: Int) {
        binding.musicItemCount.text = buildString {
            append("(")
            append(count)
            append(")")
        }
    }
}
