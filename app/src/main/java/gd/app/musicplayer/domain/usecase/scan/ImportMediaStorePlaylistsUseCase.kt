package gd.app.musicplayer.domain.usecase.scan

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.datastore.PlaylistPreferenceDataStore
import gd.app.musicplayer.domain.repository.PlaylistRepo
import gd.app.musicplayer.domain.repository.ScanRepo
import javax.inject.Inject

/**
 * Imports newly added MediaStore system playlists during library sync (original v5.k).
 *
 * Uses [PlaylistPreferenceDataStore] watermark `preference_max_playlist_time` (date_added seconds)
 * so each playlist is imported at most once. Existing local playlist names are skipped.
 */
@Suppress("DEPRECATION")
class ImportMediaStorePlaylistsUseCase @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val playlistPreference: PlaylistPreferenceDataStore,
    private val playlistRepo: PlaylistRepo,
    private val scanRepo: ScanRepo
) {

    suspend operator fun invoke(): Int {
        val watermark = playlistPreference.getMediaStorePlaylistDateAddedWatermark()
        var nextWatermark = watermark
        val pending = mutableListOf<PendingPlaylist>()
        val seenNames = mutableSetOf<String>()

        runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Playlists._ID,
                    MediaStore.Audio.Playlists.NAME,
                    MediaStore.Audio.Playlists.DATE_ADDED
                ),
                "${MediaStore.Audio.Playlists.DATE_ADDED}>$watermark",
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val mediaStoreId = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val dateAdded = cursor.getLong(dateIndex)
                    nextWatermark = maxOf(nextWatermark, dateAdded)
                    if (name.isEmpty()) continue
                    if (!seenNames.add(name)) continue
                    if (playlistRepo.playlistNameExists(name)) continue
                    pending += PendingPlaylist(mediaStoreId = mediaStoreId, name = name)
                }
            }
        }

        playlistPreference.setMediaStorePlaylistDateAddedWatermark(nextWatermark)

        if (pending.isEmpty()) return 0

        val libraryTrackIds = scanRepo.getAllTrackIds()
        var imported = 0

        for (playlist in pending) {
            val memberIds = queryMemberAudioIds(playlist.mediaStoreId)
                .filter { id -> id in libraryTrackIds }
            if (memberIds.isEmpty()) {
                // Still create empty playlist to match original create-then-add behavior
                // when members resolve later; original always L(name) then b(members).
            }
            val playlistId = playlistRepo.createPlaylist(playlist.name)
            if (memberIds.isNotEmpty()) {
                playlistRepo.addTrackIdsToPlaylists(
                    playlistIds = listOf(playlistId),
                    trackIds = memberIds
                )
            }
            imported++
        }

        return imported
    }

    private fun queryMemberAudioIds(mediaStorePlaylistId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri(
                    "external",
                    mediaStorePlaylistId
                ),
                arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),
                null,
                null,
                null
            )?.use { cursor ->
                val audioIdIndex =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
                while (cursor.moveToNext()) {
                    ids += cursor.getLong(audioIdIndex)
                }
            }
        }
        return ids
    }

    private data class PendingPlaylist(
        val mediaStoreId: Long,
        val name: String
    )
}
