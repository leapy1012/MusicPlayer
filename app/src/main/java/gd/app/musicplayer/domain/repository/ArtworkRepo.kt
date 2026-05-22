package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.database.dao.LibraryDao
import gd.app.musicplayer.core.database.entity.AlbumPictureEntity
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ArtworkRepo @Inject constructor(
    private val libraryDao: LibraryDao
) {

    fun observeTrackArtwork(trackId: Long): Flow<String?> =
        libraryDao.observeTrackArtwork(trackId)

    suspend fun getCollectionArtwork(sourceId: Long, sourceName: String): String? =
        libraryDao.getAlbumPicture(sourceId, sourceName)

    suspend fun updateTrackArtwork(track: Music, artworkPath: String?) {
        val previousPath = track.albumPicture
        libraryDao.updateTrackArtwork(track.id, artworkPath)
        cleanupIfUnused(previousPath, artworkPath)
    }

    suspend fun updateSetArtwork(
        musicSet: MusicSet,
        artworkPath: String?,
        applyToAll: Boolean
    ) {
        val sourceName = musicSet.nameForArtworkSource() ?: return
        val sourceId = musicSet.sourceIdForArtwork()
        val previousPath = libraryDao.getAlbumPicture(sourceId, sourceName)
        upsertSetArtworkInternal(
            sourceId = sourceId,
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

    suspend fun syncSetArtworkSource(
        sourceId: Long,
        oldSourceName: String?,
        newSourceName: String,
        artworkPath: String?
    ) {
        if (newSourceName.isBlank()) return

        val normalizedOldName = oldSourceName?.takeIf { it.isNotBlank() }
        val normalizedArtworkPath = artworkPath?.takeIf { it.isNotBlank() }

        if (normalizedOldName == null) {
            if (normalizedArtworkPath != null) {
                upsertSetArtworkInternal(
                    sourceId = sourceId,
                    sourceName = newSourceName,
                    artworkPath = normalizedArtworkPath
                )
            }
            return
        }

        if (normalizedArtworkPath == null) {
            libraryDao.deleteAlbumPicture(sourceId, normalizedOldName)
            return
        }

        val updatedRows = libraryDao.updateAlbumPictureSource(
            sourceId = sourceId,
            oldSourceName = normalizedOldName,
            newSourceName = newSourceName,
            artworkPath = normalizedArtworkPath
        )
        if (updatedRows == 0) {
            upsertSetArtworkInternal(
                sourceId = sourceId,
                sourceName = newSourceName,
                artworkPath = normalizedArtworkPath
            )
        }
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

    private fun MusicSet.sourceIdForArtwork(): Long =
        when (this) {
            is MusicSet.Album -> MusicSet.ALBUMS
            is MusicSet.Artist -> MusicSet.ARTISTS
            is MusicSet.Genre -> MusicSet.GENRES
            is MusicSet.Folder -> MusicSet.FOLDERS
            is MusicSet.Playlist -> id
            else -> id
        }

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
