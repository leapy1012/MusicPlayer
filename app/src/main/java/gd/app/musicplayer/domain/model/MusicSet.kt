package gd.app.musicplayer.domain.model

import android.content.Context
import android.os.Parcelable
import androidx.room.ColumnInfo
import gd.app.musicplayer.R
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Represents a browsable library destination or a concrete track collection.
 */
@Parcelize
sealed class MusicSet : Parcelable {

    @IgnoredOnParcel
    open val id: Long = UNKNOWN_ID

    @IgnoredOnParcel
    open val name: String = ""

    @IgnoredOnParcel
    open val albumArt: String? = null

    interface BrowseCategory

    interface TrackCollection

    interface ConcreteCollection : TrackCollection

    @Parcelize
    object Tracks : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = ALL_TRACKS
    }

    @Parcelize
    object Artists : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = ARTISTS
    }

    @Parcelize
    object Albums : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = ALBUMS
    }

    @Parcelize
    object Genres : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = GENRES
    }

    @Parcelize
    object Folders : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = FOLDERS
    }

    @Parcelize
    object Playlists : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = USER_PLAYLIST
    }

    @Parcelize
    object RecentlyAdded : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = RECENT_ADDED
    }

    @Parcelize
    object RecentlyPlayed : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = RECENT_PLAYED
    }

    @Parcelize
    object MostPlayed : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = MOST_PLAYED
    }

    @Parcelize
    object Favorites : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = FAVORITES
    }

    @Parcelize
    object Queue : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = PLAYING_QUEUE
    }

    @Parcelize
    data class Album(
        override val id: Long,
        override val name: String,
        override val albumArt: String? = null,
        val artist: String = "",
        val musicCount: Int,
        val year: Int = 0,
        val date: Long,
        val genres: String = ""
    ) : MusicSet(), ConcreteCollection

    @Parcelize
    data class Artist(
        override val id: Long,
        override val name: String,
        val musicCount: Int,
        val albumCount: Int,
        override val albumArt: String?
    ) : MusicSet(), ConcreteCollection

    @Parcelize
    data class Genre(
        override val id: Long,
        override val name: String,
        override val albumArt: String? = null,
        val musicCount: Int
    ) : MusicSet(), ConcreteCollection

    @Parcelize
    data class Playlist(
        override val id: Long,
        override val name: String,
        override val albumArt: String? = null,
        val musicCount: Int,
        val disabled: Boolean = false,
        val sort: Long,
        @ColumnInfo(name = "setup_time")
        val setupTime: Long,
        @ColumnInfo(name = "album_id")
        val albumId: Long,
        @ColumnInfo(name = "s_pic")
        val sourcePicture: String
    ) : MusicSet(), ConcreteCollection

    @Parcelize
    data class Folder(
        override val id: Long,
        override val name: String,
        val folderPath: String,
        val musicCount: Int,
        override val albumArt: String? = null,
        val date: Long
    ) : MusicSet(), ConcreteCollection

    companion object {
        const val UNKNOWN_ID = -1000L

        const val FAVORITES = 1L
        const val ALL_TRACKS = -1L
        const val RECENT_PLAYED = -2L
        const val RECENT_ADDED = -3L
        const val ARTISTS = -4L
        const val ALBUMS = -5L
        const val FOLDERS = -6L
        const val GENRES = -8L
        const val PLAYING_QUEUE = -9L
        const val MOST_PLAYED = -11L
        const val USER_PLAYLIST = -9L
        const val HIDDEN_FOLDERS = -14L
        const val DELETED_TRACKS = -15L
        const val HIDDEN_TRACKS = -18L

        @Deprecated(
            message = "Typo kept for source compatibility. Use DELETED_TRACKS.",
            replaceWith = ReplaceWith("DELETED_TRACKS")
        )
        const val DELECTED_TRACKS = DELETED_TRACKS
    }
}

val MusicSet.isBrowseCategory: Boolean
    get() = this is MusicSet.BrowseCategory

val MusicSet.isTrackCollection: Boolean
    get() = this is MusicSet.TrackCollection

val MusicSet.isConcreteCollection: Boolean
    get() = this is MusicSet.ConcreteCollection

val MusicSet.isUserPlaylist: Boolean
    get() = this is MusicSet.Playlist && id != MusicSet.FAVORITES

fun MusicSet.asBrowseCategory(): MusicSet.BrowseCategory? {
    return this as? MusicSet.BrowseCategory
}

fun MusicSet.asTrackCollection(): MusicSet.TrackCollection? {
    return this as? MusicSet.TrackCollection
}

fun MusicSet.displayName(): String {
    return when (this) {
        is MusicSet.Folder -> name.ifBlank { folderPath.substringAfterLast('/') }
        else -> name
    }
}

fun MusicSet.displayName(context: Context): String {
    return when (this) {
        is MusicSet.Tracks -> context.getString(R.string.all_songs)
        is MusicSet.Favorites -> context.getString(R.string.favorite)
        is MusicSet.RecentlyAdded -> context.getString(R.string.recently_added)
        is MusicSet.RecentlyPlayed -> context.getString(R.string.recently_played)
        is MusicSet.MostPlayed -> context.getString(R.string.mostly_played)
        else -> displayName().ifBlank { context.getString(R.string.music_player) }
    }
}
