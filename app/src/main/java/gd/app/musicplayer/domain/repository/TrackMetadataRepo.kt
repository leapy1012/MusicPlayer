package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.data.local.db.dao.LibraryDao
import gd.app.musicplayer.domain.model.Music
import javax.inject.Inject
import javax.inject.Singleton

data class EditableTrackMetadata(
    val title: String,
    val album: String,
    val artist: String,
    val genre: String,
    val track: Int
)

@Singleton
class TrackMetadataRepo @Inject constructor(
    private val libraryDao: LibraryDao
) {
    suspend fun updateTrackMetadata(
        track: Music,
        metadata: EditableTrackMetadata,
        artworkPath: String?
    ): Music? {
        val updatedRows = libraryDao.updateTrackMetadata(
            trackId = track.id,
            title = metadata.title,
            album = metadata.album,
            artist = metadata.artist,
            genre = metadata.genre,
            trackNumber = metadata.track,
            artworkPath = artworkPath
        )

        if (updatedRows <= 0) {
            return null
        }

        return track.copy(
            title = metadata.title,
            album = metadata.album,
            artist = metadata.artist,
            genres = metadata.genre,
            track = metadata.track,
            albumPicture = artworkPath
        )
    }
}
