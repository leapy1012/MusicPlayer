package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import org.json.JSONArray
import org.json.JSONObject

class PlaybackStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveState(queue: List<Music>, currentIndex: Int, currentPositionMs: Int) {
        val payload = JSONObject().apply {
            put(KEY_INDEX, currentIndex)
            put(KEY_POSITION, currentPositionMs)
            put(KEY_QUEUE, JSONArray().apply {
                queue.forEach { music ->
                    put(JSONObject().apply {
                        put("id", music.id)
                        put("title", music.title)
                        put("artist", music.artist)
                        put("album", music.album)
                        put("albumId", music.albumId)
                        put("playlistId", music.playlistId)
                        put("albumPicture", music.albumPicture)
                        put("data", music.data)
                        put("size", music.size ?: JSONObject.NULL)
                        put("duration", music.duration)
                        put("folderPath", music.folderPath)
                        put("date", music.date ?: JSONObject.NULL)
                        put("playTime", music.playTime ?: JSONObject.NULL)
                        put("playCount", music.playCount)
                        put("year", music.year ?: JSONObject.NULL)
                    })
                }
            })
        }
        prefs.edit()
            .putString(KEY_STATE_JSON, payload.toString())
            .putString(KEY_PROGRESS, progressValue(queue.getOrNull(currentIndex)?.id ?: -1L, currentPositionMs))
            .apply()
    }

    fun restoreState(): RestoredPlaybackState? {
        val raw = prefs.getString(KEY_STATE_JSON, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val queueArray = root.optJSONArray(KEY_QUEUE) ?: JSONArray()
            val queue = buildList(queueArray.length()) {
                for (i in 0 until queueArray.length()) {
                    val item = queueArray.optJSONObject(i) ?: continue
                    add(
                        Music(
                            id = item.optLong("id", -1L),
                            title = item.optString("title", ""),
                            artist = item.optString("artist", ""),
                            album = item.optString("album", ""),
                            albumId = item.optString("albumId", ""),
                            playlistId = item.optLong("playlistId", 0L),
                            albumPicture = item.optString("albumPicture", null),
                            data = item.optString("data", null),
                            size = item.optLong("size").takeIf { !item.isNull("size") },
                            duration = item.optInt("duration", 0),
                            folderPath = item.optString("folderPath", null),
                            date = item.optLong("date").takeIf { !item.isNull("date") },
                            playTime = item.optLong("playTime").takeIf { !item.isNull("playTime") },
                            playCount = item.optInt("playCount", 0),
                            year = item.optInt("year").takeIf { !item.isNull("year") }
                        )
                    )
                }
            }
            if (queue.isEmpty()) return null
            RestoredPlaybackState(
                queue = queue,
                index = root.optInt(KEY_INDEX, 0).coerceIn(0, queue.lastIndex),
                positionMs = root.optInt(KEY_POSITION, 0).coerceAtLeast(0)
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_STATE_JSON).remove(KEY_PROGRESS).apply()
    }

    private fun progressValue(trackId: Long, positionMs: Int): String = "$trackId&$positionMs"

    companion object {
        private const val PREF_NAME = "music_playback_state"
        private const val KEY_STATE_JSON = "state_json"
        private const val KEY_QUEUE = "queue"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_PROGRESS = "preference_music_progress"
    }
}

data class RestoredPlaybackState(
    val queue: List<Music>,
    val index: Int,
    val positionMs: Int
)

