package gd.app.musicplayer.ui.common.viewholder

import android.annotation.SuppressLint
import android.view.View
import androidx.core.graphics.ColorUtils
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.ListItem
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.loadMusicArtwork
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.isVisible
import java.util.Locale.getDefault

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
        boundMusicId = music._id

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
        if (musicSet is MusicSet.RecentlyAdded || viewInfo == "date") {
            val formattedDate = formatMonthDay(music.date)
            binding.musicItemSize.text = formattedDate
            binding.musicItemSize.visibility = if (formattedDate.isBlank()) View.GONE else View.VISIBLE
            binding.musicItemCount.visibility = View.GONE
        }

        else if (musicSet is MusicSet.MostPlayed) {
            binding.musicItemSize.visibility = View.GONE
            binding.musicItemCount.visibility =
                if (isCurrentTrack && isPlaying) View.GONE else View.VISIBLE
            binding.musicItemCount.text = music.playCount.toString()
        }

        else if (viewInfo == "size") {
            binding.musicItemSize.text = music.size.toString()
            binding.musicItemSize.visibility = View.VISIBLE
            binding.musicItemCount.visibility = View.GONE
        } else if (viewInfo == "duration") {
            binding.musicItemSize.text = music.duration.toString()
            binding.musicItemSize.visibility = View.VISIBLE
            binding.musicItemCount.visibility = View.GONE
        } else {
            binding.musicItemCount.visibility = View.GONE
            binding.musicItemCount.visibility = View.GONE
        }
    }

    private fun bindPlaybackMetadataOnly(isCurrentTrack: Boolean, isPlaying: Boolean) {
        if (musicSet is MusicSet.MostPlayed) {
            binding.musicItemCount.visibility =
                if (isCurrentTrack && isPlaying) View.GONE else View.VISIBLE
        }
    }

    private fun renderTextColors(isCurrentTrack: Boolean, isPlaying: Boolean) {
        val context = binding.root.context
        val theme = context.appContainer.themeRepo
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

    private fun formatMonthDay(value: Long?): String {
        if (value == null || value <= 0L) return ""
        val epochMillis = if (value < 1_000_000_000_000L) value * 1000L else value
        return MONTH_DAY_FORMAT.format(Date(epochMillis))
    }

    private companion object {
        val MONTH_DAY_FORMAT = SimpleDateFormat("MM-dd", Locale.getDefault())
    }
}
