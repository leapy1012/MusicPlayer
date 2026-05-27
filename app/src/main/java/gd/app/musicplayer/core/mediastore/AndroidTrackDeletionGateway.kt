package gd.app.musicplayer.core.mediastore

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.TrackDeletionGateway
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTrackDeletionGateway @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) : TrackDeletionGateway {

    override fun deleteTrackFromStorage(track: Music): Boolean {
        val deletedFromMediaStore = try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.id
            )
            appContext.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
        if (deletedFromMediaStore) return true

        val filePath = track.data?.takeIf { it.isNotBlank() } ?: return false
        return try {
            File(filePath).delete()
        } catch (_: Exception) {
            false
        }
    }

    override fun deleteDeletedTrackSource(track: Music): Boolean {
        val deletedFromMediaStore = try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.id
            )
            appContext.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
        if (deletedFromMediaStore) return true

        val path = track.data?.takeIf(String::isNotBlank) ?: return false
        return try {
            val file = File(path)
            !file.exists() || file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
