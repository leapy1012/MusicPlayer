package gd.app.musicplayer.ui.common.viewholder

import android.text.format.Formatter
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.core.extension.loadMusicArtwork
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MusicViewHolder(
    val binding: FragmentMusicListItemBinding,
    private val musicSet: MusicSet,
    val onItemClick: ((Music) -> Unit)?,
    val onItemLongClick: ((Music) -> Unit)?,
    val onMenuClick: ((Music) -> Unit)?
) : BaseViewHolder(binding.root) {

    private var boundMusicId: Long = -1L

    override fun onBind(item: ListItem, selected: Boolean, viewInfo: String) {
        bind(item = item, isCurrentTrack = false, isPlaying = false, viewInfo)
    }

    fun bind(
        item: ListItem,
        isCurrentTrack: Boolean,
        isPlaying: Boolean,
        viewInfo: String
    ) {
        val music = (item as ListItem.MusicItem).music
        boundMusicId = music.id

        music.loadMusicArtwork(binding.musicItemAlbum)
        binding.musicItemTitle.text = music.title
        binding.musicItemArtist.text = music.artist
        bindMetadata(music, isCurrentTrack, isPlaying, viewInfo)
        renderPlaybackState(isCurrentTrack, isPlaying)
        renderTextColors(isCurrentTrack, isPlaying)

        itemView.setOnClickListener { onItemClick?.invoke(music) }
        itemView.setOnLongClickListener {
            onItemLongClick?.invoke(music)
            onItemLongClick != null
        }
        binding.musicItemMenu.setOnClickListener { onMenuClick?.invoke(music) }
    }

    fun updatePlaybackState(musicId: Long, isCurrentTrack: Boolean, isPlaying: Boolean) {
        if (boundMusicId != musicId) return
        bindPlaybackMetadataOnly(isCurrentTrack, isPlaying)
        renderPlaybackState(isCurrentTrack, isPlaying)
        renderTextColors(isCurrentTrack, isPlaying)
    }

    private fun renderPlaybackState(isCurrentTrack: Boolean, isPlaying: Boolean) {
        val shouldShowState = when (musicSet) {
            is MusicSet.MostPlayed -> isCurrentTrack && isPlaying
            else -> isCurrentTrack
        }
        binding.musicItemState.visibility = if (shouldShowState) View.VISIBLE else View.GONE
        binding.musicItemState.setPaused(!isPlaying)
    }

    private fun bindMetadata(music: Music, isCurrentTrack: Boolean, isPlaying: Boolean, viewInfo: String) {
        if (musicSet is MusicSet.RecentlyAdded || viewInfo == VIEW_INFO_DATE) {
            showTrackMetadata(formatAddedDate(music.date))
            binding.musicItemCount.visibility = View.GONE
        }

        else if (musicSet is MusicSet.MostPlayed) {
            binding.musicItemSize.visibility = View.GONE
            binding.musicItemCount.visibility =
                if (isCurrentTrack && isPlaying) View.GONE else View.VISIBLE
            binding.musicItemCount.text = music.playCount.toString()
        }

        else if (viewInfo == VIEW_INFO_SIZE) {
            showTrackMetadata(formatFileSize(music.size))
            binding.musicItemCount.visibility = View.GONE
        } else if (viewInfo == VIEW_INFO_DURATION) {
            showTrackMetadata(formatDuration(music.duration))
            binding.musicItemCount.visibility = View.GONE
        } else {
            binding.musicItemCount.visibility = View.GONE
            binding.musicItemSize.visibility = View.GONE
        }
    }

    private fun showTrackMetadata(value: String) {
        binding.musicItemSize.text = value
        binding.musicItemSize.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
    }

    private fun bindPlaybackMetadataOnly(isCurrentTrack: Boolean, isPlaying: Boolean) {
        if (musicSet is MusicSet.MostPlayed) {
            binding.musicItemCount.visibility =
                if (isCurrentTrack && isPlaying) View.GONE else View.VISIBLE
        }
    }

    private fun renderTextColors(isCurrentTrack: Boolean, isPlaying: Boolean) {
        val context = binding.root.context
        val theme = context.appDependencies.themeRepo
            .getCorePalette(context)
        val isActivePlayingTrack = isCurrentTrack && isPlaying

        binding.musicItemTitle.setTextColor(
            if (isActivePlayingTrack) theme.accentColor else theme.itemTextColor
        )
        binding.musicItemArtist.setTextColor(
            if (isActivePlayingTrack) theme.accentColor
            else ColorUtils.setAlphaComponent(theme.itemTextColor, 180)
        )

        if (binding.musicItemSize.isVisible) {
            binding.musicItemSize.setTextColor(ColorUtils.setAlphaComponent(theme.itemTextColor, 180))
        }
        if (binding.musicItemCount.isVisible) {
            binding.musicItemCount.setTextColor(ColorUtils.setAlphaComponent(theme.itemTextColor, 180))
        }
    }

    private fun formatAddedDate(value: Long?): String {
        if (value == null || value <= 0L) return ""
        val epochMillis = if (value < 1_000_000_000_000L) value * 1000L else value
        return ADDED_DATE_FORMAT.format(Date(epochMillis))
    }

    private fun formatFileSize(value: Long?): String {
        if (value == null || value <= 0L) return ""
        return Formatter.formatShortFileSize(binding.root.context, value)
    }

    private fun formatDuration(durationMs: Int): String {
        if (durationMs <= 0) return ""

        val duration = durationMs.milliseconds
        val totalSeconds = duration.inWholeSeconds
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE

        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
        } else {
            "%d:%02d".format(Locale.getDefault(), minutes, seconds)
        }
    }

    private companion object {
        private const val VIEW_INFO_DATE = "date"
        private const val VIEW_INFO_SIZE = "size"
        private const val VIEW_INFO_DURATION = "duration"
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE

        val ADDED_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
}
