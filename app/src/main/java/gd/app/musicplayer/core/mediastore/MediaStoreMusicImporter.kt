package gd.app.musicplayer.core.mediastore

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.core.database.getIntOrNull
import gd.app.musicplayer.core.database.getLongOrNull

/**
 * MediaStore audio importer aligned with original v5.j:
 * - selection is `_size > 0` (no `IS_MUSIC` filter)
 * - pages by `_id DESC LIMIT 3000` until exhausted
 * - ringtone flag only when `is_ringtone == 1 && is_music == 0`
 */
class MediaStoreMusicImporter {

    fun queryMusic(
        context: Context,
        modifiedSinceMs: Long? = null
    ): List<MusicEntity> {
        val items = mutableListOf<MusicEntity>()
        val genreByAudioId = if (Build.VERSION.SDK_INT < 30) {
            loadGenreIndex(context)
        } else {
            emptyMap()
        }

        var maxExclusiveId = Long.MAX_VALUE
        while (true) {
            val page = queryPage(
                context = context,
                maxExclusiveId = maxExclusiveId,
                modifiedSinceMs = modifiedSinceMs,
                genreByAudioId = genreByAudioId
            ) ?: break

            if (page.isEmpty()) break

            items += page
            val lowestId = page.minOf(MusicEntity::id)
            if (lowestId >= maxExclusiveId) break
            maxExclusiveId = lowestId
        }

        return items
    }

    fun queryMusicById(context: Context, id: Long): MusicEntity? {
        val genreByAudioId = if (Build.VERSION.SDK_INT < 30) {
            loadGenreIndex(context)
        } else {
            emptyMap()
        }

        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection(),
            "${MediaStore.Audio.Media.SIZE} > 0 AND ${MediaStore.Audio.Media._ID} = ?",
            arrayOf(id.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) mapCursorRow(cursor, genreByAudioId) else null
        }
    }

    fun queryMusicIds(context: Context): Set<Long> {
        val ids = LinkedHashSet<Long>()
        var maxExclusiveId = Long.MAX_VALUE

        while (true) {
            val pageIds = mutableListOf<Long>()
            queryIdPage(context, maxExclusiveId)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    pageIds += cursor.getLong(idIndex)
                }
            } ?: break

            if (pageIds.isEmpty()) break

            ids += pageIds
            val lowestId = pageIds.minOrNull() ?: break
            if (lowestId >= maxExclusiveId) break
            maxExclusiveId = lowestId
        }

        return ids
    }

    private fun queryPage(
        context: Context,
        maxExclusiveId: Long,
        modifiedSinceMs: Long?,
        genreByAudioId: Map<Long, String>
    ): List<MusicEntity>? {
        val items = mutableListOf<MusicEntity>()
        val cursor = queryAudioCursor(
            context = context,
            projection = projection(),
            maxExclusiveId = maxExclusiveId,
            modifiedSinceMs = modifiedSinceMs
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                mapCursorRow(it, genreByAudioId)?.let(items::add)
            }
        }
        return items
    }

    private fun queryIdPage(context: Context, maxExclusiveId: Long): Cursor? {
        return queryAudioCursor(
            context = context,
            projection = ID_PROJECTION,
            maxExclusiveId = maxExclusiveId,
            modifiedSinceMs = null
        )
    }

    private fun queryAudioCursor(
        context: Context,
        projection: Array<String>,
        maxExclusiveId: Long,
        modifiedSinceMs: Long?
    ): Cursor? {
        val selection = buildString {
            append(MediaStore.Audio.Media._ID)
            append('<')
            append(maxExclusiveId)
            append(" AND ")
            append(MediaStore.Audio.Media.SIZE)
            append(">0")
            if (modifiedSinceMs != null) {
                append(" AND ")
                append(MediaStore.Audio.Media.DATE_MODIFIED)
                append(">=?")
            }
        }
        val selectionArgs = modifiedSinceMs?.let {
            arrayOf((it / 1000L).coerceAtLeast(0L).toString())
        }

        return runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                val args = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    if (selectionArgs != null) {
                        putStringArray(
                            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                            selectionArgs
                        )
                    }
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Audio.Media._ID)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE_SIZE)
                }
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    args,
                    null
                )
            } else {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Audio.Media._ID} DESC LIMIT $PAGE_SIZE"
                )
            }
        }.getOrNull()
    }

    private fun projection(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 30) {
            PROJECTION_API30
        } else {
            PROJECTION_BASE
        }
    }

    private fun albumArtworkUriString(albumId: Long): String {
        // Classic albumart URI (not Albums.EXTERNAL — that table has no openable stream
        // and some OEMs crash Glide with `no such column: _data` on audio_albums).
        // Runtime loads prefer embedded covers via [Music.albumArtSource] when a file path exists.
        return "content://media/external/audio/albumart/$albumId"
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

    private fun mapCursorRow(
        cursor: Cursor,
        genreByAudioId: Map<Long, String>
    ): MusicEntity? {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumId = cursor.getLongOrNull(albumIdIndex)
        val folderPath = data.extractFolderPath()

        val isRingtoneCol = cursor.getInt(
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
        )
        val isMusicCol = cursor.getInt(
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
        )
        // Original v5.j: ringtone-only when is_ringtone==1 && is_music==0
        val isRingtone = if (isRingtoneCol == 1 && isMusicCol == 0) 1 else 0

        val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
        val genre = when {
            genreColumn >= 0 -> cursor.getString(genreColumn)
                ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
            else -> genreByAudioId[id]
        } ?: "unknown"

        return MusicEntity(
            id = id,
            title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                ?: "",
            data = data ?: "",
            size = cursor.getLongOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
            duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
            album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
            albumPicture = albumId?.let(::albumArtworkUriString),
            albumId = albumId,
            genres = genre,
            folderPath = folderPath,
            folderName = extractFolderName(folderPath),
            date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) * 1000L,
            dateModified = cursor.getLong(
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            ) * 1000L,
            year = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
            artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
            artistPicture = null,
            isRingtone = isRingtone,
            track = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                ?: -1
        )
    }

    private fun loadGenreIndex(context: Context): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null
            )?.use { genres ->
                val idIndex = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameIndex = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(idIndex)
                    val name = genres.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: continue
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null,
                        null,
                        null
                    )?.use { members ->
                        val audioIdIndex =
                            members.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (members.moveToNext()) {
                            result[members.getLong(audioIdIndex)] = name
                        }
                    }
                }
            }
        }
        return result
    }

    private companion object {
        const val PAGE_SIZE = 3000

        val ID_PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID
        )

        val PROJECTION_BASE = arrayOf(
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
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.TRACK
        )

        val PROJECTION_API30 = PROJECTION_BASE + MediaStore.Audio.Media.GENRE
    }
}
