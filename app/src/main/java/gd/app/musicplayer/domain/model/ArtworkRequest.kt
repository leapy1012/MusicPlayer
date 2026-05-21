package gd.app.musicplayer.domain.model

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Input model for artwork editing screens.
 */
@Parcelize
sealed class ArtworkRequest : Parcelable {

    abstract val title: String

    abstract val currentPath: String?

    @Parcelize
    data class Track(
        val music: Music
    ) : ArtworkRequest() {

        @IgnoredOnParcel
        override val title: String = buildString {
            append(music.displayAlbum)
            append(' ')
            append(ALBUM_COVER_SUFFIX)
            append(' ')
            append(music.displayArtist)
        }

        @IgnoredOnParcel
        override val currentPath: String? = music.albumPicture
    }

    @Parcelize
    data class MusicSetTarget(
        val musicSet: MusicSet
    ) : ArtworkRequest() {

        @IgnoredOnParcel
        override val title: String = buildString {
            append(musicSet.displayName())

            if (musicSet !is MusicSet.Folder && musicSet.id < 0L) {
                append(' ')
                append(ALBUM_COVER_SUFFIX)
            }
        }

        @IgnoredOnParcel
        override val currentPath: String? = musicSet.albumArt
    }

    private companion object {
        const val ALBUM_COVER_SUFFIX = "Album cover"
    }
}