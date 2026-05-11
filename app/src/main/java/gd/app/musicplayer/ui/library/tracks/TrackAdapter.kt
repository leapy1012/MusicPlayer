package gd.app.musicplayer.ui.library.tracks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.ui.common.viewholder.MusicViewHolder

class TrackAdapter(
    private val musicSet: MusicSet,
    private val theme: ThemePalette,
    private val onItemClick: (Music) -> Unit,
    private val onMenuClick: (Music) -> Unit,
    private val onItemLongClick: (Music) -> Unit
) : ListAdapter<Music, MusicViewHolder>(MusicDiffCallback) {

    private var currentTrackId: Long = NO_TRACK_ID
    private var isCurrentTrackPlaying: Boolean = false
    private var metadataDisplayMode: String = ""
    private var positionByTrackId: Map<Long, Int> = emptyMap()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MusicViewHolder {
        val binding = FragmentMusicListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        binding.root.applyRoundedOutline(R.dimen.item_image_corner_radius)

        return MusicViewHolder(
            binding = binding,
            musicSet = musicSet,
            theme = theme,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onMenuClick = onMenuClick
        )
    }

    override fun onBindViewHolder(
        holder: MusicViewHolder,
        position: Int
    ) {
        val music = getItem(position)

        holder.bind(
            item = ListItem.MusicItem(music),
            isCurrentTrack = music.id == currentTrackId,
            isPlaying = music.id == currentTrackId && isCurrentTrackPlaying,
            viewInfo = metadataDisplayMode
        )
    }

    override fun onBindViewHolder(
        holder: MusicViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val music = getItem(position)

        if (payloads.contains(PAYLOAD_METADATA_DISPLAY_MODE)) {
            holder.updateMetadataDisplay(
                music = music,
                viewInfo = metadataDisplayMode,
                isCurrentTrack = music.id == currentTrackId,
                isPlaying = music.id == currentTrackId && isCurrentTrackPlaying
            )
        }

        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            bindPlaybackStatePayload(
                holder = holder,
                music = music
            )
        }
    }

    override fun onCurrentListChanged(
        previousList: MutableList<Music>,
        currentList: MutableList<Music>
    ) {
        positionByTrackId = currentList
            .mapIndexed { index, music ->
                music.id to index
            }
            .toMap()
    }

    fun submitPlaybackHighlight(
        currentTrackId: Long?,
        isPlaying: Boolean
    ) {
        val previousTrackId = this.currentTrackId
        val nextTrackId = currentTrackId ?: NO_TRACK_ID

        val hasChanged =
            previousTrackId != nextTrackId ||
                    isCurrentTrackPlaying != isPlaying

        if (!hasChanged) return

        this.currentTrackId = nextTrackId
        this.isCurrentTrackPlaying = isPlaying

        notifyTrackPlaybackChanged(previousTrackId)
        notifyTrackPlaybackChanged(nextTrackId)
    }

    fun setMetadataDisplayMode(mode: String) {
        val normalizedMode = mode
            .takeIf { value -> value in METADATA_DISPLAY_MODES }
            .orEmpty()

        if (metadataDisplayMode == normalizedMode) return

        metadataDisplayMode = normalizedMode

        if (itemCount > 0) {
            notifyItemRangeChanged(
                0,
                itemCount,
                PAYLOAD_METADATA_DISPLAY_MODE
            )
        }
    }

    private fun bindPlaybackStatePayload(
        holder: MusicViewHolder,
        music: Music
    ) {
        holder.updatePlaybackState(
            musicId = music.id,
            isCurrentTrack = music.id == currentTrackId,
            isPlaying = music.id == currentTrackId && isCurrentTrackPlaying
        )
    }

    private fun notifyTrackPlaybackChanged(trackId: Long) {
        if (trackId == NO_TRACK_ID) return

        val position = positionByTrackId[trackId]
            ?: currentList.indexOfFirst { music ->
                music.id == trackId
            }

        if (position != RecyclerView.NO_POSITION && position >= 0) {
            notifyItemChanged(
                position,
                PAYLOAD_PLAYBACK_STATE
            )
        }
    }

    private companion object {
        private const val NO_TRACK_ID = -1L
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playback_state"
        private const val PAYLOAD_METADATA_DISPLAY_MODE = "payload_metadata_display_mode"

        private val METADATA_DISPLAY_MODES = setOf(
            "date",
            "size",
            "duration"
        )
    }
}

private object MusicDiffCallback : DiffUtil.ItemCallback<Music>() {

    override fun areItemsTheSame(
        oldItem: Music,
        newItem: Music
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: Music,
        newItem: Music
    ): Boolean {
        return oldItem == newItem
    }
}