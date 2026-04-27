package gd.app.musicplayer.feature.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.ui.common.viewholder.BaseViewHolder
import gd.app.musicplayer.ui.common.viewholder.MusicViewHolder
import gd.app.musicplayer.feature.theme.applyCurrentTheme

class MusicAdapter(
    private val musicSet: MusicSet,
    private val onItemClick: ((Music) -> Unit)? = null,
    private val onMenuClick: ((Music) -> Unit)? = null,
    private val onItemLongClick: ((Music) -> Unit)? = null
) : ListAdapter<Music, MusicViewHolder>(DiffCallback()) {

    private var currentTrackId: Long = -1L
    private var isCurrentTrackPlaying: Boolean = false

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position)._id

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MusicViewHolder {
        val binding = FragmentMusicListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyCurrentTheme(binding.root)
        return MusicViewHolder(binding, musicSet, onItemClick, onItemLongClick, onMenuClick)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = getItem(position)
        holder.bind(
            item = ListItem.MusicItem(music),
            isCurrentTrack = music._id == currentTrackId,
            isPlaying = music._id == currentTrackId && isCurrentTrackPlaying,
            viewInfo = "date"
        )
    }

    override fun onBindViewHolder(
        holder: MusicViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            val music = getItem(position)
            holder.updatePlaybackState(
                musicId = music._id,
                isCurrentTrack = music._id == currentTrackId,
                isPlaying = music._id == currentTrackId && isCurrentTrackPlaying
            )
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun updatePlaybackState(currentTrackId: Long?, isPlaying: Boolean) {
        val previousTrackId = this.currentTrackId
        val nextTrackId = currentTrackId ?: -1L
        val stateChanged =
            previousTrackId != nextTrackId || this.isCurrentTrackPlaying != isPlaying
        if (!stateChanged) return

        this.currentTrackId = nextTrackId
        this.isCurrentTrackPlaying = isPlaying

        // Only invalidate the rows whose playback badge actually changed. Rebinding the whole
        // list on every playback-state emission would make scrolling and animations stutter.
        notifyTrackPlaybackChanged(previousTrackId)
        notifyTrackPlaybackChanged(nextTrackId)
    }

    private fun notifyTrackPlaybackChanged(trackId: Long) {
        if (trackId < 0L) return
        val position = currentList.indexOfFirst { it._id == trackId }
        if (position >= 0) {
            notifyItemChanged(position, PAYLOAD_PLAYBACK_STATE)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Music>() {
        override fun areItemsTheSame(oldItem: Music, newItem: Music): Boolean {
            return oldItem._id == newItem._id
        }

        override fun areContentsTheSame(oldItem: Music, newItem: Music): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        const val PAYLOAD_PLAYBACK_STATE = "playback_state"
    }
}
