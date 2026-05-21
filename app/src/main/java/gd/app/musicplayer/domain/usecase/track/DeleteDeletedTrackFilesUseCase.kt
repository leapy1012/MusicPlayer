package gd.app.musicplayer.domain.usecase.track

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.LibraryRepo
import java.io.File
import javax.inject.Inject

class DeleteDeletedTrackFilesUseCase @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(tracks: Collection<Music>): Int {
        val deletedIds = mutableListOf<Long>()

        tracks.distinctBy(Music::id).forEach { track ->
            if (deleteFromMediaStore(track) || deleteFromFileSystem(track)) {
                deletedIds += track.id
            }
        }

        if (deletedIds.isNotEmpty()) {
            libraryRepo.markDeletedSourceFilesRemoved(deletedIds)
        }

        return deletedIds.size
    }

    private fun deleteFromMediaStore(track: Music): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.id
            )
            appContext.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteFromFileSystem(track: Music): Boolean {
        val path = track.data?.takeIf(String::isNotBlank) ?: return false
        return try {
            val file = File(path)
            !file.exists() || file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
