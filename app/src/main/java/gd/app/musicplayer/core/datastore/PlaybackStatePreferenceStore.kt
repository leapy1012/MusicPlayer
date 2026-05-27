package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class PlaybackProgress(
    val trackId: Long,
    val progressMs: Int,
    val currentIndex: Int
)

@Singleton
class PlaybackStatePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val playSpeed: Flow<Float> =
        dataStore.data
            .map { preferences ->
                preferences[KEY_PLAY_SPEED] ?: DEFAULT_PLAY_SPEED
            }
            .distinctUntilChanged()

    val playPitch: Flow<Float> =
        dataStore.data
            .map { preferences ->
                preferences[KEY_PLAY_PITCH] ?: DEFAULT_PLAY_PITCH
            }
            .distinctUntilChanged()

    suspend fun getMusicProgress(): PlaybackProgress {
        val rawValue = dataStore.data.first()[KEY_MUSIC_PROGRESS].orEmpty()
        return rawValue.toPlaybackProgress()
    }

    suspend fun setMusicProgress(
        trackId: Long,
        progressMs: Int,
        currentIndex: Int = NO_INDEX
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_MUSIC_PROGRESS] = buildProgressValue(
                trackId = trackId,
                progressMs = progressMs.coerceAtLeast(0),
                currentIndex = currentIndex
            )
        }
    }

    suspend fun clearMusicProgress() {
        setMusicProgress(
            trackId = NO_TRACK_ID,
            progressMs = 0
        )
    }

    suspend fun getPlaySpeed(): Float {
        return dataStore.data.first()[KEY_PLAY_SPEED] ?: DEFAULT_PLAY_SPEED
    }

    suspend fun setPlaySpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_PLAY_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun getPlayPitch(): Float {
        return dataStore.data.first()[KEY_PLAY_PITCH] ?: DEFAULT_PLAY_PITCH
    }

    suspend fun setPlayPitch(pitch: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_PLAY_PITCH] = pitch.coerceIn(0.5f, 2.0f)
        }
    }

    private fun String.toPlaybackProgress(): PlaybackProgress {
        val parts = split(PROGRESS_SEPARATOR, limit = 2)
        val trackId = parts.getOrNull(0)?.toLongOrNull() ?: NO_TRACK_ID
        val progressMs = parts.getOrNull(1)
            ?.substringBefore(INDEX_SEPARATOR)
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0

        return PlaybackProgress(
            trackId = trackId,
            progressMs = progressMs,
            currentIndex = NO_INDEX
        )
    }

    private fun buildProgressValue(
        trackId: Long,
        progressMs: Int,
        currentIndex: Int
    ): String {
        return "$trackId$PROGRESS_SEPARATOR$progressMs"
    }

    private companion object {
        const val NO_TRACK_ID = -1L
        const val NO_INDEX = -1
        const val PROGRESS_SEPARATOR = "&"
        const val INDEX_SEPARATOR = "|"
        const val DEFAULT_PLAY_SPEED = 1.0f
        const val DEFAULT_PLAY_PITCH = 1.0f

        val KEY_MUSIC_PROGRESS = stringPreferencesKey("preference_music_progress")
        val KEY_PLAY_SPEED = floatPreferencesKey("preference_play_speed")
        val KEY_PLAY_PITCH = floatPreferencesKey("preference_play_pitch")
    }
}
