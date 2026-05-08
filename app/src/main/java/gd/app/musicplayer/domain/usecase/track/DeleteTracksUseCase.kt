package gd.app.musicplayer.domain.usecase.track

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.LibraryRepo
import java.io.File
import javax.inject.Inject

class DeleteTracksUseCase @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(tracks: Collection<Music>): Int {
        val deletedIds = mutableListOf<Long>()

        tracks.distinctBy(Music::id).forEach { track ->
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.id
            )
            val wasDeleted = tryDeleteFromMediaStore(contentUri) || tryDeleteFromFileSystem(track)
            if (wasDeleted) {
                deletedIds += track.id
            }
        }

        if (deletedIds.isNotEmpty()) {
            libraryRepo.hideTracks(deletedIds, System.currentTimeMillis())
        }

        return deletedIds.size
    }

    private fun tryDeleteFromMediaStore(contentUri: Uri): Boolean {
        return try {
            appContext.contentResolver.delete(contentUri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun tryDeleteFromFileSystem(track: Music): Boolean {
        val filePath = track.data?.takeIf { it.isNotBlank() } ?: return false
        return try {
            File(filePath).delete()
        } catch (_: Exception) {
            false
        }
    }
}
