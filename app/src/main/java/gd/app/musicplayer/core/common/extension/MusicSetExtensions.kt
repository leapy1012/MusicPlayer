package gd.app.musicplayer.core.common.extension

import android.net.Uri
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.isConcreteCollection
import gd.app.musicplayer.domain.model.isTrackCollection
import androidx.core.net.toUri

internal val MusicSet.supportsViewModeMenu: Boolean
    get() = this is MusicSet.Artists || this is MusicSet.Albums || this is MusicSet.Genres

internal val MusicSet.supportsShuffleAllMenu: Boolean
    get() = isTrackCollection

internal val MusicSet.supportsPlayNextMenu: Boolean
    get() = isTrackCollection && this !is MusicSet.Tracks

internal val MusicSet.supportsCompactAlbumHeader: Boolean
    get() = this is MusicSet.Folder || this is MusicSet.Playlist || this is MusicSet.Favorites || this is MusicSet.RecentlyAdded || this is MusicSet.RecentlyPlayed || this is MusicSet.MostPlayed

internal val MusicSet.supportsSortMenu: Boolean
    get() = this !is MusicSet.RecentlyPlayed &&
            this !is MusicSet.MostPlayed

internal val MusicSet.supportsRenameMenu: Boolean
    get() = this is MusicSet.Playlist ||
            this is MusicSet.Artist ||
            this is MusicSet.Album ||
            this is MusicSet.Genre

internal val MusicSet.supportsArtworkMenu: Boolean
    get() = this is MusicSet.Artist ||
            this is MusicSet.Album ||
            this is MusicSet.Genre

internal fun MusicSet.albumArtSource(): String? = when {
    !albumArt.isNullOrEmpty() -> albumArt

    this is MusicSet.Albums ||
            this is MusicSet.Artists ||
            this is MusicSet.Genres -> {
        "content://media/external/audio/albumart".toUri()
            .buildUpon()
            .appendPath(id.toString())
            .build()
            .toString()
    }

    else -> ""
}