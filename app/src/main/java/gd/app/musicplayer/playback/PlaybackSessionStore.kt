package gd.app.musicplayer.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.model.PlaybackSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {


    private val Context.playbackSessionDataStore by preferencesDataStore(
        name = "playback_session"
    )
    private object Keys {
        val MUSIC_ID = longPreferencesKey("music_id")
        val POSITION_MS = longPreferencesKey("position_ms")
        val CURRENT_INDEX = intPreferencesKey("current_index")
    }

    val session: Flow<PlaybackSession?> =
        context.playbackSessionDataStore.data
            .map { preferences ->
                val musicId = preferences[Keys.MUSIC_ID]
                val positionMs = preferences[Keys.POSITION_MS]
                val currentIndex = preferences[Keys.CURRENT_INDEX]

                if (
                    musicId == null ||
                    positionMs == null ||
                    currentIndex == null
                ) {
                    null
                } else {
                    PlaybackSession(
                        musicId = musicId,
                        positionMs = positionMs,
                        currentIndex = currentIndex
                    )
                }
            }

    suspend fun getLastSession(): PlaybackSession? {
        return session.firstOrNull()
    }

    suspend fun saveSession(
        musicId: Long,
        positionMs: Long,
        currentIndex: Int
    ) {
        context.playbackSessionDataStore.edit { preferences ->
            preferences[Keys.MUSIC_ID] = musicId
            preferences[Keys.POSITION_MS] = positionMs
            preferences[Keys.CURRENT_INDEX] = currentIndex
        }
    }

    suspend fun clearSession() {
        context.playbackSessionDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
