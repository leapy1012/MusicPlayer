package gd.app.musicplayer.feature.search

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.databinding.FragmentSearchHeaderItemBinding
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetListViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicViewHolder
import gd.app.musicplayer.ui.common.view.SelectBox
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.theme.*

class SearchResultAdapter(
    context: Context
) : SectionedSearchAdapter() {

    interface Listener {
        fun onSongClicked(song: Music)
        fun onSongMenuClicked(song: Music)
        fun onMusicSetClicked(musicSet: MusicSet)
    }

    private val inflater = LayoutInflater.from(context)
    private val accentColor = context.appContainer.themeRepo
        .getCorePalette(context)
        .accentColor
    private val sections = mutableListOf<SearchSection>()
    private var listener: Listener? = null
    private var query: String = ""

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setQuery(query: String) {
        this.query = query
        reload()
    }

    fun submitSections(newSections: List<SearchSection>) {
        sections.clear()
        sections.addAll(newSections)
        reload()
    }

    override fun getChildCount(sectionIndex: Int): Int =
        if (sections[sectionIndex].expanded) sections[sectionIndex].items.size else 0

    override fun getSectionCount(): Int = sections.size

    override fun getItemViewType(sectionIndex: Int, childIndex: Int): Int =
        when (sections[sectionIndex].items[childIndex]) {
            is ListItem.MusicItem -> VIEW_TYPE_MUSIC
            is ListItem.MusicSetItem -> VIEW_TYPE_MUSIC_SET
        }

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder =
        when (viewType) {
            VIEW_TYPE_MUSIC -> {
                val binding = FragmentMusicListItemBinding.inflate(inflater, parent, false)
                applyCurrentTheme(binding.root)
                MusicViewHolder(
                    binding = binding,
                    musicSet = MusicSet.Tracks,
                    onItemClick = { song -> listener?.onSongClicked(song) },
                    onItemLongClick = null,
                    onMenuClick = { song -> listener?.onSongMenuClicked(song) }
                )
            }

            VIEW_TYPE_MUSIC_SET -> {
                val binding = FragmentAlbumListItemBinding.inflate(inflater, parent, false)
                applyCurrentTheme(binding.root)
                MusicSetListViewHolder(binding)
            }

            else -> error("Unsupported item view type: $viewType")
        }

    override fun onCreateHeaderViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        SearchHeaderViewHolder(
            FragmentSearchHeaderItemBinding.inflate(inflater, parent, false).also {
                applyCurrentTheme(it.root)
            }
        )

    override fun onBindItemViewHolder(
        holder: RecyclerView.ViewHolder,
        sectionIndex: Int,
        childIndex: Int,
        payloads: List<Any>
    ) {
        val item = sections[sectionIndex].items[childIndex]
        (holder as BaseViewHolder).bind(item)

        when {
            holder is MusicViewHolder && item is ListItem.MusicItem -> {
                holder.binding.musicItemTitle.text = highlight(item.music.title)
                holder.binding.musicItemArtist.text = highlight(item.music.artist)
            }

            holder is MusicSetListViewHolder && item is ListItem.MusicSetItem -> {
                holder.binding.musicItemTitle.text = highlight(item.musicSet.name)
                holder.itemView.setOnClickListener {
                    listener?.onMusicSetClicked(item.musicSet)
                }
            }
        }
    }

    override fun onBindHeaderViewHolder(
        holder: RecyclerView.ViewHolder,
        sectionIndex: Int,
        payloads: List<Any>
    ) {
        (holder as SearchHeaderViewHolder).bind(sections[sectionIndex]) { expanded ->
            updateSectionExpanded(sectionIndex, expanded)
        }
    }

    data class SearchSection(
        @param:StringRes @field:StringRes val titleRes: Int,
        val items: List<ListItem>,
        var expanded: Boolean = true
    )

    private class SearchHeaderViewHolder(
        private val binding: FragmentSearchHeaderItemBinding
    ) : RecyclerView.ViewHolder(binding.root), SelectBox.OnSelectChangedListener {

        private var section: SearchSection? = null
        private var onChanged: ((Boolean) -> Unit)? = null

        init {
            binding.musicItemExpanded.setOnSelectChangedListener(this)
        }

        fun bind(section: SearchSection, onChanged: (Boolean) -> Unit) {
            this.section = section
            this.onChanged = onChanged
            binding.musicItemTitle.setText(section.titleRes)
            binding.musicItemExpanded.isEnabled = section.items.isNotEmpty()
            binding.musicItemExpanded.isSelected = section.expanded
        }

        override fun onSelectChanged(selectBox: SelectBox, fromUser: Boolean, isSelected: Boolean) {
            if (!fromUser) return
            onChanged?.invoke(isSelected)
        }
    }

    private fun updateSectionExpanded(sectionIndex: Int, expanded: Boolean) {
        val section = sections[sectionIndex]
        val previousChildCount = getChildCount(sectionIndex)
        if (section.expanded == expanded) return
        section.expanded = expanded
        val newChildCount = getChildCount(sectionIndex)
        val headerPosition = adapterPositionForSection(sectionIndex)

        clearPositionCache()
        notifyItemChanged(headerPosition, PAYLOAD_HEADER)
        when {
            newChildCount > previousChildCount -> {
                notifyItemRangeInserted(headerPosition + 1, newChildCount - previousChildCount)
            }

            newChildCount < previousChildCount -> {
                notifyItemRangeRemoved(headerPosition + 1, previousChildCount - newChildCount)
            }
        }
    }

    private fun highlight(text: String): CharSequence {
        if (query.isBlank() || text.isBlank()) return text

        val spannable = SpannableString(text)
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var startIndex = lowerText.indexOf(lowerQuery)

        while (startIndex >= 0) {
            val endIndex = startIndex + lowerQuery.length
            spannable.setSpan(
                ForegroundColorSpan(accentColor),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = lowerText.indexOf(lowerQuery, startIndex = endIndex)
        }

        return spannable
    }

    companion object {
        private const val VIEW_TYPE_MUSIC = 100
        private const val VIEW_TYPE_MUSIC_SET = 101
        private const val PAYLOAD_HEADER = "header"
    }
}
