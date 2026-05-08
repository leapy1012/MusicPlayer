package gd.app.musicplayer.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.db.MediaStoreMusicImporter
import gd.app.musicplayer.data.db.dao.LibraryDao
import gd.app.musicplayer.data.model.Music
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
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao
) {
    private val importer = MediaStoreMusicImporter()

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun updateTrackMetadata(
        track: Music,
        metadata: EditableTrackMetadata
    ): Boolean {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            track.id
        )

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, metadata.title)
            put(MediaStore.Audio.Media.ALBUM, metadata.album)
            put(MediaStore.Audio.Media.ARTIST, metadata.artist)
            put(MediaStore.Audio.Media.GENRE, metadata.genre)
            put(MediaStore.Audio.Media.TRACK, metadata.track)
        }

        val updatedRows = context.contentResolver.update(
            uri,
            values,
            null,
            null
        )

        if (updatedRows <= 0) {
            return false
        }

        importer.queryMusicById(context, track.id)?.let { refreshedTrack ->
            libraryDao.upsertAll(listOf(refreshedTrack))
        }

        return true
    }
}