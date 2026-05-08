package gd.app.musicplayer.ui.feature.lyrics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.data.local.preference.LyricSettingPreferenceStore
import gd.app.musicplayer.data.local.preference.LyricsSettingPreference
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.TrackLyricsStore
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FullLyricUiState(
    val title: String = "",
    val artist: String = "",
    val positionMs: Long = 0L,
    val trackId: Long? = null,
    val source: String? = null,
    val isPlaying: Boolean = false,
    val lyricText: String? = null,
    val lyricOffset: Long = 0L,
    val lyricPreferences: LyricsSettingPreference = LyricsSettingPreference()
)

@HiltViewModel
class FullLyricViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lyricPreferenceStore: LyricSettingPreferenceStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FullLyricUiState())
    val uiState: StateFlow<FullLyricUiState> = _uiState

    private var lyricJob: Job? = null
    private var currentSource: String? = null

    init {
        observeLyricPreferences()
    }

    private fun observeLyricPreferences() {
        viewModelScope.launch {
            lyricPreferenceStore.lyricPreferences.collect { preferences ->
                _uiState.update {
                    it.copy(lyricPreferences = preferences)
                }
            }
        }
    }

    fun onPlaybackChanged(
        title: String,
        artist: String,
        positionMs: Long,
        trackId: Long?,
        source: String?,
        isPlaying: Boolean
    ) {
        _uiState.update {
            it.copy(
                title = title,
                artist = artist,
                positionMs = positionMs,
                trackId = trackId,
                source = source,
                isPlaying = isPlaying
            )
        }

        if (trackId != null) {
            maybeLoadLyrics(trackId, source)
        }
    }

    private fun maybeLoadLyrics(
        trackId: Long,
        source: String?
    ) {
        if (source == currentSource) return

        currentSource = source
        lyricJob?.cancel()

        _uiState.update {
            it.copy(
                lyricText = null,
                lyricOffset = TrackLyricsStore.from(context).getTrackLyricOffset(trackId).toLong()
            )
        }

        if (source.isNullOrBlank()) return

        lyricJob = viewModelScope.launch {
            val result = LyricsLoader.load(context, trackId, source)
            if (currentSource != source) return@launch

            _uiState.update {
                it.copy(
                    lyricText = result.text,
                    lyricOffset = TrackLyricsStore.from(context).getTrackLyricOffset(trackId).toLong()
                )
            }
        }
    }

    override fun onCleared() {
        lyricJob?.cancel()
        super.onCleared()
    }
}