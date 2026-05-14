package gd.app.musicplayer.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveSortUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.library.UpdateLibrarySortUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObserveSelectablePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.domain.usecase.selection.AddSelectedTracksToPlaylistRequest
import gd.app.musicplayer.domain.usecase.selection.AddSelectedTracksToPlaylistUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicSelectUiState(
    val targetPlaylist: MusicSet? = null,
    val header: MusicSelectHeaderState = MusicSelectHeaderState(),
    val content: MusicSelectContentState = MusicSelectContentState(),
    val actions: MusicSelectActionState = MusicSelectActionState()
)

data class MusicSelectHeaderState(
    val isBrowsingFolders: Boolean = false,
    val selectedMusicSet: MusicSet = MusicSet.Tracks,
    val spinnerItems: List<MusicSet> = emptyList(),
    val currentSortStyle: String = "",
    val currentSortDescending: Boolean = false,
    val query: String = ""
)

data class MusicSelectContentState(
    val folderItems: List<MusicSet.Folder> = emptyList(),
    val songItems: List<Music> = emptyList(),
    val isEmpty: Boolean = true
)

data class MusicSelectActionState(
    val selectedSongIds: Set<Long> = emptySet(),
    val lockedSongIds: Set<Long> = emptySet(),
    val isConfirmEnabled: Boolean = false
)

sealed interface MusicSelectEvent {
    data class ConfirmCompleted(
        val insertedCount: Int
    ) : MusicSelectEvent
}

private enum class BrowseMode {
    TRACKS,
    FOLDERS
}

private data class BrowseState(
    val mode: BrowseMode = BrowseMode.TRACKS,
    val activeSet: MusicSet = MusicSet.Tracks,
    val lastTrackSet: MusicSet = MusicSet.Tracks
)

private data class HeaderInput(
    val target: MusicSet?,
    val browseState: BrowseState,
    val spinnerItems: List<MusicSet>,
    val sort: Pair<String, Boolean>,
    val query: String
)

private data class ContentInput(
    val folders: List<MusicSet.Folder>,
    val tracks: List<Music>,
    val selectedIds: Set<Long>,
    val lockedIds: Set<Long>
)

@HiltViewModel
class MusicSelectViewModel @Inject constructor(
    observeTracksUseCase: ObserveTracksUseCase,
    observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    observeSortUseCase: ObserveSortUseCase,
    private val updateLibrarySortUseCase: UpdateLibrarySortUseCase,
    observeSelectablePlaylistsUseCase: ObserveSelectablePlaylistsUseCase,
    private val addSelectedTracksToPlaylistUseCase: AddSelectedTracksToPlaylistUseCase,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase
) : ViewModel() {

    private val targetPlaylist = MutableStateFlow<MusicSet?>(null)
    private val trackQuery = MutableStateFlow("")
    private val folderQuery = MutableStateFlow("")
    private val browseState = MutableStateFlow(BrowseState())
    private val selectedSongsById = MutableStateFlow<Map<Long, Music>>(emptyMap())

    private val _events = Channel<MusicSelectEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val folders = observeMusicSetsUseCase(MusicSet.Folders)
        .map { sets ->
            sets.filterIsInstance<MusicSet.Folder>()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val trackSet = browseState
        .map { state ->
            normalizeTrackSet(state.activeSet)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MusicSet.Tracks
        )

    private val tracks = trackSet
        .flatMapLatest { set ->
            observeTracksUseCase(
                musicSet = set,
                isSelectionMode = true
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val lockedIds = targetPlaylist
        .filterNotNull()
        .flatMapLatest { target ->
            observeTracksUseCase(
                musicSet = target,
                isSelectionMode = true
            )
        }
        .map { list ->
            list.mapTo(hashSetOf(), Music::id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    private val playlistOptions = observeSelectablePlaylistsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val spinnerItems = combine(
        folders,
        playlistOptions,
        targetPlaylist,
        browseState
    ) { folderItems, playlists, target, state ->
        if (state.activeSet is MusicSet.Folder || state.mode == BrowseMode.FOLDERS) {
            folderItems.map { folder -> folder as MusicSet }
        } else {
            buildTrackSourceItems(
                playlists = playlists,
                target = target
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val sortState = browseState
        .map { state ->
            if (state.mode == BrowseMode.FOLDERS) {
                MusicSet.Folders
            } else {
                normalizeTrackSet(state.activeSet)
            }
        }
        .flatMapLatest { set ->
            observeSortUseCase(
                musicSet = set,
                isSelectionMode = true
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "" to false
        )

    private val headerBase = combine(
        targetPlaylist,
        browseState,
        spinnerItems,
        sortState
    ) { target, state, spinner, sort ->
        HeaderInput(
            target = target,
            browseState = state,
            spinnerItems = spinner,
            sort = sort,
            query = ""
        )
    }

    private val headerInput = combine(
        headerBase,
        trackQuery,
        folderQuery
    ) { base, currentTrackQuery, currentFolderQuery ->
        base.copy(
            query = if (base.browseState.mode == BrowseMode.FOLDERS) {
                currentFolderQuery
            } else {
                currentTrackQuery
            }
        )
    }

    private val contentInput = combine(
        folders,
        tracks,
        selectedSongsById,
        lockedIds
    ) { folderList, trackList, selected, locked ->
        ContentInput(
            folders = folderList,
            tracks = trackList,
            selectedIds = selected.keys,
            lockedIds = locked
        )
    }

    val uiState: StateFlow<MusicSelectUiState> = combine(
        headerInput,
        contentInput
    ) { header, content ->
        buildUiState(
            target = header.target,
            browseState = header.browseState,
            spinnerItems = header.spinnerItems,
            sort = header.sort,
            rawQuery = header.query,
            folders = content.folders,
            tracks = content.tracks,
            selectedIds = content.selectedIds,
            lockedIds = content.lockedIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MusicSelectUiState()
    )

    fun initialize(target: MusicSet) {
        if (targetPlaylist.value != null) return
        if (!isSupportedTarget(target)) return

        targetPlaylist.value = target
    }

    fun onQueryChanged(newValue: String) {
        if (browseState.value.mode == BrowseMode.FOLDERS) {
            folderQuery.value = newValue
        } else {
            trackQuery.value = newValue
        }
    }

    fun onSwitchSourceClicked() {
        browseState.update { state ->
            if (state.mode == BrowseMode.FOLDERS || state.activeSet is MusicSet.Folder) {
                state.copy(
                    mode = BrowseMode.TRACKS,
                    activeSet = state.lastTrackSet
                )
            } else {
                state.copy(
                    mode = BrowseMode.FOLDERS,
                    lastTrackSet = normalizeTrackSet(state.activeSet)
                )
            }
        }
    }

    fun onFolderClicked(folder: MusicSet.Folder) {
        browseState.update { state ->
            if (sameSet(state.activeSet, folder) && state.mode == BrowseMode.TRACKS) {
                state
            } else {
                state.copy(
                    mode = BrowseMode.TRACKS,
                    activeSet = folder
                )
            }
        }
    }

    fun onSpinnerMusicSetSelected(set: MusicSet) {
        browseState.update { state ->
            if (state.mode == BrowseMode.FOLDERS) {
                state.copy(activeSet = set)
            } else if (sameSet(state.activeSet, set)) {
                state
            } else {
                state.copy(
                    mode = BrowseMode.TRACKS,
                    activeSet = set,
                    lastTrackSet = if (set is MusicSet.Folder) {
                        state.lastTrackSet
                    } else {
                        normalizeTrackSet(set)
                    }
                )
            }
        }
    }

    fun onSongClicked(song: Music) {
        if (song.id in lockedIds.value) return

        selectedSongsById.update { current ->
            current.toMutableMap().apply {
                if (containsKey(song.id)) {
                    remove(song.id)
                } else {
                    put(song.id, song)
                }
            }
        }
    }

    fun onSelectAllClicked() {
        val state = uiState.value

        val selectableIds = state.content.songItems
            .asSequence()
            .map { music -> music.id }
            .filterNot { id -> id in state.actions.lockedSongIds }
            .toSet()

        if (selectableIds.isEmpty()) return

        selectedSongsById.update { current ->
            val next = current.toMutableMap()

            if (state.actions.selectedSongIds.containsAll(selectableIds)) {
                selectableIds.forEach(next::remove)
            } else {
                state.content.songItems
                    .asSequence()
                    .filterNot { music -> music.id in state.actions.lockedSongIds }
                    .forEach { music -> next[music.id] = music }
            }

            next
        }
    }

    fun onSortChanged(
        style: String,
        descending: Boolean
    ) {
        val state = browseState.value

        val targetSet = if (state.mode == BrowseMode.FOLDERS) {
            MusicSet.Folders
        } else {
            normalizeTrackSet(state.activeSet)
        }

        viewModelScope.launch {
            updateLibrarySortUseCase(
                musicSet = targetSet,
                sortStyle = style,
                descending = descending,
                true
            )
        }
    }

    fun onBackPressedInSelection(): Boolean {
        val state = browseState.value

        if (state.mode == BrowseMode.TRACKS && state.activeSet is MusicSet.Folder) {
            browseState.update {
                it.copy(mode = BrowseMode.FOLDERS)
            }
            return true
        }

        return false
    }

    fun confirmSelection() {
        val target = targetPlaylist.value ?: return
        val state = uiState.value

        if (state.header.isBrowsingFolders) return

        val selectedSongs = selectedSongsById.value.values
            .filterNot { music -> music.id in state.actions.lockedSongIds }

        if (selectedSongs.isEmpty()) return

        viewModelScope.launch {
            val insertedCount = when (target) {
                is MusicSet.Playlist -> {
                    addSelectedTracksToPlaylistUseCase(
                        AddSelectedTracksToPlaylistRequest(
                            selectedTracks = selectedSongs,
                            targetPlaylist = target
                        )
                    ).insertedCount
                }

                is MusicSet.Favorites -> {
                    addSelectedTracksToFavorites(selectedSongs)
                }

                else -> 0
            }

            _events.send(
                MusicSelectEvent.ConfirmCompleted(
                    insertedCount = insertedCount
                )
            )
        }
    }

    private suspend fun addSelectedTracksToFavorites(
        selectedSongs: List<Music>
    ): Int {
        var insertedCount = 0

        selectedSongs.forEach { music ->
            val favorited = toggleFavoriteTrackUseCase(music.id)

            if (favorited) {
                insertedCount++
            }
        }

        return insertedCount
    }

    private fun buildUiState(
        target: MusicSet?,
        browseState: BrowseState,
        spinnerItems: List<MusicSet>,
        sort: Pair<String, Boolean>,
        rawQuery: String,
        folders: List<MusicSet.Folder>,
        tracks: List<Music>,
        selectedIds: Set<Long>,
        lockedIds: Set<Long>
    ): MusicSelectUiState {
        val trimmedQuery = rawQuery.trim()
        val visibleFolders = folders.filterFoldersByQuery(trimmedQuery)
        val visibleSongs = tracks.filterTracksByQuery(trimmedQuery)
        val validSelectedIds = selectedIds.filterTo(hashSetOf()) { id ->
            id !in lockedIds
        }

        val isBrowsingFolders = browseState.mode == BrowseMode.FOLDERS

        return MusicSelectUiState(
            targetPlaylist = target,
            header = MusicSelectHeaderState(
                isBrowsingFolders = isBrowsingFolders,
                selectedMusicSet = browseState.activeSet,
                spinnerItems = spinnerItems,
                currentSortStyle = sort.first,
                currentSortDescending = sort.second,
                query = trimmedQuery
            ),
            content = MusicSelectContentState(
                folderItems = visibleFolders,
                songItems = visibleSongs,
                isEmpty = if (isBrowsingFolders) {
                    visibleFolders.isEmpty()
                } else {
                    visibleSongs.isEmpty()
                }
            ),
            actions = MusicSelectActionState(
                selectedSongIds = validSelectedIds,
                lockedSongIds = lockedIds,
                isConfirmEnabled = !isBrowsingFolders && validSelectedIds.isNotEmpty()
            )
        )
    }

    private fun List<MusicSet.Folder>.filterFoldersByQuery(
        query: String
    ): List<MusicSet.Folder> {
        if (query.isBlank()) return this

        return filter { folder ->
            folder.name.contains(query, ignoreCase = true) ||
                    folder.folderPath.contains(query, ignoreCase = true)
        }
    }

    private fun List<Music>.filterTracksByQuery(
        query: String
    ): List<Music> {
        if (query.isBlank()) return this

        return filter { music ->
            music.title.contains(query, ignoreCase = true) ||
                    music.artist.orEmpty().contains(query, ignoreCase = true) ||
                    music.album.orEmpty().contains(query, ignoreCase = true) ||
                    music.data.orEmpty().contains(query, ignoreCase = true)
        }
    }

    private fun buildTrackSourceItems(
        playlists: List<MusicSet.Playlist>,
        target: MusicSet?
    ): List<MusicSet> {
        return buildList {
            add(MusicSet.Tracks)

            if (target !is MusicSet.Favorites) {
                add(MusicSet.Favorites)
            }

            add(MusicSet.RecentlyPlayed)
            add(MusicSet.RecentlyAdded)
            add(MusicSet.MostPlayed)

            playlists
                .filterNot { playlist ->
                    target is MusicSet.Playlist && playlist.id == target.id
                }
                .forEach(::add)
        }
    }

    private fun normalizeTrackSet(set: MusicSet): MusicSet {
        return if (set is MusicSet.TrackCollection) {
            set
        } else {
            MusicSet.Tracks
        }
    }

    private fun sameSet(
        first: MusicSet,
        second: MusicSet
    ): Boolean {
        if (first::class != second::class) return false

        return when {
            first is MusicSet.Folder && second is MusicSet.Folder -> {
                first.folderPath == second.folderPath
            }

            first is MusicSet.Playlist && second is MusicSet.Playlist -> {
                first.id == second.id
            }

            else -> first.id == second.id
        }
    }

    private fun isSupportedTarget(target: MusicSet): Boolean {
        return target is MusicSet.Playlist ||
                target is MusicSet.Favorites
    }
}
