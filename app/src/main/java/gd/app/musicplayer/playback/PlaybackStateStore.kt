package gd.app.musicplayer.playback

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.model.Music
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class PlaybackStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveState(queue: List<Music>, currentIndex: Int, currentPositionMs: Int) {
        if (queue.isEmpty() || currentIndex !in queue.indices) {
            clear()
            return
        }
        val payload = PersistedPlaybackState(
            queue = queue,
            index = currentIndex,
            positionMs = currentPositionMs.coerceAtLeast(0),
        )
        preferences.edit {
            putString(KEY_STATE, gson.toJson(payload))
        }
    }

    fun restoreState(): PersistedPlaybackState? {
        val raw = preferences.getString(KEY_STATE, null) ?: return null
        val state = runCatching {
            gson.fromJson(raw, PersistedPlaybackState::class.java)
        }.getOrNull()
        return state?.takeIf { it.queue.isNotEmpty() }
    }

    fun clear() {
        preferences.edit { remove(KEY_STATE) }
    }

    companion object {
        private const val PREFS_NAME = "playback_state_store"
        private const val KEY_STATE = "playback_state"
        private val gson = Gson()
    }
}

data class PersistedPlaybackState(
    val queue: List<Music>,
    val index: Int,
    val positionMs: Int,
)
