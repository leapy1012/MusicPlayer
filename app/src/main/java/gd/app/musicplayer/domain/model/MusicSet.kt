package gd.app.musicplayer.domain.model

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

        // -12L (alternate queue/search queue bucket (handled same as playlist queue in SQL branch)
        // -13L: rated songs
        // -16L: raw musictbl mode (special internal set)
        const val HIDDEN_FOLDERS = -14L
        const val DELECTED_TRACKS = -15L
        const val HIDDEN_TRACKS = -18L
        const val USER_PLAYLIST = -9L

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
