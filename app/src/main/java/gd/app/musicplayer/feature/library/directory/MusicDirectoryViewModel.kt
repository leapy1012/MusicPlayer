package gd.app.musicplayer.feature.library.directory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.RestartCurrentTrackUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetClickAddQueueEnabledUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetReplaySongEnabledUseCase
import gd.app.musicplayer.domain.usecase.preferences.IsTrackClickOperationEnabledUseCase
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicDirectoryUiState(
    val title: String = "",
    val subtitle: String? = null,
    val entries: List<DirectoryEntry> = emptyList(),
    val hasLoaded: Boolean = false
) {
    val isEmpty: Boolean
        get() = hasLoaded && entries.isEmpty()
}

data class DirectoryPlaybackHighlight(
    val currentMusicId: Long? = null,
    val isPlaying: Boolean = false
)

sealed interface MusicDirectoryEvent {
    data class RestoreScroll(val position: Int, val offset: Int) : MusicDirectoryEvent
    data object OpenNowPlaying : MusicDirectoryEvent
    data class ShowTrackOptions(val music: Music) : MusicDirectoryEvent
}

@HiltViewModel
class MusicDirectoryViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    observeTracksUseCase: ObserveTracksUseCase,
    private val getReplaySongEnabledUseCase: GetReplaySongEnabledUseCase,
    private val getClickAddQueueEnabledUseCase: GetClickAddQueueEnabledUseCase,
    private val isTrackClickOperationEnabledUseCase: IsTrackClickOperationEnabledUseCase,
    private val playTracksUseCase: PlayTracksUseCase,
    private val restartCurrentTrackUseCase: RestartCurrentTrackUseCase,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val rootNode = buildDirectoryRoot(appContext)

    private var currentNode: DirectoryNode = rootNode
    private var tracksByFolder: Map<String, List<Music>> = emptyMap()

    private val _uiState = MutableStateFlow(MusicDirectoryUiState())
    val uiState: StateFlow<MusicDirectoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MusicDirectoryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MusicDirectoryEvent> = _events.asSharedFlow()

    val playbackHighlight: StateFlow<DirectoryPlaybackHighlight> =
        playbackController.state
            .map { state ->
                DirectoryPlaybackHighlight(
                    currentMusicId = state.currentTrack?.id,
                    isPlaying = state.isPlaying
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DirectoryPlaybackHighlight()
            )

    init {
        // Original BActivity.H0("PARAMS_RELOAD_LOCAL"): the grouped track map is rebuilt and the
        // whole visible branch re-scanned whenever the library changes.
        viewModelScope.launch {
            observeTracksUseCase(MusicSet.Tracks).collect { tracks ->
                tracksByFolder = withContext(Dispatchers.IO) { groupTracksByFolder(tracks) }
                refreshCurrentNode(restoreScroll = false)
            }
        }
    }

    val canNavigateUp: Boolean
        get() = currentNode.parent != null

    fun onEntryClicked(entry: DirectoryEntry) {
        when (entry) {
            is DirectoryNode -> openNode(entry)
            is DirectoryTrack -> playTrack(entry)
        }
    }

    fun onTrackMenuClicked(track: DirectoryTrack) {
        viewModelScope.launch {
            _events.emit(MusicDirectoryEvent.ShowTrackOptions(track.music))
        }
    }

    /** `ActivityMusicDirectory.onBackPressed`: walk one level up before leaving the screen. */
    fun navigateUp(): Boolean {
        val parent = currentNode.parent ?: return false
        openNode(parent)
        return true
    }

    fun saveScrollState(position: Int, offset: Int) {
        currentNode.savedPosition = position
        currentNode.savedOffset = offset
    }

    private fun openNode(node: DirectoryNode) {
        currentNode = node
        refreshCurrentNode(restoreScroll = true)
    }

    private fun refreshCurrentNode(restoreScroll: Boolean) {
        val node = currentNode

        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                loadDirectoryChildren(node, tracksByFolder)
            }

            if (currentNode !== node) return@launch

            _uiState.value = MusicDirectoryUiState(
                title = node.name,
                // Original M0: subtitle is the node path, shown only below the synthetic root.
                subtitle = node.path.takeIf { node.parent != null },
                entries = entries,
                hasLoaded = true
            )

            if (restoreScroll) {
                _events.emit(
                    MusicDirectoryEvent.RestoreScroll(
                        position = node.savedPosition,
                        offset = node.savedOffset
                    )
                )
            }
        }
    }

    /**
     * Mirrors `SearchViewModel.onSongClicked` / `l5.m0` click handling: re-tapping the current
     * track replays, pauses or resumes instead of rebuilding the queue. When
     * `preference_click_add_queue` is on, append via original [y6.y.R0] instead of replacing.
     */
    private fun playTrack(track: DirectoryTrack) {
        viewModelScope.launch {
            val playbackState = playbackController.state.value
            val isCurrentTrack = playbackState.currentTrack?.id == track.music.id

            when {
                isCurrentTrack && playbackState.isPlaying && getReplaySongEnabledUseCase() -> {
                    restartCurrentTrackUseCase()
                }

                isCurrentTrack && playbackState.isPlaying -> playbackController.pause()

                isCurrentTrack -> playbackController.play()

                getClickAddQueueEnabledUseCase() -> {
                    // Original w7.v.w1 → y6.y.R0(music, 1): append to playing queue and play.
                    appendAndPlayTrack(track.music)
                }

                else -> {
                    val queue = track.parent.tracks(track.music)
                    val startIndex = queue.indexOf(track.music).takeIf { it >= 0 } ?: 0
                    playTracksUseCase(queue, startIndex)
                }
            }

            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(MusicDirectoryEvent.OpenNowPlaying)
            }
        }
    }

    /** Original [y6.y.R0]: append if missing, jump current index to that track, play. */
    private fun appendAndPlayTrack(track: Music) {
        val queue = playbackController.state.value.queue.toMutableList()
        var index = queue.indexOfFirst { it.id == track.id }
        if (index < 0) {
            queue += track
            index = queue.lastIndex
        }
        playTracksUseCase(queue, index)
    }

    private companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
