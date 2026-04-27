package gd.app.musicplayer.ui.common.model

import android.widget.ImageView
import com.bumptech.glide.Glide
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity

fun MusicSet.resolvePlaceholderRes(useTextVariant: Boolean): Int {
    if (id == MusicSet.FAVORITES_ID) return R.drawable.main_favourite_simple
    return when (this) {
        is MusicSet.Genre -> if (useTextVariant) R.drawable.th_genres_lang else R.drawable.main_genre_simple
        is MusicSet.Favorites -> R.drawable.main_favourite_simple
        is MusicSet.Folder -> R.drawable.main_folder_simple
        is MusicSet.Album -> if (useTextVariant) R.drawable.th_album_lang else R.drawable.main_album_simple
        is MusicSet.Artist -> if (useTextVariant) R.drawable.th_artist_lang else R.drawable.main_artist_simple
        is MusicSet.RecentlyAdded -> R.drawable.main_recent_add_simple
        is MusicSet.RecentlyPlayed -> R.drawable.main_recent_play_simple
        is MusicSet.Tracks -> R.drawable.default_album_identify
        else -> R.drawable.main_list_simple
    }
}

fun MusicSet.loadArtwork(imageView: ImageView, fallbackResId: Int) {
    val context = imageView.context
    if (context is BaseActivity && context.isDestroyed) return

    val artworkSource = when {
        !albumArt.isNullOrEmpty() -> albumArt
        this is MusicSet.Albums || this is MusicSet.Artists || this is MusicSet.Genres ->
            "content://media/external/audio/albumart/$id"
        else -> ""
    }

    if (artworkSource.isNullOrEmpty() || !artworkSource.endsWith("gif")) {
        Glide.with(context)
            .load(artworkSource)
            .placeholder(fallbackResId)
            .error(fallbackResId)
            .fitCenter()
            .into(imageView)
    } else {
        Glide.with(context)
            .asGif()
            .load(artworkSource)
            .placeholder(fallbackResId)
            .error(fallbackResId)
            .into(imageView)
    }
}
