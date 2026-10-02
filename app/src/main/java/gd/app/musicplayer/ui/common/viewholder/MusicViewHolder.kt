package gd.app.musicplayer.ui.common.viewholder

import android.view.View
import androidx.core.graphics.ColorUtils
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.formatAddedDate
import gd.app.musicplayer.core.common.extension.formatFileSize
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.itemTextColor
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentMusicListItemBinding
import gd.app.musicplayer.ui.selection.ItemTouchStateListener

class MusicViewHolder(
    val binding: FragmentMusicListItemBinding,
    private val musicSet: MusicSet,
    private val theme: ThemePalette,
    val onItemClick: ((Music) -> Unit)?,
    /** Second arg is itemView.top — same contract as original l5.m0 → ActivityEdit.n1(topOffset). */
    val onItemLongClick: ((Music, Int) -> Unit)?,
    val onMenuClick: ((Music) -> Unit)?
) : BaseViewHolder(binding.root), ItemTouchStateListener {

    private var boundMusicId: Long = -1L

    override fun onBind(
        item: ListItem,
        selected: Boolean,
        viewInfo: String
    ) {
        bind(
            item = item,
            isCurrentTrack = false,
            isPlaying = false,
            viewInfo = viewInfo
        )
    }

    fun bind(
        item: ListItem,
        isCurrentTrack: Boolean,
        isPlaying: Boolean,
        viewInfo: String
    ) {
        val music = (item as ListItem.MusicItem).music

        boundMusicId = music.id

        binding.musicItemAlbum.loadMusicArtwork(music.albumArtSource())

        binding.musicItemTitle.text = music.title
        binding.musicItemArtist.text = music.artist

        bindMetadata(
            music = music,
            isCurrentTrack = isCurrentTrack,
            viewInfo = viewInfo
        )

        renderPlaybackState(
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying
        )

        renderTextColors(isCurrentTrack)

        itemView.setOnClickListener {
            onItemClick?.invoke(music)
        }

        if (onItemLongClick != null) {
            itemView.setOnLongClickListener { view ->
                onItemLongClick.invoke(music, view.top)
                true
            }
        } else {
            itemView.setOnLongClickListener(null)
        }

        binding.musicItemMenu.setOnClickListener {
            onMenuClick?.invoke(music)
        }
    }

    fun updatePlaybackState(
        musicId: Long,
        isCurrentTrack: Boolean,
        isPlaying: Boolean
    ) {
        if (boundMusicId != musicId) return

        bindPlaybackMetadataOnly(
            isCurrentTrack = isCurrentTrack
        )

        renderPlaybackState(
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying
        )

        renderTextColors(isCurrentTrack)
    }

    fun updateMetadataDisplay(
        music: Music,
        viewInfo: String,
        isCurrentTrack: Boolean,
        isPlaying: Boolean
    ) {
        if (boundMusicId != music.id) return

        bindMetadata(
            music = music,
            isCurrentTrack = isCurrentTrack,
            viewInfo = viewInfo
        )

        renderTextColors(isCurrentTrack)
    }

    private fun renderPlaybackState(
        isCurrentTrack: Boolean,
        isPlaying: Boolean
    ) {

        binding.musicItemState.setColor(playingHighlightColor())
        binding.musicItemState.visibility =
            if (isCurrentTrack) View.VISIBLE else View.GONE

        binding.musicItemState.setPaused(!isPlaying)
    }

    private fun bindMetadata(
        music: Music,
        isCurrentTrack: Boolean,
        viewInfo: String
    ) {
        binding.musicItemSize.visibility = View.GONE
        binding.musicItemCount.visibility = View.GONE

        when {
            musicSet is MusicSet.RecentlyAdded || viewInfo == VIEW_INFO_DATE -> {
                showTrackMetadata(music.formatAddedDate())
            }

            musicSet is MusicSet.MostPlayed -> {
                binding.musicItemCount.visibility =
                    if (isCurrentTrack) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }

                binding.musicItemCount.text = music.playCount.toString()
            }

            viewInfo == VIEW_INFO_SIZE -> {
                showTrackMetadata(
                    music.formatFileSize(binding.root.context)
                )
            }

            viewInfo == VIEW_INFO_DURATION -> {
                showTrackMetadata(music.durationMs.toDurationString())
            }

            else -> Unit
        }
    }

    private fun showTrackMetadata(value: String) {
        binding.musicItemSize.text = value
        binding.musicItemSize.visibility =
            if (value.isBlank()) View.GONE else View.VISIBLE
    }

    private fun bindPlaybackMetadataOnly(
        isCurrentTrack: Boolean
    ) {
        if (musicSet is MusicSet.MostPlayed) {
            binding.musicItemCount.visibility =
                if (isCurrentTrack) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }
    }

    private fun renderTextColors(isCurrentTrack: Boolean) {
        val highlight = playingHighlightColor()
        val primaryTextColor =
            if (isCurrentTrack) {
                highlight
            } else {
                theme.itemTextColor
            }

        val secondaryTextColor =
            if (isCurrentTrack) {
                highlight
            } else {
                ColorUtils.setAlphaComponent(
                    theme.itemTextColor,
                    SECONDARY_TEXT_ALPHA
                )
            }

        binding.musicItemTitle.setTextColor(primaryTextColor)
        binding.musicItemArtist.setTextColor(secondaryTextColor)
        binding.musicItemCount.setTextColor(secondaryTextColor)
        binding.musicItemSize.setTextColor(secondaryTextColor)
    }

    /** White theme: hard COUI label/theme blue. Picture/Night: palette accent. */
    private fun playingHighlightColor(): Int {
        if (theme.getThemeType() != ThemeManager.THEME_TYPE_LIGHT) {
            return theme.accentColor
        }
        val typed = itemView.context.obtainStyledAttributes(
            intArrayOf(com.coui.appcompat.R.attr.couiColorLabelTheme)
        )
        val color = typed.getColor(0, theme.accentColor)
        typed.recycle()
        return color
    }

    override fun onItemSelected() {
        itemView.alpha = DRAG_ALPHA
    }

    override fun onItemCleared() {
        itemView.alpha = DRAG_ALPHA_FULL
    }

    private companion object {
        private const val VIEW_INFO_DATE = "date"
        private const val VIEW_INFO_SIZE = "size"
        private const val VIEW_INFO_DURATION = "duration"

        private const val SECONDARY_TEXT_ALPHA = 180
        private const val DRAG_ALPHA = 0.8f
        private const val DRAG_ALPHA_FULL = 1f
    }
}
