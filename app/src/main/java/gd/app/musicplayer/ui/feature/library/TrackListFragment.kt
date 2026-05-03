package gd.app.musicplayer.ui.feature.library

import android.os.Bundle
import android.view.View
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
import gd.app.musicplayer.data.model.MusicSet.Album
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.ui.feature.library.adapter.ArtistAlbumHeaderAdapter
import gd.app.musicplayer.ui.feature.library.adapter.TrackAdapter
import gd.app.musicplayer.ui.feature.player.PlayQueueActivity
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.ui.feature.selection.MusicEditActivity
import gd.app.musicplayer.playback.PlaybackGateway
import gd.app.musicplayer.playback.queue.currentTrack
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.menu.MusicSetContextMenu
import gd.app.musicplayer.ui.common.menu.MusicSetMenuAction
import gd.app.musicplayer.ui.feature.shortcut.MusicSetShortcutHelper
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackListFragment : BaseListFragment() {

    private val viewModel: TrackListViewModel by viewModels()

    private lateinit var trackAdapter: TrackAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController

    private var artistAlbumHeaderAdapter: ArtistAlbumHeaderAdapter? = null
    private var currentTracks: List<Music> = emptyList()

    override fun onBindingCreated(
        binding: LayoutRecyclerviewBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupAdapters()
        setupRecyclerView(concatAdapter)
        setupEmptyStateController(binding)

        observeUiState()
        observeEvents()
        observePlaybackState()

        viewModel.bind(musicSet)
    }

    private fun setupAdapters() {
        trackAdapter = TrackAdapter(
            musicSet = musicSet,
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

    private fun observePlaybackState() {
        collectWhenStarted(
            PlaybackGateway.state
                .map { playbackState -> playbackState.currentTrack?.id to playbackState.isPlaying }
                .distinctUntilChanged()
        ) { (currentTrackId, isPlaying) ->
            trackAdapter.updatePlaybackState(
                currentTrackId = currentTrackId,
                isPlaying = isPlaying
            )
        }
    }

    private fun render(state: TrackListUiState) {
        currentTracks = state.tracks

        emptyStateController.setVisible(state.isEmpty)

        trackAdapter.setMetadataDisplayMode(state.trackMetadataDisplayMode)
        trackAdapter.submitList(state.tracks)
        artistAlbumHeaderAdapter?.submitAlbums(state.artistAlbums)

        updateCurrentPlaybackState()
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

    private fun updateCurrentPlaybackState() {
        val playbackState = PlaybackGateway.state.value
        trackAdapter.updatePlaybackState(
            currentTrackId = playbackState.currentTrack?.id,
            isPlaying = playbackState.isPlaying
        )
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
        val preferences = PreferenceUtil.getInstance(context)
        val playbackState = PlaybackGateway.state.value

        val shouldRestartCurrentTrack =
            preferences.isReplaySongEnabled() &&
                    playbackState.currentTrack?.id == track.id

        if (shouldRestartCurrentTrack) {
            PlaybackGateway.restartCurrentTrack(context)
        } else {
            viewModel.onTrackClicked(track)
        }

        if (preferences.isTrackClickOperationEnabled()) {
            PlayQueueActivity.start(context)
        }
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

    private fun openAlbumMusic(album: Album) {
        AlbumMusicActivity.start(
            context = requireContext(),
            musicSet = album
        )
    }

    override fun showMoreMenu(anchor: View) {
        MusicSetContextMenu(
            context = requireContext(),
            musicSet = musicSet,
            onAction = ::handleMusicSetMenuAction
        ).show(anchor)
    }

    fun handleMusicSetMenuAction(action: MusicSetMenuAction) {
        when (action) {
            MusicSetMenuAction.Select -> openSelection()

            MusicSetMenuAction.ShuffleAll,
            MusicSetMenuAction.PlayNext,
            MusicSetMenuAction.AddToQueue,
            MusicSetMenuAction.AddToPlaylist,
            MusicSetMenuAction.ClearFavorites,
            MusicSetMenuAction.ClearRecentlyAdded,
            MusicSetMenuAction.ClearRecentlyPlayed,
            MusicSetMenuAction.ClearMostPlayed -> viewModel.onMenuAction(action)

            MusicSetMenuAction.Rename -> showRenameDialog()

            MusicSetMenuAction.ManageArtwork -> showManageArtworkDialog()

            is MusicSetMenuAction.SortChanged -> {
                // Sort update flow is driven by view model state observers.
            }

            MusicSetMenuAction.SortBy -> {
                // Sort submenu is opened inside MusicSetContextMenu.
            }

            MusicSetMenuAction.AddToHomeScreen -> {
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

            MusicSetMenuAction.DeletePlaylist,
            MusicSetMenuAction.BackupPlaylists,
            MusicSetMenuAction.RestorePlaylists,
            MusicSetMenuAction.DeleteEmptyPlaylists,
            MusicSetMenuAction.ViewAsList,
            MusicSetMenuAction.ViewAsGrid -> {
                // Not handled by TrackListFragment.
            }
        }
    }

    fun onSortChanged() {
        viewModel.onSortChanged()
    }

    private fun openSelection() {
        MusicEditActivity.start(
            context = requireContext(),
            musicSet = musicSet
        )
    }

    private fun showRenameDialog() {
        when (val set = musicSet) {
            is MusicSet.Playlist -> {
                PlaylistInputDialog
                    .forSet(
                        set = set,
                        mode = PlaylistInputDialog.MODE_RENAME_SET
                    )
                    .show(
                        parentFragmentManager,
                        TAG_RENAME_PLAYLIST_DIALOG
                    )
            }

            is MusicSet.Album,
            is MusicSet.Artist,
            is MusicSet.Genre -> showNotImplemented()

            else -> showNotImplemented()
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

    fun getCurrentTracks(): List<Music> {
        return currentTracks
    }

    private fun showNotImplemented() {
        ToastUtil.show(requireContext(), R.string.feature_not_implemented)
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

