package gd.app.musicplayer.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.db.entity.MusicPlaylistEntity
import gd.app.musicplayer.data.repository.PlaylistRepo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistBackupManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val playlistRepo: PlaylistRepo,
    private val musicDao: MusicDao
) {

    suspend fun backup(): Int {
        val backups = playlistRepo.getPlaylistBackups()

        val root = JSONObject().apply {
            put(KEY_VERSION, JSON_VERSION)
            put(
                KEY_PLAYLISTS,
                JSONArray().apply {
                    backups.forEach { playlist ->
                        put(
                            JSONObject().apply {
                                put(KEY_NAME, playlist.name)
                                put(KEY_TRACKS, JSONArray(playlist.trackIds))
                            }
                        )
                    }
                }
            )
        }

        backupFile.apply {
            parentFile?.mkdirs()
            writeText(root.toString())
        }

        return backups.size
    }

    suspend fun restore(): Int {
        val file = backupFile

        if (!file.exists()) {
            return 0
        }

        val payload = runCatching {
            JSONObject(file.readText())
        }.getOrNull() ?: return 0

        val playlists = payload.optJSONArray(KEY_PLAYLISTS) ?: return 0

        var restoredCount = 0

        for (index in 0 until playlists.length()) {
            val playlistJson = playlists.optJSONObject(index) ?: continue
            val playlistName = playlistJson.optString(KEY_NAME).trim()

            if (playlistName.isEmpty()) {
                continue
            }

            val playlistId = playlistRepo.createOrGetPlaylist(playlistName)

            playlistRepo.clearPlaylistEntries(playlistId)

            val trackIds = playlistJson
                .optJSONArray(KEY_TRACKS)
                ?.toLongList()
                .orEmpty()
                .distinct()

            if (trackIds.isEmpty()) {
                restoredCount += 1
                continue
            }

            val existingTrackIds = musicDao
                .getExistingTrackIds(trackIds)
                .toHashSet()

            val playlistEntries = trackIds
                .filter { trackId -> trackId in existingTrackIds }
                .mapIndexed { indexInPlaylist, trackId ->
                    MusicPlaylistEntity(
                        musicId = trackId,
                        playlistId = playlistId,
                        sort = indexInPlaylist + 1
                    )
                }

            if (playlistEntries.isNotEmpty()) {
                playlistRepo.insertPlaylistEntries(playlistEntries)
            }

            restoredCount += 1
        }

        return restoredCount
    }

    fun hasBackup(): Boolean {
        return backupFile.exists()
    }

    private val backupFile: File
        get() = File(
            appContext.getExternalFilesDir(null),
            FILE_NAME
        )

    private fun JSONArray.toLongList(): List<Long> {
        return buildList(length()) {
            for (index in 0 until length()) {
                val value = optLong(index, INVALID_ID)

                if (value != INVALID_ID) {
                    add(value)
                }
            }
        }
    }

    companion object {
        private const val FILE_NAME = "playlist_backup.json"
        private const val JSON_VERSION = 1

        private const val KEY_VERSION = "version"
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_NAME = "name"
        private const val KEY_TRACKS = "tracks"

        private const val INVALID_ID = Long.MIN_VALUE
    }
}
