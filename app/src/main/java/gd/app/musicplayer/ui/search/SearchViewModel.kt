package gd.app.musicplayer.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.RestartCurrentTrackUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetQueueForSearchingModeUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetReplaySongEnabledUseCase
import gd.app.musicplayer.domain.usecase.preferences.IsTrackClickOperationEnabledUseCase
import gd.app.musicplayer.domain.usecase.search.ObserveDefaultSearchMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.search.ObserveDefaultSearchPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.search.ObserveDefaultSearchTracksUseCase
import gd.app.musicplayer.domain.usecase.search.ObserveSearchMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.search.ObserveSearchPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.search.ObserveSearchTracksUseCase
import gd.app.musicplayer.domain.usecase.search.SortSearchResultsUseCase
import gd.app.musicplayer.playback.PlaybackController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val observeDefaultSearchTracksUseCase: ObserveDefaultSearchTracksUseCase,
    private val observeSearchTracksUseCase: ObserveSearchTracksUseCase,
    private val observeDefaultSearchMusicSetsUseCase: ObserveDefaultSearchMusicSetsUseCase,
    private val observeSearchMusicSetsUseCase: ObserveSearchMusicSetsUseCase,
    private val observeDefaultSearchPlaylistsUseCase: ObserveDefaultSearchPlaylistsUseCase,
    private val observeSearchPlaylistsUseCase: ObserveSearchPlaylistsUseCase,
    private val getReplaySongEnabledUseCase: GetReplaySongEnabledUseCase,
    private val isTrackClickOperationEnabledUseCase: IsTrackClickOperationEnabledUseCase,
    private val getQueueForSearchingModeUseCase: GetQueueForSearchingModeUseCase,
    private val sortSearchResultsUseCase: SortSearchResultsUseCase,
    private val playTracksUseCase: PlayTracksUseCase,
    private val restartCurrentTrackUseCase: RestartCurrentTrackUseCase,
    private val observeTracksUseCase: ObserveTracksUseCase,
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

    val sections: StateFlow<List<SearchResultAdapter.SearchSection>> =
        combine(
            query.debounce(200).distinctUntilChanged(),
            sortVersion
        ) { rawQuery, _ -> rawQuery.trim() }
            .flatMapLatest(::observeSearchSource)
            .map { source ->
                buildSections(
                    tracks = source.tracks,
                    albums = source.albums,
                    artists = source.artists,
                    folders = source.folders,
                    playlists = source.playlists
                )
            }
            .stateIn(
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

    suspend fun onSongClicked(song: Music, currentTrackId: Long?) {
        val shouldReplayCurrent =
            getReplaySongEnabledUseCase() && currentTrackId == song.id

        if (shouldReplayCurrent) {
            restartCurrentTrackUseCase(appContext)
            if (isTrackClickOperationEnabledUseCase()) {
                _events.emit(SearchEvent.OpenQueueScreen)
            }
            return
        }

        val (queue, startIndex) = resolvePlaybackQueue(song)
        playTracksUseCase(appContext, queue, startIndex)
        if (isTrackClickOperationEnabledUseCase()) {
            _events.emit(SearchEvent.OpenQueueScreen)
        }
    }

    suspend fun resolvePlaybackQueue(clickedTrack: Music): Pair<List<Music>, Int> {
//        val queue = if (getQueueForSearchingModeUseCase() == 0) {
//            observeTracksUseCase(MusicSet.Tracks)
//        } else {
//            sections.value.firstOrNull { section ->
//                section.items.firstOrNull() is ListItem.MusicItem
//            }?.items?.mapNotNull { (it as? ListItem.MusicItem)?.music }.orEmpty()
//        }.ifEmpty { listOf(clickedTrack) }
//
//        val startIndex = queue.indexOfFirst { it.id == clickedTrack.id }.coerceAtLeast(0)
//        return queue to startIndex
        TODO()
    }

    private fun observeSearchSource(query: String) = combine(
        observeTracksSource(query),
        observeBrowseSource(query, MusicSet.Albums),
        observeBrowseSource(query, MusicSet.Artists),
        observeBrowseSource(query, MusicSet.Folders),
        observePlaylistsSource(query)
    ) { tracks, albums, artists, folders, playlists ->
        SearchSource(
            tracks = tracks,
            albums = albums,
            artists = artists,
            folders = folders,
            playlists = playlists
        )
    }

    private fun observeTracksSource(query: String) =
        if (query.isBlank()) {
            observeDefaultSearchTracksUseCase()
        } else {
            observeSearchTracksUseCase(query)
        }

    private fun observeBrowseSource(query: String, musicSet: MusicSet) =
        if (query.isBlank()) {
            observeDefaultSearchMusicSetsUseCase(musicSet)
        } else {
            observeSearchMusicSetsUseCase(musicSet, query)
        }

    private fun observePlaylistsSource(query: String) =
        if (query.isBlank()) {
            observeDefaultSearchPlaylistsUseCase()
        } else {
            observeSearchPlaylistsUseCase(query)
        }

    private fun buildSections(
        tracks: List<Music>,
        albums: List<MusicSet>,
        artists: List<MusicSet>,
        folders: List<MusicSet>,
        playlists: List<MusicSet.Playlist>
    ): List<SearchResultAdapter.SearchSection> {
        val sortedTracks = sortSearchResultsUseCase.sortTracks(tracks, sortVersion.value)
        val sortedAlbums = sortSearchResultsUseCase.sortAlbums(albums)
        val sortedArtists = sortSearchResultsUseCase.sortArtists(artists)
        val sortedFolders = sortSearchResultsUseCase.sortFolders(folders)
        val sortedPlaylists = sortSearchResultsUseCase.sortPlaylists(playlists)

        return buildList {
            if (sortedTracks.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.songs,
                        items = sortedTracks.map { ListItem.MusicItem(it) }
                    )
                )
            }
            if (sortedAlbums.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.albums,
                        items = sortedAlbums.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (sortedArtists.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.artists,
                        items = sortedArtists.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (sortedFolders.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.folders,
                        items = sortedFolders.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
            if (sortedPlaylists.isNotEmpty()) {
                add(
                    SearchResultAdapter.SearchSection(
                        titleRes = R.string.playlists,
                        items = sortedPlaylists.map { ListItem.MusicSetItem(it) }
                    )
                )
            }
        }
    }

}

sealed interface SearchEvent {
    data object OpenQueueScreen : SearchEvent
}
