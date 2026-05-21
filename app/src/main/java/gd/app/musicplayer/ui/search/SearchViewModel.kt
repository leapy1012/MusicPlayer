package gd.app.musicplayer.ui.search

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
import gd.app.musicplayer.domain.usecase.playlist.ObservePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObserveSelectablePlaylistsUseCase
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
    )

    private val searchSource: StateFlow<SearchSource> =
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
            initialValue = SearchSource(
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                folders = emptyList(),
                playlists = emptyList()
            )
        )

    val sections: StateFlow<List<SearchResultAdapter.SearchSection>> =
        combine(
            searchSource,
            query,
            sortVersion
        ) { source, rawQuery, _ ->
            val normalizedQuery = rawQuery.trim().lowercase()
            buildSections(
                tracks = filterTracks(source.tracks, normalizedQuery),
                albums = filterMusicSets(source.albums, normalizedQuery),
                artists = filterMusicSets(source.artists, normalizedQuery),
                folders = filterMusicSets(source.folders, normalizedQuery),
                playlists = filterPlaylists(source.playlists, normalizedQuery)
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setQuery(query: String) {
        this.query.value = query
    }

    fun refreshSortOrder() {
        sortVersion.value += 1
    }

    suspend fun onSongClicked(song: Music) {
        val playbackState = playbackController.state.value
        val currentTrackId = playbackController.state.value.currentTrack?.id
        val isCurrentTrack = currentTrackId == song.id
        val shouldReplayCurrent =
            getReplaySongEnabledUseCase() && isCurrentTrack && playbackState.isPlaying

        if (shouldReplayCurrent) {
            restartCurrentTrackUseCase(appContext)
            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(SearchEvent.OpenNowPlaying)
            }
            return
        }

        if (isCurrentTrack && playbackState.isPlaying) {
            playbackController.pause(appContext)
            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(SearchEvent.OpenNowPlaying)
            }
            return
        }

        if (isCurrentTrack) {
            playbackController.play(appContext)
            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(SearchEvent.OpenNowPlaying)
            }
            return
        }

        val (queue, startIndex) = resolvePlaybackQueue(song)
        playTracksUseCase(appContext, queue, startIndex)
        if (isTrackClickOperationEnabledUseCase()) {
            _events.emit(SearchEvent.OpenNowPlaying)
        }
    }

    suspend fun resolvePlaybackQueue(clickedTrack: Music): Pair<List<Music>, Int> {
        val queue = if (query.value.isBlank()) {
            searchSource.value.tracks
        } else {
            sections.value.firstOrNull { it.titleRes == R.string.songs }
                ?.items
                ?.mapNotNull { (it as? ListItem.MusicItem)?.music }
                .orEmpty()
        }.ifEmpty { listOf(clickedTrack) }

        val startIndex = queue.indexOfFirst { it.id == clickedTrack.id }
            .takeIf { it >= 0 }
            ?: 0
        return queue to startIndex
    }

    private fun buildSections(
        tracks: List<Music>,
        albums: List<MusicSet>,
        artists: List<MusicSet>,
        folders: List<MusicSet>,
        playlists: List<MusicSet.Playlist>
    ): List<SearchResultAdapter.SearchSection> {
        return buildList {
            if (tracks.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.songs,
                        items = tracks.map { ListItem.MusicItem(it) }
                    )
                )
            }
            if (albums.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.albums,
                        items = albums.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (artists.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.artists,
                        items = artists.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (folders.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.folders,
                        items = folders.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (playlists.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.playlists,
                        items = playlists.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
        }
    }

    private fun filterTracks(
        tracks: List<Music>,
        query: String
    ): List<Music> {
        if (query.isBlank()) return tracks
        return tracks.filter { track ->
            track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true)
        }
    }

    private fun filterMusicSets(
        musicSets: List<MusicSet>,
        query: String
    ): List<MusicSet> {
        if (query.isBlank()) return musicSets
        return musicSets.filter { musicSet ->
            musicSet.name.contains(query, ignoreCase = true)
        }
    }

    private fun filterPlaylists(
        playlists: List<MusicSet.Playlist>,
        query: String
    ): List<MusicSet.Playlist> {
        if (query.isBlank()) return playlists
        return playlists.filter { playlist ->
            playlist.name.contains(query, ignoreCase = true)
        }
    }

}

sealed interface SearchEvent {
    data object OpenNowPlaying : SearchEvent
}
