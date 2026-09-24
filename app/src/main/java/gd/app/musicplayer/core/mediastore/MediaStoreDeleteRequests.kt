package gd.app.musicplayer.core.mediastore

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import gd.app.musicplayer.domain.model.Music

/**
 * Builds MediaStore system delete requests (API 30+) for audio tracks.
 */
object MediaStoreDeleteRequests {

    @RequiresApi(Build.VERSION_CODES.R)
    fun createDeletePendingIntent(
        contentResolver: ContentResolver,
        tracks: List<Music>
    ): PendingIntent? {
        val uris = tracks
            .distinctBy(Music::id)
            .filter { track -> track.id > 0L }
            .map { track ->
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id
                )
            }

        if (uris.isEmpty()) return null

        return runCatching {
            MediaStore.createDeleteRequest(contentResolver, uris)
        }.getOrNull()
    }
}
