package gd.app.musicplayer.ui.scan

import android.content.Context
import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.scan.QueryMediaStoreTracksUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ScanFolderItem(
    val path: String,
    val name: String,
    val trackCount: Int,
    @DrawableRes val iconRes: Int
)

data class ScanSettingUiState(
    val currentPath: String = ROOT_PATH,
    val currentName: String = "",
    val selectedPaths: Set<String> = emptySet(),
    val items: List<ScanFolderItem> = emptyList(),
    val restoreScroll: Boolean = false,
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0
)

@HiltViewModel
class ScanSettingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    private val queryMediaStoreTracksUseCase: QueryMediaStoreTracksUseCase,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScanSettingUiState(
            currentName = appContext.getString(R.string.scan_specified_folder)
        )
    )
    val uiState: StateFlow<ScanSettingUiState> = _uiState.asStateFlow()

    private val rootNode = BrowserFolderNode(
        path = ROOT_PATH,
        name = appContext.getString(R.string.music_directory),
        iconRes = R.drawable.main_folder_simple,
        parent = null,
        childrenLoaded = true
    )

    private var currentNode: BrowserFolderNode = rootNode
    private var musicFolderCounts: Map<String, Int> = emptyMap()

    fun load(initialSelectedPaths: Collection<String>) {
        if (rootNode.children.isNotEmpty()) return

        _uiState.update { state ->
            state.copy(
                selectedPaths = initialSelectedPaths
                    .map(::normalizeSelectedPath)
                    .filter(String::isNotBlank)
                    .toCollection(linkedSetOf())
            )
        }

        rootNode.children = storageRootNodes()
        renderCurrentNode(restoreScroll = false)

        viewModelScope.launch(dispatchers.io) {
            musicFolderCounts = loadMusicFolderCounts()
            renderCurrentNode(restoreScroll = false)
        }
    }

    fun openFolder(
        itemPath: String,
        scrollPosition: Int,
        scrollOffset: Int
    ) {
        val nextNode = currentNode.children.firstOrNull { node -> node.path == itemPath } ?: return
        currentNode.scrollPosition = scrollPosition
        currentNode.scrollOffset = scrollOffset

        viewModelScope.launch(dispatchers.io) {
            loadChildrenIfNeeded(nextNode)
            if (nextNode.children.isEmpty()) {
                _events.update { ScanSettingEvent.NoSubfolders }
                return@launch
            }

            currentNode = nextNode
            renderCurrentNode(restoreScroll = false)
        }
    }

    fun navigateUp(): Boolean {
        val parent = currentNode.parent ?: return false
        currentNode = parent
        renderCurrentNode(restoreScroll = true)
        return true
    }

    fun setSelected(path: String, selected: Boolean) {
        val selectedPath = normalizeSelectedPath(path)
        _uiState.update { state ->
            val selectedPaths = LinkedHashSet(state.selectedPaths)
            if (selected) {
                selectedPaths += selectedPath
            } else {
                selectedPaths -= selectedPath
            }
            state.copy(selectedPaths = selectedPaths)
        }
    }

    private val _events = MutableStateFlow<ScanSettingEvent?>(null)
    val events: StateFlow<ScanSettingEvent?> = _events.asStateFlow()

    fun consumeEvent() {
        _events.value = null
    }

    private fun renderCurrentNode(restoreScroll: Boolean) {
        val node = currentNode
        _uiState.update { state ->
            state.copy(
                currentPath = node.path,
                currentName = node.name,
                items = node.children.map { child -> child.toItem() },
                restoreScroll = restoreScroll,
                scrollPosition = if (restoreScroll) node.scrollPosition else 0,
                scrollOffset = if (restoreScroll) node.scrollOffset else 0
            )
        }
    }

    private fun BrowserFolderNode.toItem(): ScanFolderItem {
        return ScanFolderItem(
            path = path,
            name = name,
            trackCount = countTracksUnder(path),
            iconRes = iconRes
        )
    }

    private fun countTracksUnder(path: String): Int {
        val selectedPath = normalizeSelectedPath(path)
        return musicFolderCounts.entries.sumOf { (folderPath, count) ->
            if (folderPath.startsWith(selectedPath)) count else 0
        }
    }

    private fun storageRootNodes(): MutableList<BrowserFolderNode> {
        return storageRoots(appContext).mapIndexed { index, path ->
            BrowserFolderNode(
                path = normalizeSelectedPath(path),
                name = when (index) {
                    0 -> appContext.getString(R.string.internal_storage)
                    1 -> appContext.getString(R.string.sd_card)
                    else -> appContext.getString(R.string.sd_card) + (index - 1)
                },
                iconRes = if (index == 0) {
                    R.drawable.vector_internal_storage
                } else {
                    R.drawable.vector_sd_card
                },
                parent = rootNode
            )
        }.toMutableList()
    }

    private fun loadChildrenIfNeeded(node: BrowserFolderNode) {
        if (node.childrenLoaded) return

        val directory = File(node.path)
        val children = directory.listFiles()
            .orEmpty()
            .filter { file -> file.isDirectory && !file.isHidden }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { file -> file.name })
            .map { file ->
                BrowserFolderNode(
                    path = normalizeSelectedPath(file.absolutePath),
                    name = file.name,
                    iconRes = R.drawable.main_folder_simple,
                    parent = node
                )
            }

        node.children = children.toMutableList()
        node.childrenLoaded = true
    }

    private suspend fun loadMusicFolderCounts(): Map<String, Int> {
        val fromLibrary = observeMusicSetsUseCase(MusicSet.Folders)
            .first()
            .filterIsInstance<MusicSet.Folder>()
            .associate { folder ->
                normalizeSelectedPath(folder.folderPath) to folder.musicCount
            }

        val fromMediaStore = runCatching {
            queryMediaStoreTracksUseCase(appContext)
                .groupingBy { track -> normalizeSelectedPath(track.folderPath) }
                .eachCount()
                .filterKeys(String::isNotBlank)
        }.getOrDefault(emptyMap())

        return if (fromMediaStore.isNotEmpty()) fromMediaStore else fromLibrary
    }

    private data class BrowserFolderNode(
        val path: String,
        val name: String,
        @DrawableRes val iconRes: Int,
        val parent: BrowserFolderNode?,
        var childrenLoaded: Boolean = false,
        var children: MutableList<BrowserFolderNode> = mutableListOf(),
        var scrollPosition: Int = 0,
        var scrollOffset: Int = 0
    )
}

sealed interface ScanSettingEvent {
    object NoSubfolders : ScanSettingEvent
}

const val ROOT_PATH = "/"

private fun storageRoots(context: Context): List<String> {
    val roots = linkedSetOf<String>()

    Environment.getExternalStorageDirectory()?.absolutePath
        ?.let(::normalizeSelectedPath)
        ?.takeIf(String::isNotBlank)
        ?.let(roots::add)

    context.getExternalFilesDirs(null)
        .orEmpty()
        .mapNotNull { file -> file?.absolutePath?.substringBefore("/Android/") }
        .map(::normalizeSelectedPath)
        .filter(String::isNotBlank)
        .forEach(roots::add)

    return roots.toList()
}

fun normalizeSelectedPath(path: String?): String {
    val normalized = path.orEmpty()
        .replace('\\', '/')
        .trimEnd('/')

    if (normalized.isBlank()) return ""
    return "$normalized/"
}
