package gd.app.musicplayer.feature.lyrics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.datastore.LyricSettingPreferenceStore
import gd.app.musicplayer.core.datastore.LyricsSettingPreference
import gd.app.musicplayer.core.datastore.TrackLyricData
import gd.app.musicplayer.core.datastore.TrackLyricPreferenceStore
import gd.app.musicplayer.util.LyricsLoader
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val lyricPreferenceStore: LyricSettingPreferenceStore,
    private val trackLyricPreferenceStore: TrackLyricPreferenceStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FullLyricUiState())
    val uiState: StateFlow<FullLyricUiState> = _uiState

    private val currentTrack = MutableStateFlow<TrackRequest?>(null)
    private var lyricJob: Job? = null
    private var currentLoadKey: LyricLoadKey? = null

    init {
        observeLyricPreferences()
        observeCurrentTrackLyricData()
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
            currentTrack.value = TrackRequest(trackId, source)
        } else {
            currentTrack.value = null
            currentLoadKey = null
            lyricJob?.cancel()
            _uiState.update {
                it.copy(lyricText = null, lyricOffset = 0L)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeCurrentTrackLyricData() {
        viewModelScope.launch {
            currentTrack
                .flatMapLatest { request ->
                    if (request == null) {
                        flowOf(null)
                    } else {
                        trackLyricPreferenceStore.observeTrackLyricData(request.trackId)
                            .map { data -> request to data }
                    }
                }
                .collect { requestAndData ->
                    if (requestAndData == null) return@collect
                    loadLyrics(requestAndData.first, requestAndData.second)
                }
        }
    }

    private fun loadLyrics(
        request: TrackRequest,
        lyricData: TrackLyricData
    ) {
        val loadKey = LyricLoadKey(
            trackId = request.trackId,
            source = request.source,
            lyricPath = lyricData.path,
            lyricRevision = lyricData.revision
        )
        if (loadKey == currentLoadKey) return

        currentLoadKey = loadKey
        lyricJob?.cancel()
        _uiState.update {
            it.copy(
                lyricText = null,
                lyricOffset = lyricData.offsetMs.toLong()
            )
        }

        lyricJob = viewModelScope.launch {
            val result = LyricsLoader.load(context, request.trackId, request.source)
            if (currentLoadKey != loadKey) return@launch

            _uiState.update {
                it.copy(
                    lyricText = result.text,
                    lyricOffset = lyricData.offsetMs.toLong()
                )
            }
        }
    }

    override fun onCleared() {
        lyricJob?.cancel()
        super.onCleared()
    }

    private data class TrackRequest(
        val trackId: Long,
        val source: String?
    )

    private data class LyricLoadKey(
        val trackId: Long,
        val source: String?,
        val lyricPath: String?,
        val lyricRevision: Int
    )
}
