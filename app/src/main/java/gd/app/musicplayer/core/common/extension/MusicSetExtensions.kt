package gd.app.musicplayer.core.common.extension

import gd.app.musicplayer.domain.model.MusicSet
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

internal val MusicSet.supportsManualOrdering: Boolean
    get() =
        id > 0L && (this is MusicSet.Playlist || this is MusicSet.Favorites)

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

internal val MusicSet.stableId: String
    get() =
        when (this) {
            is MusicSet.Artist -> "artist:${id}"
            is MusicSet.Album -> "album:${id}"
            is MusicSet.Genre -> "genre:${id}:${name}"
            is MusicSet.Folder -> "folder:${folderPath}"
            is MusicSet.Playlist -> "playlist:${id}"
            is MusicSet.Tracks -> "tracks:${id}"
            is MusicSet.Artists -> "artists:${id}"
            is MusicSet.Albums -> "albums:${id}"
            is MusicSet.Genres -> "genres:${id}"
            is MusicSet.Folders -> "folders:${id}"
            is MusicSet.Playlists -> "playlists"
            is MusicSet.RecentlyAdded -> "recently_added:${id}"
            is MusicSet.RecentlyPlayed -> "recently_played:${id}"
            is MusicSet.MostPlayed -> "most_played"
            is MusicSet.Favorites -> "favorites"
            is MusicSet.Queue -> "queue"
        }