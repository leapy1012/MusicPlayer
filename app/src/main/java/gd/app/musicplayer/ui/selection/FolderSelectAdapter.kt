package gd.app.musicplayer.ui.selection

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSelectItemBinding
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder

class FolderSelectAdapter(
    private val accentColor: Int,
    private val onItemClick: (MusicSet.Folder) -> Unit
) : ListAdapter<MusicSet.Folder, FolderSelectAdapter.ViewHolder>(DiffCallback()) {

    private var highlightQuery: String = ""

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ActivityMusicSelectItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, accentColor, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), highlightQuery)
    }

    fun submitFolders(items: List<MusicSet.Folder>, highlightQuery: String) {
        this.highlightQuery = highlightQuery
        submitList(items.toList())
    }

    class ViewHolder(
        private val binding: ActivityMusicSelectItemBinding,
        private val accentColor: Int,
        private val onItemClick: (MusicSet.Folder) -> Unit
    ) : BaseViewHolder(binding.root) {

        fun bind(item: MusicSet.Folder, highlightQuery: String) {
            val context = binding.root.context

            binding.musicItemMenu.visibility = View.GONE
            binding.musicItemSize.text = context.resources.getQuantityString(
                R.plurals.plurals_track,
                item.musicCount,
                item.musicCount
            )
            item.loadArtwork(binding.musicItemImage, R.drawable.main_folder_simple)
            binding.musicItemTitle.text = buildHighlightedText(item.name, highlightQuery, accentColor)
            binding.musicItemArtist.text = buildHighlightedText(item.folderPath, highlightQuery, accentColor)
            binding.root.isActivated = false
            binding.root.isEnabled = true
            binding.root.setOnClickListener { onItemClick(item) }
        }

        override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) = Unit

        private fun buildHighlightedText(
            text: String,
            query: String,
            accentColor: Int
        ): CharSequence {
            if (query.isBlank() || text.isBlank()) return text

            val spannable = SpannableString(text)
            var startIndex = text.indexOf(query, ignoreCase = true)
            while (startIndex >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(accentColor),
                    startIndex,
                    startIndex + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = text.indexOf(
                    query,
                    startIndex = startIndex + query.length,
                    ignoreCase = true
                )
            }
            return spannable
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MusicSet.Folder>() {
        override fun areItemsTheSame(oldItem: MusicSet.Folder, newItem: MusicSet.Folder): Boolean =
            oldItem.folderPath == newItem.folderPath

        override fun areContentsTheSame(oldItem: MusicSet.Folder, newItem: MusicSet.Folder): Boolean =
            oldItem == newItem
    }
}
