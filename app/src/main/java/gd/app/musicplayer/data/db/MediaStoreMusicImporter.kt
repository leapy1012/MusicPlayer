package gd.app.musicplayer.data.db

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import gd.app.musicplayer.data.db.entity.MusicEntity

class MediaStoreMusicImporter {

    @RequiresApi(Build.VERSION_CODES.R)
    fun queryMusic(context: Context): List<MusicEntity> {
        val items = mutableListOf<MusicEntity>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                mapCursorRow(cursor)?.let(items::add)
            }
        }

        return items
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun queryMusicById(context: Context, id: Long): MusicEntity? {
        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media._ID} = ?",
            arrayOf(id.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) mapCursorRow(cursor) else null
        }
    }

    private fun albumArtworkUriString(albumId: Long): String {
        return ContentUris.withAppendedId(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            albumId
        ).toString()
    }

    private fun extractFolderName(folderPath: String?): String? {
        if (folderPath.isNullOrBlank()) return folderPath
        return folderPath.trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
    }

    private fun String?.extractFolderPath(): String? {
        if (this.isNullOrBlank()) return null
        return substringBeforeLast('\\', "")
            .ifEmpty { substringBeforeLast('/', "") }
            .ifEmpty { null }
    }

    private fun mapCursorRow(cursor: android.database.Cursor): MusicEntity? {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumId = cursor.getLongOrNull(albumIdIndex)
        val folderPath = data.extractFolderPath()

        return MusicEntity(
            id = id,
            title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "",
            data = data ?: "",
            size = cursor.getLongOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
            duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
            album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
            albumPicture = albumId?.let(::albumArtworkUriString),
            albumId = albumId,
            genres = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)) ?: "unknown",
            folderPath = folderPath,
            folderName = extractFolderName(folderPath),
            date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) * 1000L,
            dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)) * 1000L,
            year = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
            artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
            artistPicture = null,
            isRingtone = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)),
            track = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)) ?: -1
        )
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.GENRE
        )
    }
}
