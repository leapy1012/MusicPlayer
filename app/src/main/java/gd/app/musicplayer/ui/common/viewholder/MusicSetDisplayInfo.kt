package gd.app.musicplayer.ui.common.viewholder

import android.content.res.Resources
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes

internal data class MusicSetDisplayInfo(
    val iconRes: Int,
    val subtitle: String
)

internal fun MusicSet.bindTitleSubtitleArtwork(
    titleView: TextView,
    subtitleView: TextView,
    artworkView: ImageView
) {
    titleView.text = name
    toDisplayInfo(titleView.resources)?.let { info ->
        loadArtwork(artworkView, resolvePlaceholderRes(false))
        subtitleView.text = info.subtitle
    }
}

internal fun ImageView.bindMultiSelectCheckbox(selected: Boolean, accentColor: Int) {
    setImageResource(
        if (selected) R.drawable.vector_multi_checked else R.drawable.vector_multi_unchecked
    )
    setColorFilter(
        if (selected) {
            accentColor
        } else {
            ContextCompat.getColor(context, R.color.white)
        }
    )
}

internal fun MusicSet.toDisplayInfo(resources: Resources): MusicSetDisplayInfo? {
    return when (this) {
        is MusicSet.Artist -> MusicSetDisplayInfo(
            iconRes = this.resolvePlaceholderRes(false),
            subtitle = buildString {
                append(resources.getQuantityString(R.plurals.plurals_album, albumCount, albumCount))
                append(" | ")
                append(resources.getQuantityString(R.plurals.plurals_track, musicCount, musicCount))
            }
        )

        is MusicSet.Album -> MusicSetDisplayInfo(
            iconRes = this.resolvePlaceholderRes(false),
            subtitle = buildString {
                append(artist)
                append(" | ")
                append(resources.getQuantityString(R.plurals.plurals_track, musicCount, musicCount))
            }
        )

        is MusicSet.Genre -> MusicSetDisplayInfo(
            iconRes = this.resolvePlaceholderRes(false),
            subtitle = resources.getQuantityString(R.plurals.plurals_track, musicCount, musicCount)
        )

        is MusicSet.Playlist -> MusicSetDisplayInfo(
            iconRes = this.resolvePlaceholderRes(false),
            subtitle = resources.getQuantityString(R.plurals.plurals_track, musicCount, musicCount)
        )

        is MusicSet.Folder -> MusicSetDisplayInfo(
            iconRes = this.resolvePlaceholderRes(false),
            subtitle = resources.getQuantityString(R.plurals.plurals_track, musicCount, musicCount)
        )

        else -> null
    }
}
