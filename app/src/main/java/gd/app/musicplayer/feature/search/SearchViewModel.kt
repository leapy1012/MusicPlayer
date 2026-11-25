package gd.app.musicplayer.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.RestartCurrentTrackUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObserveSelectablePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetClickAddQueueEnabledUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetReplaySongEnabledUseCase
import gd.app.musicplayer.domain.usecase.preferences.IsTrackClickOperationEnabledUseCase
import gd.app.musicplayer.playback.PlaybackController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val observeTracksUseCase: ObserveTracksUseCase,
    private val observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    private val observeSelectablePlaylistsUseCase: ObserveSelectablePlaylistsUseCase,
    private val getReplaySongEnabledUseCase: GetReplaySongEnabledUseCase,
    private val getClickAddQueueEnabledUseCase: GetClickAddQueueEnabledUseCase,
    private val isTrackClickOperationEnabledUseCase: IsTrackClickOperationEnabledUseCase,
    private val playTracksUseCase: PlayTracksUseCase,
    private val restartCurrentTrackUseCase: RestartCurrentTrackUseCase,
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sortVersion = MutableStateFlow(0)
    private val _events = MutableSharedFlow<SearchEvent>()
    val events: SharedFlow<SearchEvent> = _events.asSharedFlow()

    private data class SearchSource(
        val tracks: List<Music>,
        val albums: List<MusicSet>,
        val artists: List<MusicSet>,
        val folders: List<MusicSet>,
        val playlists: List<MusicSet.Playlist>
    ) {
        val index = SearchIndex(tracks, albums, artists, folders, playlists)
    }

    // null means the sources have not all emitted yet; an empty snapshot is real data.
    private val searchSource: StateFlow<SearchSource?> =
        combine(
            observeTracksUseCase(MusicSet.Tracks),
            observeMusicSetsUseCase(MusicSet.Albums),
            observeMusicSetsUseCase(MusicSet.Artists),
            observeMusicSetsUseCase(MusicSet.Folders),
            observeSelectablePlaylistsUseCase()
        ) { tracks, albums, artists, folders, playlists ->
            SearchSource(
                tracks = tracks,
                albums = albums,
                artists = artists,
                folders = folders,
                playlists = playlists
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** null is initial loading; loaded results always include the query used to filter them. */
    val results: StateFlow<SearchResults?> =
        combine(
            searchSource,
            query,
            sortVersion
        ) { source, rawQuery, _ ->
            if (source == null) return@combine null
            source.index.search(rawQuery)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun setQuery(query: String) {
        this.query.value = query
    }

    fun refreshSortOrder() {
        sortVersion.value += 1
    }

    suspend fun onSongClicked(song: Music, preferredIndex: Int? = null) {
        val playbackState = playbackController.state.value
        val currentTrackId = playbackController.state.value.currentTrack?.id
        val isCurrentTrack = currentTrackId == song.id
        val shouldReplayCurrent =
            getReplaySongEnabledUseCase() && isCurrentTrack && playbackState.isPlaying

        if (shouldReplayCurrent) {
            restartCurrentTrackUseCase()
            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(SearchEvent.OpenNowPlaying)
            }
            return
        }

        if (isCurrentTrack && playbackState.isPlaying) {
            playbackController.pause()
            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(SearchEvent.OpenNowPlaying)
            }
            return
        }

        if (isCurrentTrack) {
            playbackController.play()
            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(SearchEvent.OpenNowPlaying)
            }
            return
        }

        if (getClickAddQueueEnabledUseCase()) {
            // Original w7.v.w1 → y6.y.R0(music, 1): append to playing queue and play.
            appendAndPlayTrack(song)
        } else {
            val (queue, startIndex) = resolvePlaybackQueue(song, preferredIndex)
            playTracksUseCase(queue, startIndex)
        }
        if (isTrackClickOperationEnabledUseCase()) {
            _events.emit(SearchEvent.OpenNowPlaying)
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

    suspend fun resolvePlaybackQueue(
        clickedTrack: Music,
        preferredIndex: Int? = null
    ): Pair<List<Music>, Int> {
        val result = results.value
        val queue = if (result == null || result.query.isBlank()) {
            searchSource.value?.tracks.orEmpty()
        } else {
            result.sections.firstOrNull { it.titleRes == R.string.songs }
                ?.items
                ?.mapNotNull { (it as? ListItem.MusicItem)?.music }
                .orEmpty()
        }.ifEmpty { listOf(clickedTrack) }

        val startIndex = preferredIndex
            ?.takeIf { it in queue.indices }
            ?.takeIf { queue[it] == clickedTrack }
            ?: queue.indexOf(clickedTrack).takeIf { it >= 0 }
            ?: queue.indexOfFirst { it.id == clickedTrack.id }.takeIf { it >= 0 }
            ?: 0
        return queue to startIndex
    }

}

sealed interface SearchEvent {
    data object OpenNowPlaying : SearchEvent
}
