package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.LibraryDao
import gd.app.musicplayer.data.db.entity.AlbumPictureEntity
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import java.io.File

class ArtworkRepo(
    private val libraryDao: LibraryDao
) {

    suspend fun getCollectionArtwork(sourceId: Long, sourceName: String): String? =
        libraryDao.getAlbumPicture(sourceId, sourceName)

    suspend fun updateTrackArtwork(track: Music, artworkPath: String?) {
        val previousPath = track.albumPicture
        libraryDao.updateTrackArtwork(track._id, artworkPath)
        cleanupIfUnused(previousPath, artworkPath)
    }

    suspend fun updateSetArtwork(
        musicSet: MusicSet,
        artworkPath: String?,
        applyToAll: Boolean
    ) {
        val sourceName = musicSet.nameForArtworkSource() ?: return
        val previousPath = libraryDao.getAlbumPicture(musicSet.id, sourceName)
        upsertSetArtworkInternal(
            sourceId = musicSet.id,
            sourceName = sourceName,
            artworkPath = artworkPath
        )

        if (applyToAll) {
            when (musicSet) {
                is MusicSet.Album -> libraryDao.updateTrackArtworkByAlbum(musicSet.name, artworkPath)
                is MusicSet.Artist -> libraryDao.updateTrackArtworkByArtist(musicSet.name, artworkPath)
                is MusicSet.Genre -> libraryDao.updateTrackArtworkByGenre(musicSet.name, artworkPath)
                else -> Unit
            }
        }

        cleanupIfUnused(previousPath, artworkPath)
    }

    suspend fun renameSetArtworkSource(
        sourceId: Long,
        oldSourceName: String?,
        newSourceName: String
    ) {
        if (newSourceName.isBlank()) return
        libraryDao.updateAlbumPictureSourceName(
            sourceId = sourceId,
            newSourceName = newSourceName,
            oldSourceName = oldSourceName
        )
    }

    suspend fun deleteSetArtworkBySourceId(sourceId: Long) {
        val previousPaths = libraryDao.getAlbumPicturesBySourceId(sourceId)
        libraryDao.deleteAlbumPicturesBySourceId(sourceId)
        previousPaths.forEach { cleanupIfUnused(it, replacementPath = null) }
    }

    suspend fun cleanupOrphanedPlaylistArtwork() {
        val orphanedPaths = libraryDao.getOrphanedPlaylistAlbumPicturePaths()
        libraryDao.deleteOrphanedPlaylistAlbumPictures()
        orphanedPaths.forEach { cleanupIfUnused(it, replacementPath = null) }
    }

    suspend fun cleanupIfUnused(previousPath: String?, replacementPath: String? = null) {
        val candidate = previousPath
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it == replacementPath }
            ?.takeIf(::isManagedArtworkPath)
            ?: return

        val trackRefs = libraryDao.countTracksUsingArtwork(candidate)
        val setRefs = libraryDao.countSetsUsingArtwork(candidate)
        if (trackRefs == 0 && setRefs == 0) {
            runCatching { File(candidate).delete() }
        }
    }

    private suspend fun upsertSetArtworkInternal(
        sourceId: Long,
        sourceName: String,
        artworkPath: String?
    ) {
        val existingRowId = libraryDao.getAlbumPictureRowId(sourceId, sourceName)
        if (existingRowId == null) {
            libraryDao.insertAlbumPicture(
                AlbumPictureEntity(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    sourcePicture = artworkPath
                )
            )
        } else {
            libraryDao.updateAlbumPicture(existingRowId, artworkPath)
        }
    }

    private fun isManagedArtworkPath(path: String): Boolean =
        !path.startsWith("content://") && File(path).isAbsolute

    private fun MusicSet.nameForArtworkSource(): String? =
        when (this) {
            is MusicSet.Album -> name
            is MusicSet.Artist -> name
            is MusicSet.Genre -> name
            is MusicSet.Folder -> folderPath
            is MusicSet.Playlist -> name
            else -> null
        }
}
