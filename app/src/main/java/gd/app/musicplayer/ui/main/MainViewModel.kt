package gd.app.musicplayer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.data.repository.MainRepo
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.R
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.SmartPlaylistPreferenceOps
import gd.app.musicplayer.util.SortPreferenceOps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class MainUiState(
    val items: List<MainItem> = emptyList(),
    val playlists: List<MusicSet.Playlist> = emptyList(),
    val playlistCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: MainRepo,
    private val preferenceUtil: PreferenceUtil,
    private val dispatchers: AppDispatchers
) : ViewModel() {
    private val playlistDisplayOrderIds = MutableStateFlow<List<Long>>(emptyList())

    val playlists: StateFlow<List<MusicSet.Playlist>> = combine(
        repo.observePlaylists(),
        preferenceUtil.observePreferenceChanges(
            SortPreferenceOps.KEY_PLAYLIST_SORT_STYLE,
            SortPreferenceOps.KEY_PLAYLIST_SORT_REVERSE
        ).map {
            preferenceUtil.getPlaylistSortStyle() to preferenceUtil.isPlaylistSortReversed()
        }
    ) { playlists, (style, reversed) ->
        sortPlaylists(playlists, style, reversed)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val smartPlaylistConfig: Flow<SmartPlaylistPreferenceOps.SmartPlaylistConfig> =
        preferenceUtil.observePreferenceChanges(
            SmartPlaylistPreferenceOps.KEY_PLAYLIST_TRACK_LIMIT_TIME,
            SmartPlaylistPreferenceOps.KEY_PLAYLIST_TRACK_LIMIT
        )
            .map { preferenceUtil.getSmartPlaylistConfig() }
            .distinctUntilChanged()

    val items: StateFlow<List<MainItem>> = smartPlaylistConfig.flatMapLatest { config ->
        combine(
            repo.observeTracksCount(),
            repo.observeFolderCount(),
            repo.observeFavoriteCount(),
            repo.observeRecentPlayCount(
                playlistWindowMs = config.windowDurationMs,
                windowStartMs = config.windowStartMs,
                playlistLimit = config.playlistLimit
            ),
            repo.observeRecentAddCount(
                playlistWindowMs = config.windowDurationMs,
                windowStartMs = config.windowStartMs,
                playlistLimit = config.playlistLimit
            ),
            repo.observeMostPlayCount(
                playlistWindowMs = config.windowDurationMs,
                windowStartMs = config.windowStartMs,
                playlistLimit = config.playlistLimit
            )
        ) { values ->
            buildMainItems(
                libraryCount = values[0],
                folderCount = values[1],
                favoriteCount = values[2],
                recentPlayCount = values[3],
                recentAddCount = values[4],
                mostPlayCount = values[5]
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildMainItems(
            libraryCount = 0,
            folderCount = 0,
            favoriteCount = 0,
            recentPlayCount = 0,
            recentAddCount = 0,
            mostPlayCount = 0
        )
    )

    val uiState: StateFlow<MainUiState> = combine(
        items,
        playlists,
        playlistDisplayOrderIds
    ) { items, playlists, displayOrderIds ->
        MainUiState(
            items = items,
            playlists = playlists,
            playlistCount = playlists.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(items = items.value)
    )

    private fun buildMainItems(
        libraryCount: Int,
        folderCount: Int,
        favoriteCount: Int,
        recentPlayCount: Int,
        recentAddCount: Int,
        mostPlayCount: Int
    ): List<MainItem> {

        return listOf(
            MainItem(
                titleRes = R.string.library,
                iconRes = R.drawable.main_local,
                bgColor = -867723789,
                count = libraryCount
            ),
            MainItem(
                titleRes = R.string.folder,
                iconRes = R.drawable.main_folder,
                bgColor = -855992486,
                count = folderCount
            ),
            MainItem(
                titleRes = R.string.favorite,
                iconRes = R.drawable.main_favourite,
                bgColor = -856058475,
                count = favoriteCount
            ),
            MainItem(
                titleRes = R.string.recent_play,
                iconRes = R.drawable.main_recent_play,
                bgColor = -864305174,
                count = recentPlayCount
            ),
            MainItem(
                titleRes = R.string.recent_add,
                iconRes = R.drawable.main_recent_add,
                bgColor = -872359528,
                count = recentAddCount
            ),
            MainItem(
                titleRes = R.string.most_play,
                iconRes = R.drawable.main_most_play,
                bgColor = -859467278,
                count = mostPlayCount
            )
        )
    }


    fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        if (playlistIdsInDisplayOrder.isEmpty()) return

        viewModelScope.launch {
            preferenceUtil.setPlaylistSortStyle("default")
            preferenceUtil.setPlaylistSortReversed(false)
            repo.updatePlaylistOrder(playlistIdsInDisplayOrder)
        }
    }

    private fun sortPlaylists(
        playlists: List<MusicSet.Playlist>,
        style: String,
        reversed: Boolean
    ): List<MusicSet.Playlist> {

        val comparator = when (style) {
            "name" -> compareBy<MusicSet.Playlist, String>(
                String.CASE_INSENSITIVE_ORDER,
                { it.name }
            ).thenBy { it.id }

            "date" -> compareByDescending<MusicSet.Playlist> { it.setup_time }
                .thenByDescending { it.id }


            "amount" -> compareByDescending<MusicSet.Playlist> { it.musicCount }
                .thenByDescending { it.id }


            else -> compareBy({ it.sort }, { it.id })
        }.let { if (reversed) it.reversed() else it }

        return playlists.sortedWith(comparator)
    }
}
