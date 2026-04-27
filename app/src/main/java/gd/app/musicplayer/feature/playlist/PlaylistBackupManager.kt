package gd.app.musicplayer.feature.playlist

import android.content.Context
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.data.db.entity.MusicPlaylistEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistBackupManager {
    private const val FILE_NAME = "playlist_backup.json"
    private const val JSON_VERSION = 1

    suspend fun backup(context: Context): Int {
        val playlistDao = context.appContainer.playlistRepo
        val backups = playlistDao.getPlaylistBackups()
        val root = JSONObject().apply {
            put("version", JSON_VERSION)
            put(
                "playlists",
                JSONArray().apply {
                    backups.forEach { playlist ->
                        put(
                            JSONObject().apply {
                                put("name", playlist.name)
                                put("tracks", JSONArray(playlist.trackIds))
                            }
                        )
                    }
                }
            )
        }

        backupFile(context).apply {
            parentFile?.mkdirs()
            writeText(root.toString())
        }
        return backups.size
    }

    suspend fun restore(context: Context): Int {
        val file = backupFile(context)
        if (!file.exists()) return 0

        val payload = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return 0
        val playlists = payload.optJSONArray("playlists") ?: return 0
        val playlistRepo = context.appContainer.playlistRepo
        val musicDao = context.appContainer.musicDao
        var restoredCount = 0

        for (index in 0 until playlists.length()) {
            val item = playlists.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue

            val playlistId = playlistRepo.createOrGetPlaylist(name)
            playlistRepo.clearPlaylistEntries(playlistId)

            val trackIds = item.optJSONArray("tracks")
                ?.let(::jsonArrayToLongs)
                .orEmpty()
                .distinct()
            val existingTrackIds = musicDao.getExistingTrackIds(trackIds).toHashSet()
            val refs = trackIds
                .filter { it in existingTrackIds }
                .mapIndexed { sort, trackId ->
                    MusicPlaylistEntity(
                        musicId = trackId,
                        playlistId = playlistId,
                        sort = sort + 1
                    )
                }
            if (refs.isNotEmpty()) {
                playlistRepo.insertPlaylistEntries(refs)
            }
            restoredCount += 1
        }

        return restoredCount
    }

    fun hasBackup(context: Context): Boolean = backupFile(context).exists()

    private fun backupFile(context: Context): File =
        File(context.getExternalFilesDir(null), FILE_NAME)

    private fun jsonArrayToLongs(array: JSONArray): List<Long> =
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.optLong(index, Long.MIN_VALUE)
                if (value != Long.MIN_VALUE) {
                    add(value)
                }
            }
        }

    data class PlaylistBackup(
        val name: String,
        val trackIds: List<Long>
    )
}
