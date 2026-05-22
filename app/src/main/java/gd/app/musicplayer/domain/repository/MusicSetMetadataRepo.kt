package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.database.dao.LibraryDao
import gd.app.musicplayer.domain.model.MusicSet
import javax.inject.Inject
import javax.inject.Singleton

data class EditableAlbumMetadata(
    val album: String,
    val artist: String,
    val genre: String,
    val year: Int
)

@Singleton
class MusicSetMetadataRepo @Inject constructor(
    private val libraryDao: LibraryDao,
    private val artworkRepo: ArtworkRepo
) {
    suspend fun updateAlbumMetadata(
        set: MusicSet.Album,
        metadata: EditableAlbumMetadata,
        artworkPath: String?
    ): Boolean {
        if (metadata.album.isBlank()) return false
        libraryDao.updateTracksByAlbumName(
            oldAlbum = set.name,
            newAlbum = metadata.album,
            newArtist = metadata.artist,
            newGenre = metadata.genre,
            newYear = metadata.year
        )
        artworkRepo.syncSetArtworkSource(
            sourceId = MusicSet.ALBUMS,
            oldSourceName = set.name,
            newSourceName = metadata.album,
            artworkPath = artworkPath
        )
        return true
    }

    suspend fun updateArtistMetadata(
        set: MusicSet.Artist,
        newName: String,
        artworkPath: String?
    ): Boolean {
        if (newName.isBlank()) return false
        libraryDao.updateTracksByArtistName(oldArtist = set.name, newArtist = newName)
        artworkRepo.syncSetArtworkSource(
            sourceId = MusicSet.ARTISTS,
            oldSourceName = set.name,
            newSourceName = newName,
            artworkPath = artworkPath
        )
        return true
    }

    suspend fun updateGenreMetadata(
        set: MusicSet.Genre,
        newName: String,
        artworkPath: String?
    ): Boolean {
        if (newName.isBlank()) return false
        libraryDao.updateTracksByGenreName(oldGenre = set.name, newGenre = newName)
        artworkRepo.syncSetArtworkSource(
            sourceId = MusicSet.GENRES,
            oldSourceName = set.name,
            newSourceName = newName,
            artworkPath = artworkPath
        )
        return true
    }
}
