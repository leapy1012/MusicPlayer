package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.LibraryDao
import gd.app.musicplayer.data.model.MusicSet

data class EditableAlbumMetadata(
    val album: String,
    val artist: String,
    val genre: String,
    val year: Int
)

class MusicSetMetadataRepo(
    private val libraryDao: LibraryDao,
    private val artworkRepo: ArtworkRepo
) {
    suspend fun updateAlbumMetadata(set: MusicSet.Album, metadata: EditableAlbumMetadata): Boolean {
        if (metadata.album.isBlank()) return false
        libraryDao.updateTracksByAlbumName(
            oldAlbum = set.name,
            newAlbum = metadata.album,
            newArtist = metadata.artist,
            newGenre = metadata.genre,
            newYear = metadata.year
        )
        artworkRepo.renameSetArtworkSource(
            sourceId = MusicSet.ALBUMS,
            oldSourceName = set.name,
            newSourceName = metadata.album
        )
        return true
    }

    suspend fun updateArtistMetadata(set: MusicSet.Artist, newName: String): Boolean {
        if (newName.isBlank()) return false
        libraryDao.updateTracksByArtistName(oldArtist = set.name, newArtist = newName)
        artworkRepo.renameSetArtworkSource(
            sourceId = MusicSet.ARTISTS,
            oldSourceName = set.name,
            newSourceName = newName
        )
        return true
    }

    suspend fun updateGenreMetadata(set: MusicSet.Genre, newName: String): Boolean {
        if (newName.isBlank()) return false
        libraryDao.updateTracksByGenreName(oldGenre = set.name, newGenre = newName)
        artworkRepo.renameSetArtworkSource(
            sourceId = MusicSet.GENRES,
            oldSourceName = set.name,
            newSourceName = newName
        )
        return true
    }
}
