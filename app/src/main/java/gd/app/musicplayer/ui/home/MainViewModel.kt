package gd.app.musicplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.SmartPlaylistConfig
import gd.app.musicplayer.domain.usecase.library.ObserveSortUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveFavoriteCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveFolderCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveMainPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveMostPlayCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveRecentAddCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveRecentPlayCountUseCase
import gd.app.musicplayer.domain.usecase.main.ObserveTracksCountUseCase
import gd.app.musicplayer.domain.usecase.main.UpdateMainPlaylistOrderUseCase
import gd.app.musicplayer.domain.usecase.playlist.ResetPlaylistsSortUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObserveSmartPlaylistConfigUseCase
import gd.app.musicplayer.playback.PlaybackStartupInitializer
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5_000L

data class MainUiState(
    val items: List<MainItem> = emptyList(),
    val playlists: List<MusicSet.Playlist> = emptyList(),
    val playlistCount: Int = playlists.size
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeMainPlaylistsUseCase: ObserveMainPlaylistsUseCase,
    private val observeSortUseCase: ObserveSortUseCase,
    private val observeSmartPlaylistConfigUseCase: ObserveSmartPlaylistConfigUseCase,
    private val observeTracksCountUseCase: ObserveTracksCountUseCase,
    private val observeFolderCountUseCase: ObserveFolderCountUseCase,
    private val observeFavoriteCountUseCase: ObserveFavoriteCountUseCase,
    private val observeRecentPlayCountUseCase: ObserveRecentPlayCountUseCase,
    private val observeRecentAddCountUseCase: ObserveRecentAddCountUseCase,
    private val observeMostPlayCountUseCase: ObserveMostPlayCountUseCase,
    private val updateMainPlaylistOrderUseCase: UpdateMainPlaylistOrderUseCase,
    private val resetPlaylistsSortUseCase: ResetPlaylistsSortUseCase,
    private val playbackStartupInitializer: PlaybackStartupInitializer
) : ViewModel() {

    private var updatePlaylistOrderJob: Job? = null

    init {
        viewModelScope.launch {
            playbackStartupInitializer.initialize()
        }
    }

    private val smartPlaylistConfig: Flow<SmartPlaylistConfig> =
        observeSmartPlaylistConfigUseCase()
            .distinctUntilChanged()

    private val playlists: StateFlow<List<MusicSet.Playlist>> = combine(
        observeMainPlaylistsUseCase(),
        observeSortUseCase(MusicSet.Playlists)
    ) { playlists, sort ->
        sortPlaylists(
            playlists = playlists,
            style = sort.first,
            reversed = sort.second
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val mainItems: StateFlow<List<MainItem>> = smartPlaylistConfig
        .flatMapLatest { config ->
            combine(
                observeTracksCountUseCase(),
                observeFolderCountUseCase(),
                observeFavoriteCountUseCase(),
                observeRecentPlayCountUseCase(
                    playlistWindowMs = config.windowDurationMs,
                    windowStartMs = config.windowStartMs,
                    playlistLimit = config.trackLimit
                ),
                observeRecentAddCountUseCase(
                    playlistWindowMs = config.windowDurationMs,
                    windowStartMs = config.windowStartMs,
                    playlistLimit = config.trackLimit
                ),
                observeMostPlayCountUseCase(
                    playlistWindowMs = config.windowDurationMs,
                    windowStartMs = config.windowStartMs,
                    playlistLimit = config.trackLimit
                )
            ) { counts ->
                buildMainItems(
                    libraryCount = counts[0],
                    folderCount = counts[1],
                    favoriteCount = counts[2],
                    recentPlayCount = counts[3],
                    recentAddCount = counts[4],
                    mostPlayCount = counts[5]
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
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
        mainItems,
        playlists
    ) { items, playlists ->
        MainUiState(
            items = items,
            playlists = playlists,
            playlistCount = playlists.size
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            initialValue = MainUiState()
        )

    fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        if (playlistIdsInDisplayOrder.isEmpty()) return

        updatePlaylistOrderJob?.cancel()
        updatePlaylistOrderJob = viewModelScope.launch {
            resetPlaylistsSortUseCase()
            updateMainPlaylistOrderUseCase(playlistIdsInDisplayOrder)
        }
    }

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
                category = MainCategory.Library,
                titleRes = R.string.library,
                iconRes = R.drawable.main_local,
                bgColor = MAIN_LIBRARY_COLOR,
                count = libraryCount
            ),
            MainItem(
                category = MainCategory.Folders,
                titleRes = R.string.folder,
                iconRes = R.drawable.main_folder,
                bgColor = MAIN_FOLDER_COLOR,
                count = folderCount
            ),
            MainItem(
                category = MainCategory.Favorites,
                titleRes = R.string.favorite,
                iconRes = R.drawable.main_favourite,
                bgColor = MAIN_FAVORITE_COLOR,
                count = favoriteCount
            ),
            MainItem(
                category = MainCategory.RecentlyPlayed,
                titleRes = R.string.recent_play,
                iconRes = R.drawable.main_recent_play,
                bgColor = MAIN_RECENT_PLAY_COLOR,
                count = recentPlayCount
            ),
            MainItem(
                category = MainCategory.RecentlyAdded,
                titleRes = R.string.recent_add,
                iconRes = R.drawable.main_recent_add,
                bgColor = MAIN_RECENT_ADD_COLOR,
                count = recentAddCount
            ),
            MainItem(
                category = MainCategory.MostPlayed,
                titleRes = R.string.most_play,
                iconRes = R.drawable.main_most_play,
                bgColor = MAIN_MOST_PLAY_COLOR,
                count = mostPlayCount
            )
        )
    }

    private fun sortPlaylists(
        playlists: List<MusicSet.Playlist>,
        style: String,
        reversed: Boolean
    ): List<MusicSet.Playlist> {
        val comparator = when (style) {
            SORT_NAME -> compareBy<MusicSet.Playlist, String>(
                String.CASE_INSENSITIVE_ORDER
            ) { playlist -> playlist.name }.thenBy { playlist -> playlist.id }

            SORT_DATE -> compareByDescending<MusicSet.Playlist> { playlist -> playlist.setupTime }
                .thenByDescending { playlist -> playlist.id }

            SORT_AMOUNT -> compareByDescending<MusicSet.Playlist> { playlist -> playlist.musicCount }
                .thenByDescending { playlist -> playlist.id }

            else -> compareBy<MusicSet.Playlist> { playlist -> playlist.sort }
                .thenBy { playlist -> playlist.id }
        }

        return playlists.sortedWith(
            if (reversed) comparator.reversed() else comparator
        )
    }

    private companion object {
        const val SORT_NAME = "name"
        const val SORT_DATE = "date"
        const val SORT_AMOUNT = "amount"

        const val MAIN_LIBRARY_COLOR = -867723789
        const val MAIN_FOLDER_COLOR = -855992486
        const val MAIN_FAVORITE_COLOR = -856058475
        const val MAIN_RECENT_PLAY_COLOR = -864305174
        const val MAIN_RECENT_ADD_COLOR = -872359528
        const val MAIN_MOST_PLAY_COLOR = -859467278
    }
}
