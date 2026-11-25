package gd.app.musicplayer.feature.search

import android.content.Context
import android.text.SpannableString
import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.databinding.FragmentSearchHeaderItemBinding
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicSetListViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicViewHolder
import gd.app.musicplayer.core.common.extension.installCouiPressFeedback

class SearchResultAdapter(
    context: Context,
    private val theme: ThemePalette,
    /**
     * Original `i7.c`'s `z10`: the combined "All" tab caps each section and draws its header;
     * the per-type tabs show the full list behind a zero-height header.
     */
    private val combined: Boolean
) : SectionedSearchAdapter() {

    interface Listener {
        fun onSongClicked(song: Music, preferredIndex: Int?)
        fun onSongMenuClicked(song: Music)
        fun onMusicSetClicked(musicSet: MusicSet)

        fun onMusicSetMenuClicked(musicSet: MusicSet)

        /** Original `i7.c.InterfaceC0186c.x`: switch to the tab owning this section. */
        fun onSeeAllClicked(@StringRes sectionTitleRes: Int)
    }

    private val inflater = LayoutInflater.from(context)
    private val accentColor = if (theme.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
        val typed = context.obtainStyledAttributes(
            intArrayOf(com.coui.appcompat.R.attr.couiColorLabelTheme)
        )
        typed.getColor(0, theme.accentColor).also { typed.recycle() }
    } else {
        theme.accentColor
    }
    private val sections = mutableListOf<SearchSection>()
    private var listener: Listener? = null
    private var query: String = ""
    private var currentTrackId: Long = NO_TRACK_ID
    private var isCurrentTrackPlaying: Boolean = false

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /** Original `i7.c.u`: results and the term they are highlighted against arrive together. */
    fun submitSections(newSections: List<SearchSection>, query: String) {
        val normalizedQuery = query.trim().lowercase()
        val sameContent = sections == newSections
        if (sameContent && this.query == normalizedQuery) return
        val previousQuery = this.query
        this.query = normalizedQuery
        if (sameContent) {
            // The rows and artwork are unchanged; only the match spans need updating.
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_QUERY)
            return
        }
        val oldRows = rows(sections)
        val newRows = rows(newSections)
        val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldRows.size
            override fun getNewListSize() = newRows.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val old = oldRows[oldPosition]
                val new = newRows[newPosition]
                if (old.section != new.section) return false
                return when {
                    old.item == null || new.item == null -> old.item == new.item
                    old.item is ListItem.MusicItem && new.item is ListItem.MusicItem ->
                        old.item.music.id == new.item.music.id
                    old.item is ListItem.MusicSetItem && new.item is ListItem.MusicSetItem ->
                        old.item.musicSet.javaClass == new.item.musicSet.javaClass &&
                            old.item.musicSet.id == new.item.musicSet.id
                    else -> false
                }
            }
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                oldRows[oldPosition] == newRows[newPosition] &&
                    (newRows[newPosition].item == null || previousQuery == normalizedQuery)

            override fun getChangePayload(oldPosition: Int, newPosition: Int): Any? =
                if (oldRows[oldPosition] == newRows[newPosition]) PAYLOAD_QUERY else null
        }, false)
        sections.clear()
        sections.addAll(newSections)
        clearPositionCache()
        // A category changing below the visible songs must not reload those songs' artwork.
        changes.dispatchUpdatesTo(this)
    }

    private data class Row(val section: Int, val item: ListItem?)

    private fun rows(sections: List<SearchSection>): List<Row> = buildList {
        sections.forEach { section ->
            add(Row(section.titleRes, null))
            val count = if (combined) minOf(section.items.size, section.previewLimit) else section.items.size
            repeat(count) { add(Row(section.titleRes, section.items[it])) }
        }
    }

    /** Original `i7.c.a.g`: the song row shows the play-state indicator for the current track. */
    fun submitPlaybackHighlight(currentTrackId: Long?, isPlaying: Boolean) {
        val nextTrackId = currentTrackId ?: NO_TRACK_ID
        if (this.currentTrackId == nextTrackId && isCurrentTrackPlaying == isPlaying) return

        this.currentTrackId = nextTrackId
        this.isCurrentTrackPlaying = isPlaying

        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_PLAYBACK_STATE)
        }
    }

    /** Original `i7.c.g`: songs cap at 5 and everything else at 3, but only on the combined tab. */
    override fun getChildCount(sectionIndex: Int): Int {
        val section = sections[sectionIndex]
        return if (combined) minOf(section.items.size, section.previewLimit) else section.items.size
    }

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
                MusicViewHolder(
                    binding = binding,
                    musicSet = MusicSet.Tracks,
                    theme = theme,
                    onItemClick = { song -> listener?.onSongClicked(song, null) },
                    onItemLongClick = null,
                    onMenuClick = { song -> listener?.onSongMenuClicked(song) }
                )
            }

            VIEW_TYPE_MUSIC_SET -> {
                val binding = FragmentAlbumListItemBinding.inflate(inflater, parent, false)
                MusicSetListViewHolder(binding)
            }

            else -> error("Unsupported item view type: $viewType")
        }

    override fun onCreateHeaderViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        SearchHeaderViewHolder(
            binding = FragmentSearchHeaderItemBinding.inflate(inflater, parent, false),
            combined = combined
        )

    override fun onBindItemViewHolder(
        holder: RecyclerView.ViewHolder,
        sectionIndex: Int,
        childIndex: Int,
        payloads: List<Any>
    ) {
        val item = sections[sectionIndex].items[childIndex]

        when {
            holder is MusicViewHolder && item is ListItem.MusicItem -> {
                val isCurrentTrack = item.music.id == currentTrackId

                if (payloads.contains(PAYLOAD_QUERY)) {
                    updateHighlight(holder.binding.musicItemTitle, item.music.title)
                    updateHighlight(holder.binding.musicItemArtist, item.music.artist)
                    if (payloads.all { it == PAYLOAD_QUERY }) return
                }

                if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
                    holder.updatePlaybackState(
                        musicId = item.music.id,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isCurrentTrack && isCurrentTrackPlaying
                    )
                    return
                }

                holder.bind(
                    item = item,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isCurrentTrack && isCurrentTrackPlaying,
                    viewInfo = "",
                    titleText = highlight(item.music.title),
                    artistText = highlight(item.music.artist)
                )
                holder.itemView.setOnClickListener {
                    // Its position can change without rebinding when other results disappear.
                    listener?.onSongClicked(item.music, null)
                }
            }

            holder is MusicSetListViewHolder && item is ListItem.MusicSetItem -> {
                if (payloads.contains(PAYLOAD_QUERY)) {
                    updateHighlight(holder.binding.musicItemTitle, item.musicSet.name)
                    if (payloads.all { it == PAYLOAD_QUERY }) return
                }
                if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) return

                holder.bind(item)
                updateHighlight(holder.binding.musicItemTitle, item.musicSet.name)
                holder.itemView.setOnClickListener {
                    listener?.onMusicSetClicked(item.musicSet)
                }
                holder.binding.musicItemMenu.setOnClickListener {
                    listener?.onMusicSetMenuClicked(item.musicSet)
                }
            }
        }
    }

    override fun onBindHeaderViewHolder(
        holder: RecyclerView.ViewHolder,
        sectionIndex: Int,
        payloads: List<Any>
    ) {
        if (payloads.isNotEmpty()) return
        val section = sections[sectionIndex]
        (holder as SearchHeaderViewHolder).bind(
            section = section,
            onSeeAllClicked = { listener?.onSeeAllClicked(section.titleRes) }
        )
    }

    /** Original `i7.d`: one result type, with the cap the combined view applies to it. */
    data class SearchSection(
        @param:StringRes @field:StringRes val titleRes: Int,
        val items: List<ListItem>
    ) {
        val previewLimit: Int
            get() = if (titleRes == R.string.songs) SONG_SECTION_LIMIT else OTHER_SECTION_LIMIT
    }

    private class SearchHeaderViewHolder(
        private val binding: FragmentSearchHeaderItemBinding,
        private val combined: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        private var onSeeAllClicked: (() -> Unit)? = null

        init {
            // Original i7.c.b: the per-type tabs collapse the header row to zero height.
            if (!combined) {
                binding.root.updateLayoutParams { height = 0 }
            }
            binding.seeAllLayout.installCouiPressFeedback()
            binding.seeAllLayout.setOnClickListener {
                onSeeAllClicked?.invoke()
            }
        }

        fun bind(
            section: SearchSection,
            onSeeAllClicked: () -> Unit
        ) {
            this.onSeeAllClicked = onSeeAllClicked
            binding.musicItemTitle.setText(section.titleRes)
            binding.root.isEnabled = section.items.isNotEmpty()
            binding.seeAllLayout.isVisible = combined
        }
    }

    private fun updateHighlight(view: TextView, text: String) {
        val existing = view.text as? Spannable
        if (existing != null && existing.toString() == text) {
            existing.getSpans(0, existing.length, ForegroundColorSpan::class.java)
                .forEach(existing::removeSpan)
            applyHighlight(existing, text)
        } else {
            view.setText(highlight(text), TextView.BufferType.SPANNABLE)
        }
    }

    private fun highlight(text: String): CharSequence {
        if (query.isBlank() || text.isBlank()) return text

        val spannable = SpannableString(text)
        applyHighlight(spannable, text)
        return spannable
    }

    private fun applyHighlight(spannable: Spannable, text: String) {
        if (query.isBlank() || text.isBlank()) return
        val lowerText = text.lowercase()
        var startIndex = lowerText.indexOf(query)

        while (startIndex >= 0) {
            val endIndex = startIndex + query.length
            spannable.setSpan(
                ForegroundColorSpan(accentColor),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = lowerText.indexOf(query, startIndex = endIndex)
        }
    }

    companion object {
        private const val VIEW_TYPE_MUSIC = 100
        private const val VIEW_TYPE_MUSIC_SET = 101
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playback_state"
        private const val PAYLOAD_QUERY = "payload_query"
        private const val NO_TRACK_ID = -1L

        private const val SONG_SECTION_LIMIT = 5
        private const val OTHER_SECTION_LIMIT = 3
    }
}
