package gd.app.musicplayer.domain.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Ignore
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class Music(
    @ColumnInfo("_id")
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo("album_id")
    val albumId: String,
    @ColumnInfo("p_id")
    val playlistId: Long,
    @ColumnInfo(name = "album_pic")
    val albumPicture: String? = null,
    val data: String? = null,
    val size: Long? = null,
    val duration: Int = 0,
    @ColumnInfo("folder_path")
    val folderPath: String? = null,
    val date: Long? = null,
    @ColumnInfo(name = "play_time")
    val playTime: Long? = null,
    @ColumnInfo(name = "count")
    val playCount: Int = 0,
    val year: Int? = null,
    @ColumnInfo(defaultValue = "'Unknown'")
    val genres: String = DEFAULT_GENRE,
    @ColumnInfo(defaultValue = "-1")
    val track: Int = UNKNOWN_NUMBER,
    @ColumnInfo(name = "bit_rate", defaultValue = "-1")
    val bitRate: Int = UNKNOWN_NUMBER,
    @ColumnInfo(name = "sample_rate", defaultValue = "-1")
    val sampleRate: Int = UNKNOWN_NUMBER,
) : Parcelable {

    @Ignore
    @IgnoredOnParcel
    var queueToken: Int = 0

    val hasValidFilePath: Boolean
        get() = !data.isNullOrBlank()

    val durationMs: Long
        get() = duration.coerceAtLeast(0).toLong()

    val displayTitle: String
        get() = title.ifBlank { UNKNOWN_TITLE }

    val displayArtist: String
        get() = artist.ifBlank { UNKNOWN_ARTIST }

    val displayAlbum: String
        get() = album.ifBlank { UNKNOWN_ALBUM }

    companion object {
        const val DEFAULT_GENRE = "Unknown"
        const val UNKNOWN_NUMBER = -1
        const val UNKNOWN_TITLE = "Unknown title"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}