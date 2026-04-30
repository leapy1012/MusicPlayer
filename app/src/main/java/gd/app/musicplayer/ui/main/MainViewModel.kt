package gd.app.musicplayer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.usecase.main.ObserveFavoriteCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveFolderCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveMainPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveMostPlayCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveRecentAddCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveRecentPlayCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveTracksCountUseCase
import gd.app.musicplayer.domain.usecase.main.UpdateMainPlaylistOrderUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObservePlaylistSortUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObserveSmartPlaylistConfigUseCase
import gd.app.musicplayer.domain.usecase.preferences.ResetPlaylistSortUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val items: List<MainItem> = emptyList(),
    val playlists: List<MusicSet.Playlist> = emptyList(),
    val playlistCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeMainPlaylistsUseCase: ObserveMainPlaylistsUseCase,
    private val observePlaylistSortUseCase: ObservePlaylistSortUseCase,
    private val observeSmartPlaylistConfigUseCase: ObserveSmartPlaylistConfigUseCase,
    private val observeTracksCountUseCase: ObserveTracksCountUseCase,
    private val observeFolderCountUseCase: ObserveFolderCountUseCase,
    private val observeFavoriteCountUseCase: ObserveFavoriteCountUseCase,
    private val observeRecentPlayCountUseCase: ObserveRecentPlayCountUseCase,
    private val observeRecentAddCountUseCase: ObserveRecentAddCountUseCase,
    private val observeMostPlayCountUseCase: ObserveMostPlayCountUseCase,
    private val updateMainPlaylistOrderUseCase: UpdateMainPlaylistOrderUseCase,
    private val resetPlaylistSortUseCase: ResetPlaylistSortUseCase
) : ViewModel() {

    val playlists: StateFlow<List<MusicSet.Playlist>> = combine(
        observeMainPlaylistsUseCase(),
        observePlaylistSortUseCase()
    ) { playlists, (style, reversed) ->
        sortPlaylists(playlists, style, reversed)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val smartPlaylistConfig: Flow<gd.app.musicplayer.util.SmartPlaylistPreferenceOps.SmartPlaylistConfig> =
        observeSmartPlaylistConfigUseCase()

    val items: StateFlow<List<MainItem>> = smartPlaylistConfig.flatMapLatest { config ->
        combine(
            observeTracksCountUseCase(),
            observeFolderCountUseCase(),
            observeFavoriteCountUseCase(),
            observeRecentPlayCountUseCase(
                playlistWindowMs = config.windowDurationMs,
                windowStartMs = config.windowStartMs,
                playlistLimit = config.playlistLimit
            ),
            observeRecentAddCountUseCase(
                playlistWindowMs = config.windowDurationMs,
                windowStartMs = config.windowStartMs,
                playlistLimit = config.playlistLimit
            ),
            observeMostPlayCountUseCase(
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

    val uiState: StateFlow<MainUiState> = combine(items, playlists) { items, playlists ->
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
            resetPlaylistSortUseCase()
            updateMainPlaylistOrderUseCase(playlistIdsInDisplayOrder)
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
