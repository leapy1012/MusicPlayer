package gd.app.musicplayer.data.model

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class MusicSet : Parcelable {
    @IgnoredOnParcel
    open val name: String = ""

    @IgnoredOnParcel
    open val albumArt: String? = null

    @IgnoredOnParcel
    open val id: Long = UNKNOWN_ID

    interface BrowseCategory

    interface TrackCollection

    interface ConcreteCollection : TrackCollection

    @Parcelize
    object Tracks : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = TRACKS_ID
    }

    @Parcelize
    object Artists : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = ARTISTS_ID
    }

    @Parcelize
    object Albums : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = ALBUMS_ID
    }

    @Parcelize
    object Genres : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = GENRES_ID
    }

    @Parcelize
    object Folders : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = FOLDERS_ID
    }

    @Parcelize
    object Playlists : MusicSet(), BrowseCategory {
        @IgnoredOnParcel
        override val id: Long = PLAYLISTS_ID
    }

    @Parcelize
    object RecentlyAdded : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = RECENTLY_ADDED_ID
    }

    @Parcelize
    object RecentlyPlayed : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = RECENTLY_PLAYED_ID
    }

    @Parcelize
    object MostPlayed : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = MOST_PLAYED_ID
    }

    object Favorites : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = FAVORITES_ID
    }

    @Parcelize
    object Queue : MusicSet(), TrackCollection {
        @IgnoredOnParcel
        override val id: Long = QUEUE_ID
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
        val setup_time: Long,
        val album_id: Long,
        val s_pic: String
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
        const val TRACKS_ID = -1L
        const val RECENTLY_PLAYED_ID = -2L
        const val RECENTLY_ADDED_ID = -3L
        const val ARTISTS_ID = -4L
        const val ALBUMS_ID = -5L
        const val FOLDERS_ID = -6L
        const val MOST_PLAYED_ID = -7L
        const val GENRES_ID = -8L
        const val PLAYLISTS_ID = -9L
        const val QUEUE_ID = -10L
        const val FAVORITES_ID = 1L
    }
}

val MusicSet.isBrowseCategory: Boolean
    get() = this is MusicSet.BrowseCategory

val MusicSet.isTrackCollection: Boolean
    get() = this is MusicSet.TrackCollection

val MusicSet.isConcreteCollection: Boolean
    get() = this is MusicSet.ConcreteCollection

fun MusicSet.asBrowseCategory(): MusicSet.BrowseCategory? =
    this as? MusicSet.BrowseCategory

fun MusicSet.asTrackCollection(): MusicSet.TrackCollection? =
    this as? MusicSet.TrackCollection
