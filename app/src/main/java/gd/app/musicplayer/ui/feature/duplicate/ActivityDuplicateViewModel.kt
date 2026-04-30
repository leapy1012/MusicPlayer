package gd.app.musicplayer.ui.feature.duplicate

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ActivityDuplicateViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val observeTracksUseCase: ObserveTracksUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicateFinderUiState())
    val uiState: StateFlow<DuplicateFinderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DuplicateFinderEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<DuplicateFinderEvent> = _events.asSharedFlow()

    private var scanJob: Job? = null
    private var hasStartedScan = false

    fun startScanIfNeeded() {
        if (hasStartedScan || scanJob?.isActive == true) return
        startScan()
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun toggleTrackSelection(trackId: Long) {
        _uiState.update { state ->
            val selectedIds = state.selectedIds.toMutableSet()
            if (!selectedIds.add(trackId)) {
                selectedIds.remove(trackId)
            }
            state.copy(
                selectedIds = selectedIds,
                allDuplicatesSelected = areAllDuplicatesSelected(state.groups, selectedIds)
            )
        }
    }

    fun toggleGroupExpansion(groupKey: String) {
        _uiState.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.key == groupKey) group.copy(expanded = !group.expanded) else group
                }
            )
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val selectedIds = if (state.allDuplicatesSelected) {
                emptySet()
            } else {
                buildDefaultSelection(state.groups)
            }
            state.copy(
                selectedIds = selectedIds,
                allDuplicatesSelected = areAllDuplicatesSelected(state.groups, selectedIds)
            )
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        if (state.isDeleting || state.selectedIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            val selectedTracks = state.groups
                .asSequence()
                .flatMap { it.tracks.asSequence() }
                .filter { it.id in state.selectedIds }
                .distinctBy(Music::id)
                .toList()

            val deletedCount = withContext(dispatchers.io) {
                deleteTracksUseCase(selectedTracks)
            }

            if (deletedCount > 0) {
                val deletedIds = selectedTracks.mapTo(hashSetOf(), Music::id)
                _uiState.update { current ->
                    val updatedGroups = current.groups
                        .mapNotNull { group ->
                            val remainingTracks = group.tracks.filterNot { it.id in deletedIds }
                            if (remainingTracks.size > 1) {
                                group.copy(tracks = remainingTracks)
                            } else {
                                null
                            }
                        }
                    val selectedIds = buildDefaultSelection(updatedGroups)
                    current.copy(
                        groups = updatedGroups,
                        selectedIds = selectedIds,
                        allDuplicatesSelected = areAllDuplicatesSelected(updatedGroups, selectedIds),
                        isDeleting = false
                    )
                }
                _events.tryEmit(DuplicateFinderEvent.DeleteCompleted(deletedCount))
            } else {
                _uiState.update { it.copy(isDeleting = false) }
                _events.tryEmit(DuplicateFinderEvent.DeleteFailed)
            }
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        hasStartedScan = true
        scanJob = viewModelScope.launch {
            val tracks = withContext(dispatchers.io) {
                observeTracksUseCase(MusicSet.Tracks).first().distinctBy(Music::id)
            }

            _uiState.value = DuplicateFinderUiState(
                isScanning = true,
                scannedCount = 0,
                totalCount = tracks.size
            )

            val duplicateGroups = withContext(dispatchers.io) {
                findDuplicateGroups(tracks) { index, total, music ->
                    _uiState.value = _uiState.value.copy(
                        isScanning = true,
                        scannedCount = index,
                        totalCount = total,
                        currentTrackTitle = music.title
                    )
                }
            }

            val selectedIds = buildDefaultSelection(duplicateGroups)
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scannedCount = tracks.size,
                totalCount = tracks.size,
                currentTrackTitle = "",
                groups = duplicateGroups,
                selectedIds = selectedIds,
                allDuplicatesSelected = areAllDuplicatesSelected(duplicateGroups, selectedIds)
            )
        }.also { job ->
            job.invokeOnCompletion {
                if (scanJob === job) {
                    scanJob = null
                }
            }
        }
    }

    private suspend fun findDuplicateGroups(
        tracks: List<Music>,
        onProgress: (index: Int, total: Int, music: Music) -> Unit
    ): List<DuplicateGroup> {
        val tracksByHash = LinkedHashMap<String, MutableList<Music>>()
        val duplicateHashes = LinkedHashSet<String>()
        val total = tracks.size

        tracks.forEachIndexed { index, music ->
            currentCoroutineContext().ensureActive()
            onProgress(index, total, music)

            val hash = computeMd5(music) ?: return@forEachIndexed
            val existingTracks = tracksByHash.getOrPut(hash) { mutableListOf() }
            if (existingTracks.isNotEmpty()) {
                duplicateHashes += hash
            }
            existingTracks += music
        }

        return duplicateHashes.mapIndexed { index, hash ->
            val groupTracks = tracksByHash.getValue(hash).toList()
            DuplicateGroup(
                key = "${hash}_${index}_${groupTracks.first().id}",
                tracks = groupTracks,
                expanded = false
            )
        }
    }

    private fun computeMd5(music: Music): String? {
        val contentResolver = appContext.contentResolver
        val mediaUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            music.id
        )
        val stream = tryOpenInputStream(contentResolver, mediaUri, music) ?: return null

        return stream.use { input ->
            runCatching {
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            }.getOrNull()
        }
    }

    private fun tryOpenInputStream(
        contentResolver: ContentResolver,
        mediaUri: Uri,
        music: Music
    ): InputStream? {
        val contentStream = runCatching {
            contentResolver.openInputStream(mediaUri)
        }.getOrNull()
        if (contentStream != null) return contentStream

        val filePath = music.data?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            FileInputStream(filePath)
        }.getOrNull()
    }

    private fun buildDefaultSelection(groups: List<DuplicateGroup>): Set<Long> {
        return buildSet {
            groups.forEach { group ->
                group.tracks.drop(1).forEach { add(it.id) }
            }
        }
    }

    private fun areAllDuplicatesSelected(
        groups: List<DuplicateGroup>,
        selectedIds: Set<Long>
    ): Boolean {
        if (groups.isEmpty()) return false
        return groups.all { group ->
            group.tracks.withIndex().all { (index, track) ->
                if (index == 0) {
                    track.id !in selectedIds
                } else {
                    track.id in selectedIds
                }
            }
        }
    }
}

data class DuplicateFinderUiState(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentTrackTitle: String = "",
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val allDuplicatesSelected: Boolean = false,
    val isDeleting: Boolean = false
) {
    val hasGroups: Boolean
        get() = groups.isNotEmpty()
}

data class DuplicateGroup(
    val key: String,
    val tracks: List<Music>,
    val expanded: Boolean
)

sealed interface DuplicateFinderEvent {
    data class DeleteCompleted(val count: Int) : DuplicateFinderEvent
    data object DeleteFailed : DuplicateFinderEvent
}
