package gd.app.musicplayer.ui.feature.library

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.ArtworkRequest
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.ThemeRepo
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.domain.usecase.preferences.GetReplaySongEnabledUseCase
import gd.app.musicplayer.domain.usecase.preferences.IsTrackClickOperationEnabledUseCase
import gd.app.musicplayer.ui.feature.library.adapter.ArtistAlbumHeaderAdapter
import gd.app.musicplayer.ui.feature.library.adapter.TrackAdapter
import gd.app.musicplayer.ui.feature.player.PlayQueueActivity
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.ui.feature.selection.MusicEditActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.menu.ContextMenu
import gd.app.musicplayer.ui.common.menu.ContextMenuAction
import gd.app.musicplayer.ui.player.PlayerViewModel
import gd.app.musicplayer.ui.feature.shortcut.MusicSetShortcutHelper

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackListFragment : BaseListFragment() {

    private val viewModel: TrackListViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels ()
    @Inject lateinit var themeRepo: ThemeRepo
    @Inject lateinit var getReplaySongEnabledUseCase: GetReplaySongEnabledUseCase
    @Inject lateinit var isTrackClickOperationEnabledUseCase: IsTrackClickOperationEnabledUseCase

    private lateinit var trackAdapter: TrackAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController

    private var artistAlbumHeaderAdapter: ArtistAlbumHeaderAdapter? = null
    private var currentTracks: List<Music> = emptyList()
    private var currentSortState: TrackListSortState = TrackListSortState()

    fun currentSortState(): TrackListSortState = currentSortState

    override fun onBindingCreated(
        binding: LayoutRecyclerviewBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupAdapters()
        setupRecyclerView(concatAdapter)
        setupEmptyStateController(binding)

        observeUiState()
        observeSortState()
        observeEvents()
        observeCurrentTrack()

        viewModel.bind(musicSet)
    }

    private fun setupAdapters() {
        trackAdapter = TrackAdapter(
            musicSet = musicSet,
            theme = themeRepo.getCorePalette(),
            onItemClick = ::onTrackClicked,
            onMenuClick = ::showMusicOptionsDialog,
            onItemLongClick = ::openMusicEditActivity
        )

        concatAdapter = if (musicSet is MusicSet.Artist) {
            artistAlbumHeaderAdapter = ArtistAlbumHeaderAdapter(
                onAlbumClick = ::openAlbumMusic
            )

            ConcatAdapter(
                artistAlbumHeaderAdapter,
                trackAdapter
            )
        } else {
            ConcatAdapter(trackAdapter)
        }
    }

    private fun setupEmptyStateController(binding: LayoutRecyclerviewBinding) {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.recyclerview,
            emptyViewStub = binding.layoutListEmpty
        ).apply {
            configureForMusicSet()
        }
    }

    private fun observeUiState() {
        collectWhenStarted(viewModel.uiState, ::render)
    }

    private fun observeEvents() {
        collectWhenStarted(viewModel.events, ::handleEvent)
    }

    private fun observeSortState() {
        collectWhenStarted(viewModel.sortState) { state ->
            currentSortState = state
            trackAdapter.setMetadataDisplayMode(state.sortStyle)
        }
    }

    private fun observeCurrentTrack() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.playbackHighlightState.collect { state ->
                    trackAdapter.submitPlaybackHighlight(state.currentMusicId, state.isPlaying)
                }
            }
        }
    }

    private fun render(state: TrackListUiState) {
        currentTracks = state.tracks

        emptyStateController.setVisible(state.isEmpty)

        trackAdapter.submitList(state.tracks)
        artistAlbumHeaderAdapter?.submitAlbums(state.artistAlbums)
    }

    private fun handleEvent(event: TrackListEvent) {
        when (event) {
            TrackListEvent.OpenPlayQueue -> PlayQueueActivity.start(requireContext())

            is TrackListEvent.OpenAddToPlaylist -> ActivityPlaylistSelect.start(requireContext(), event.tracks)

            is TrackListEvent.ShowMessage -> ToastUtil.show(requireContext(), event.messageRes)

            is TrackListEvent.ShowEnqueuedMessage -> {
                ToastUtil.show(
                    requireContext(),
                    getString(
                        R.string.enqueue_msg_count,
                        event.count
                    )
                )
            }
        }
    }

    private fun RecyclerEmptyStateController.configureForMusicSet() {
        when (musicSet) {
            is MusicSet.Favorites,
            is MusicSet.Playlist -> {
                setActionButtonVisible(true)
                setActionButtonText(getString(R.string.add_songs))
                setEmptyMessage(getString(R.string.music_empty))
            }

            is MusicSet.Tracks,
            is MusicSet.RecentlyAdded -> {
                setActionButtonVisible(true)
                setExtraTextVisible(true)
                setActionButtonText(getString(R.string.rescan_library))
                setExtraText(getString(R.string.music_empty_add))
                setEmptyMessage(getString(R.string.music_empty))
            }

            else -> {
                setEmptyMessage(getString(R.string.music_empty))
            }
        }
    }

    private fun onTrackClicked(track: Music) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val shouldRestartCurrentTrack =
                getReplaySongEnabledUseCase() &&
                        playerViewModel.playbackHighlightState.value.currentMusicId == track.id

            if (shouldRestartCurrentTrack) {
                playerViewModel.restartCurrentTrack(context)
            } else {
                playTrackFromCurrentList(track)
            }

            if (isTrackClickOperationEnabledUseCase()) {
                PlayQueueActivity.start(context)
            }
        }
    }

    private fun playTrackFromCurrentList(track: Music) {
        val startIndex = currentTracks.indexOfFirst { it.id == track.id }

        if (startIndex == -1) return

        playerViewModel.playQueue(
            context = requireContext(),
            queue = currentTracks,
            startIndex = startIndex
        )
    }

    private fun showMusicOptionsDialog(music: Music) {
        MusicOptionsDialog
            .newInstance(
                music = music,
                musicSet = musicSet
            )
            .show(
                parentFragmentManager,
                MusicOptionsDialog::class.java.simpleName
            )
    }

    private fun openMusicEditActivity(music: Music) {
        MusicEditActivity.start(
            context = requireContext(),
            musicSet = musicSet,
            selectedMusic = music,
            offset = view?.top ?: 0
        )
    }

    private fun openAlbumMusic(album: MusicSet.Album) {
        AlbumMusicActivity.start(
            context = requireContext(),
            musicSet = album
        )
    }

    override fun showMoreMenu(anchor: View) {
        ContextMenu(
            context = requireContext(),
            musicSet = musicSet,
            theme = themeRepo.getCorePalette(),
            onAction = ::handleContextMenuAction,
            currentSortStyle = currentSortState.sortStyle,
            currentSortDescending = currentSortState.sortDescending
        ).show(anchor)
    }

    fun handleContextMenuAction(action: ContextMenuAction) {
        when (action) {
            ContextMenuAction.Select -> openSelection()

            ContextMenuAction.ShuffleAll,
            ContextMenuAction.PlayNext,
            ContextMenuAction.AddToQueue,
            ContextMenuAction.AddToPlaylist,
            ContextMenuAction.ClearFavorites,
            ContextMenuAction.ClearRecentlyAdded,
            ContextMenuAction.ClearRecentlyPlayed,
            ContextMenuAction.ClearMostPlayed -> viewModel.onMenuAction(action)

            ContextMenuAction.Rename -> showRenameDialog()

            ContextMenuAction.ManageArtwork -> showManageArtworkDialog()

            is ContextMenuAction.SortChanged -> {
                viewModel.onSortChanged(action.sortKey, action.descending)
            }

            ContextMenuAction.AddToHomeScreen -> {
                val context = requireContext()
                val success = MusicSetShortcutHelper.requestPinnedShortcut(
                    context = context,
                    musicSet = musicSet,
                    title = musicSet.name
                )
                ToastUtil.show(
                    context,
                    if (success) R.string.succeed else R.string.feature_not_implemented
                )
            }

            else -> Unit
        }
    }

    private fun openSelection() {
        MusicEditActivity.start(
            context = requireContext(),
            musicSet = musicSet
        )
    }

    private fun showRenameDialog() {

        if (musicSet is MusicSet.Playlist) {
            PlaylistInputDialog
                .forSet(
                    set = musicSet,
                    mode = PlaylistInputDialog.MODE_RENAME_SET
                )
                .show(
                    parentFragmentManager,
                    TAG_RENAME_PLAYLIST_DIALOG
                )
        }
    }

    private fun showManageArtworkDialog() {
        ManageArtworkDialogFragment
            .newInstance(
                ArtworkRequest.MusicSetTarget(musicSet)
            )
            .show(
                parentFragmentManager,
                ManageArtworkDialogFragment::class.java.simpleName
            )
    }

    private fun <T> collectWhenStarted(flow: Flow<T>, collector: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect(collector)
            }
        }
    }

    companion object {
        private const val TAG_RENAME_PLAYLIST_DIALOG = "rename_playlist_dialog"

        fun newInstance(set: MusicSet): TrackListFragment {
            return TrackListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_SET, set)
                }
            }
        }
    }
}

