package gd.app.musicplayer.domain.usecase.track

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.TrackMutationRepo
import java.io.File

class DeleteTracksUseCase(
    private val appContext: Context,
    private val trackMutationRepo: TrackMutationRepo
) {
    suspend operator fun invoke(tracks: Collection<Music>): Int {
        val deletedIds = mutableListOf<Long>()

        tracks.distinctBy(Music::_id).forEach { track ->
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track._id
            )
            val wasDeleted = tryDeleteFromMediaStore(contentUri) || tryDeleteFromFileSystem(track)
            if (wasDeleted) {
                deletedIds += track._id
            }
        }

        if (deletedIds.isNotEmpty()) {
            trackMutationRepo.hideTracks(deletedIds, System.currentTimeMillis())
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
