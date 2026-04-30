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
import gd.app.musicplayer.core.extension.formatAddedDate
import gd.app.musicplayer.core.extension.formatDuration
import gd.app.musicplayer.core.extension.formatFileSize
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
        renderTextColors(isCurrentTrack)

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
        renderTextColors(isCurrentTrack)
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
            showTrackMetadata(music.formatAddedDate())
            binding.musicItemCount.visibility = View.GONE
        }

        else if (musicSet is MusicSet.MostPlayed) {
            binding.musicItemSize.visibility = View.GONE
            binding.musicItemCount.visibility =
                if (isCurrentTrack && isPlaying) View.GONE else View.VISIBLE
            binding.musicItemCount.text = music.playCount.toString()
        }

        else if (viewInfo == VIEW_INFO_SIZE) {
            showTrackMetadata(music.formatFileSize(binding.root.context))
            binding.musicItemCount.visibility = View.GONE
        } else if (viewInfo == VIEW_INFO_DURATION) {
            showTrackMetadata(music.formatDuration())
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

    private fun renderTextColors(isCurrentTrack: Boolean) {
        val context = binding.root.context
        val theme = context.appDependencies.themeRepo
            .getCorePalette(context)

        binding.musicItemTitle.setTextColor(
            if (isCurrentTrack) theme.accentColor else theme.itemTextColor
        )
        binding.musicItemArtist.setTextColor(
            if (isCurrentTrack) theme.accentColor
            else ColorUtils.setAlphaComponent(theme.itemTextColor, 180)
        )

        binding.musicItemCount.setTextColor(
            if (isCurrentTrack) theme.accentColor
            else ColorUtils.setAlphaComponent(theme.itemTextColor, 180)
        )

        binding.musicItemSize.setTextColor(
            if (isCurrentTrack) theme.accentColor
            else ColorUtils.setAlphaComponent(theme.itemTextColor, 180)
        )
    }

    private companion object {
        private const val VIEW_INFO_DATE = "date"
        private const val VIEW_INFO_SIZE = "size"
        private const val VIEW_INFO_DURATION = "duration"
    }
}
