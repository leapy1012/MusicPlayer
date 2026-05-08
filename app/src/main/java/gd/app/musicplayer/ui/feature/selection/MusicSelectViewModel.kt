package gd.app.musicplayer.ui.feature.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveSortUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.library.UpdateLibrarySortUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObserveSelectablePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.selection.AddSelectedTracksToPlaylistRequest
import gd.app.musicplayer.domain.usecase.selection.AddSelectedTracksToPlaylistResult
import gd.app.musicplayer.domain.usecase.selection.AddSelectedTracksToPlaylistUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    data class ConfirmCompleted(val result: AddSelectedTracksToPlaylistResult) : MusicSelectEvent
}

private enum class BrowseMode {
    TRACKS,
    FOLDERS
}

@HiltViewModel
class MusicSelectViewModel @Inject constructor(
    observeTracksUseCase: ObserveTracksUseCase,
    observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    observeSortUseCase: ObserveSortUseCase,
    private val updateLibrarySortUseCase: UpdateLibrarySortUseCase,
    observeSelectablePlaylistsUseCase: ObserveSelectablePlaylistsUseCase,
    private val addSelectedTracksToPlaylistUseCase: AddSelectedTracksToPlaylistUseCase
) : ViewModel() {

    private val targetPlaylist = MutableStateFlow<MusicSet?>(null)
    private val query = MutableStateFlow("")
    private val browseMode = MutableStateFlow(BrowseMode.TRACKS)
    private val activeSet = MutableStateFlow<MusicSet>(MusicSet.Tracks)
    private val lastTrackSet = MutableStateFlow<MusicSet>(MusicSet.Tracks)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _events = MutableSharedFlow<MusicSelectEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val folders = observeMusicSetsUseCase(MusicSet.Folders)
        .map { sets -> sets.filterIsInstance<MusicSet.Folder>() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val trackSet = activeSet.map(::normalizeTrackSet)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicSet.Tracks)

    private val tracks = trackSet
        .flatMapLatest { observeTracksUseCase(it, isSelectionMode = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val lockedIds = targetPlaylist
        .filterNotNull()
        .flatMapLatest { observeTracksUseCase(it, isSelectionMode = true) }
        .map { list -> list.mapTo(hashSetOf(), Music::id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val playlistOptions = observeSelectablePlaylistsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val spinnerItems = combine(folders, playlistOptions, targetPlaylist, activeSet) { folderItems, playlists, target, selected ->
        if (selected is MusicSet.Folder) {
            folderItems.map { it as MusicSet }
        } else {
            buildList<MusicSet> {
                add(MusicSet.Tracks)
                add(MusicSet.Favorites)
                add(MusicSet.RecentlyAdded)
                playlists.filterNot { playlist -> playlist.id == target?.id }.forEach(::add)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sortState = combine(activeSet, browseMode) { set, mode ->
        if (mode == BrowseMode.FOLDERS) MusicSet.Folders else normalizeTrackSet(set)
    }.flatMapLatest { observeSortUseCase(it, isSelectionMode = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "" to false)

    val uiState: StateFlow<MusicSelectUiState> = combine(
        targetPlaylist,
        browseMode,
        activeSet,
        spinnerItems,
        sortState,
        query,
        folders,
        tracks,
        selectedIds,
        lockedIds
    ) { values ->
        val target = values[0] as MusicSet?
        val mode = values[1] as BrowseMode
        val set = values[2] as MusicSet
        val spinner = values[3] as List<MusicSet>
        val sort = values[4] as Pair<String, Boolean>
        val q = values[5] as String
        val folderList = values[6] as List<MusicSet.Folder>
        val trackList = values[7] as List<Music>
        val selected = values[8] as Set<Long>
        val locked = values[9] as Set<Long>

        val trimmedQuery = q.trim()
        val visibleFolders = if (trimmedQuery.isBlank()) {
            folderList
        } else {
            folderList.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                    it.folderPath.contains(trimmedQuery, ignoreCase = true)
            }
        }
        val visibleSongs = if (trimmedQuery.isBlank()) {
            trackList
        } else {
            trackList.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
        }

        val currentTrackIds = trackList.mapTo(hashSetOf(), Music::id)
        val validSelectedIds = selected.filterTo(hashSetOf()) { id ->
            id in currentTrackIds && id !in locked
        }
        val isBrowsingFolders = mode == BrowseMode.FOLDERS

        MusicSelectUiState(
            targetPlaylist = target,
            header = MusicSelectHeaderState(
                isBrowsingFolders = isBrowsingFolders,
                selectedMusicSet = set,
                spinnerItems = spinner,
                currentSortStyle = sort.first,
                currentSortDescending = sort.second,
                query = trimmedQuery
            ),
            content = MusicSelectContentState(
                folderItems = visibleFolders,
                songItems = visibleSongs,
                isEmpty = if (isBrowsingFolders) visibleFolders.isEmpty() else visibleSongs.isEmpty()
            ),
            actions = MusicSelectActionState(
                selectedSongIds = validSelectedIds,
                lockedSongIds = locked,
                isConfirmEnabled = !isBrowsingFolders && validSelectedIds.isNotEmpty()
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicSelectUiState())

    fun initialize(target: MusicSet) {
        if (targetPlaylist.value != null) return
        targetPlaylist.value = target
    }

    fun onQueryChanged(newValue: String) {
        query.value = newValue
    }

    fun onSwitchSourceClicked() {
        if (browseMode.value == BrowseMode.FOLDERS) {
            browseMode.value = BrowseMode.TRACKS
            activeSet.value = lastTrackSet.value
        } else {
            lastTrackSet.value = normalizeTrackSet(activeSet.value)
            browseMode.value = BrowseMode.FOLDERS
        }
        clearSelection()
    }

    fun onFolderClicked(folder: MusicSet.Folder) {
        if (sameSet(activeSet.value, folder) && browseMode.value == BrowseMode.TRACKS) return
        activeSet.value = folder
        browseMode.value = BrowseMode.TRACKS
        clearSelection()
    }

    fun onSpinnerMusicSetSelected(set: MusicSet) {
        if (sameSet(activeSet.value, set)) return
        activeSet.value = set
        if (set !is MusicSet.Folder) {
            lastTrackSet.value = normalizeTrackSet(set)
        }
        browseMode.value = BrowseMode.TRACKS
        clearSelection()
    }

    fun onSongClicked(song: Music) {
        if (song.id in lockedIds.value) return
        val next = selectedIds.value.toMutableSet()
        if (!next.add(song.id)) next.remove(song.id)
        selectedIds.value = next
    }

    fun onSelectAllClicked() {
        val state = uiState.value
        val selectableIds = state.content.songItems
            .asSequence()
            .map(Music::id)
            .filterNot { it in state.actions.lockedSongIds }
            .toSet()
        if (selectableIds.isEmpty()) return

        val next = state.actions.selectedSongIds.toMutableSet()
        if (next.containsAll(selectableIds)) {
            next.removeAll(selectableIds)
        } else {
            next.addAll(selectableIds)
        }
        selectedIds.value = next
    }

    fun onSortChanged(style: String, descending: Boolean) {
        val targetSet = if (browseMode.value == BrowseMode.FOLDERS) MusicSet.Folders else normalizeTrackSet(activeSet.value)
        viewModelScope.launch {
            updateLibrarySortUseCase(targetSet, style, descending)
        }
    }

    fun onBackPressedInSelection(): Boolean {
        if (browseMode.value == BrowseMode.TRACKS && activeSet.value is MusicSet.Folder) {
            browseMode.value = BrowseMode.FOLDERS
            clearSelection()
            return true
        }
        return false
    }

    fun confirmSelection() {
        val target = targetPlaylist.value as? MusicSet.Playlist ?: return
        if (browseMode.value == BrowseMode.FOLDERS) return

        val selectedIds = uiState.value.actions.selectedSongIds
        if (selectedIds.isEmpty()) return

        val selectedSongs = tracks.value.filter { it.id in selectedIds }
        if (selectedSongs.isEmpty()) return

        viewModelScope.launch {
            val result = addSelectedTracksToPlaylistUseCase(
                AddSelectedTracksToPlaylistRequest(
                    selectedTracks = selectedSongs,
                    targetPlaylist = target
                )
            )
            _events.emit(MusicSelectEvent.ConfirmCompleted(result))
        }
    }

    private fun clearSelection() {
        selectedIds.value = emptySet()
    }

    private fun normalizeTrackSet(set: MusicSet): MusicSet {
        return if (set is MusicSet.TrackCollection) set else MusicSet.Tracks
    }

    private fun sameSet(first: MusicSet, second: MusicSet): Boolean {
        if (first::class != second::class) return false
        return when {
            first is MusicSet.Folder && second is MusicSet.Folder -> first.folderPath == second.folderPath
            first is MusicSet.Playlist && second is MusicSet.Playlist -> first.id == second.id
            else -> first.id == second.id
        }
    }
}
