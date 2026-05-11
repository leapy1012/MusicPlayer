package gd.app.musicplayer.domain.model

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class ArtworkRequest : Parcelable {
    abstract val title: String
    abstract val currentPath: String?

    @Parcelize
    data class Track(
        val music: Music
    ) : ArtworkRequest() {
        @IgnoredOnParcel
        override val title: String =
            buildString {
                append(music.album)
                append(" Album cover ")
                append(music.artist)
            }

        @IgnoredOnParcel
        override val currentPath: String? = music.albumPicture
    }

    @Parcelize
    data class MusicSetTarget(
        val musicSet: MusicSet
    ) : ArtworkRequest() {
        @IgnoredOnParcel
        override val title: String =
            buildString {
                if (musicSet is MusicSet.Folder) {
                    append(musicSet.name.ifBlank { musicSet.folderPath.substringAfterLast('/') })
                } else {
                    append(musicSet.name)
                }
                if (musicSet !is MusicSet.Folder && musicSet.id < 0L) {
                    append(" Album cover")
                }
            }

        @IgnoredOnParcel
        override val currentPath: String? = musicSet.albumArt
    }
}
