package gd.app.musicplayer.feature.library

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.feature.menu.MusicSetContextMenu
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.MusicSet.Album
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.feature.player.ActivityPlayQueue
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.feature.selection.MusicEditActivity
import gd.app.musicplayer.playback.MusicPlaybackController
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.jvm.java

@AndroidEntryPoint
class MusicListFragment : BaseListFragment() {
    private val viewModel: MusicListViewModel by viewModels()

    private lateinit var adapter: MusicAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private var artistHeaderAdapter: ArtistAlbumHeaderAdapter? = null
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private var currentTracks: List<Music> = emptyList()

    override fun onBindingCreated(binding: LayoutRecyclerviewBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        viewModel.bind(musicSet)

        setupAdapter()
        setupRecyclerView(concatAdapter)
        setupEmptyStateController()
        observePlaybackState()
        observeUiState()

        adapter = MusicAdapter(
            musicSet = musicSet,
            onItemClick = ::handleTrackClick,
            onMenuClick = { music ->
                MusicOptionsDialog.newInstance(music, musicSet)
                    .show(parentFragmentManager, MusicOptionsDialog::class.java.simpleName)
            },
            onItemLongClick = { music ->
                MusicEditActivity.start(
                    context = requireContext(),
                    musicSet = musicSet,
                    selectedMusic = music,
                    view?.top ?: 0
                )
            }
        )

        concatAdapter = if (musicSet is MusicSet.Artist) {
            artistHeaderAdapter = ArtistAlbumHeaderAdapter { album ->
                AlbumMusicActivity.start(requireContext(), album)
            }
            ConcatAdapter(artistHeaderAdapter, adapter)
        } else {
            ConcatAdapter(adapter)
        }

        setupRecyclerView(concatAdapter)
        setupEmptyStateController()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MusicPlaybackController.state
                    .map { state -> state.currentTrack?._id to state.isPlaying }
                    .distinctUntilChanged()
                    .collect { (trackId, isPlaying) ->
                        adapter.updatePlaybackState(trackId, isPlaying)
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect { state ->
                    currentTracks = state.tracks
                    emptyStateController.setVisible(state.isEmpty)
                    adapter.submitList(state.tracks)
                    artistHeaderAdapter?.submitAlbums(state.artistAlbums)
                    val playbackState = MusicPlaybackController.state.value
                    adapter.updatePlaybackState(
                        playbackState.currentTrack?._id,
                        playbackState.isPlaying
                    )
                }
            }
        }
    }


    private fun setupAdapter() {
        adapter = MusicAdapter(
            musicSet = musicSet,
            onItemClick = ::handleTrackClick,
            onMenuClick = ::showMusicOptionsDialog,
            onItemLongClick = ::openMusicEditActivity
        )

        concatAdapter = if (musicSet is MusicSet.Artist) {
            artistHeaderAdapter = ArtistAlbumHeaderAdapter { album ->
                AlbumMusicActivity.start(requireContext(), album)
            }

            ConcatAdapter(artistHeaderAdapter, adapter)
        } else {
            ConcatAdapter(adapter)
        }
    }

    private fun showMusicOptionsDialog(music: Music) {
        MusicOptionsDialog
            .newInstance(music, musicSet)
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

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MusicPlaybackController.state
                    .map { state ->
                        state.currentTrack?._id to state.isPlaying
                    }
                    .distinctUntilChanged()
                    .collect { (trackId, isPlaying) ->
                        adapter.updatePlaybackState(trackId, isPlaying)
                    }
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: MusicListUiState) {
        currentTracks = state.tracks

        emptyStateController.setVisible(state.isEmpty)

        adapter.submitList(state.tracks)
        artistHeaderAdapter?.submitAlbums(state.artistAlbums)

        updateCurrentPlaybackState()
    }

    private fun updateCurrentPlaybackState() {
        val playbackState = MusicPlaybackController.state.value

        adapter.updatePlaybackState(
            currentTrackId = playbackState.currentTrack?._id,
            isPlaying = playbackState.isPlaying
        )
    }

    override fun showMoreMenu(anchor: View) {
        MusicSetContextMenu(
            context = requireContext(),
            musicSet = musicSet,
            onSelect = {
                MusicEditActivity.start(
                    context = requireContext(),
                    musicSet = musicSet
                )
            },
            tracksProvider = {
                currentTracks
            },
            onSortChanged = { _, _ ->
                viewModel.bind(musicSet)
            }
        ).show(anchor)
    }


    private fun setupEmptyStateController() {
        val binding = requireBinding()

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.recyclerview,
            emptyViewStub = binding.layoutListEmpty
        ).apply {
            configureEmptyState()
        }
    }


    private fun RecyclerEmptyStateController.configureEmptyState() {
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

    private fun handleTrackClick(track: Music) {
        val preferences = PreferenceUtil.getInstance(requireContext())
        val playbackState = MusicPlaybackController.state.value

        val shouldRestartCurrentTrack =
            preferences.isReplaySongEnabled() &&
                    playbackState.currentTrack?._id == track._id

        if (shouldRestartCurrentTrack) {
            MusicPlaybackController.restartCurrentTrack(requireContext())
        } else {
            viewModel.onTrackClicked(track)
        }

        if (preferences.isTrackClickOperationEnabled()) {
            ActivityPlayQueue.start(requireContext())
        }
    }

    fun getCurrentTracks(): List<Music> {
        return currentTracks
    }

    private class ArtistAlbumHeaderAdapter(
        private val onAlbumClick: (Album) -> Unit
    ) : RecyclerView.Adapter<ArtistAlbumHeaderAdapter.HeaderViewHolder>() {

        private var albums: List<Album> = emptyList()

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): HeaderViewHolder {
            return HeaderViewHolder(
                ArtistAlbumHeaderView(parent.context)
            )
        }

        override fun onBindViewHolder(
            holder: HeaderViewHolder,
            position: Int
        ) {
            holder.bind(
                albums = albums,
                onAlbumClick = onAlbumClick
            )
        }

        override fun getItemCount(): Int {
            return if (albums.isEmpty()) 0 else 1
        }

        fun submitAlbums(newAlbums: List<Album>) {
            val hadHeader = albums.isNotEmpty()
            val hasHeader = newAlbums.isNotEmpty()
            val oldAlbums = albums

            albums = newAlbums

            when {
                !hadHeader && hasHeader -> notifyItemInserted(0)
                hadHeader && !hasHeader -> notifyItemRemoved(0)
                hadHeader && oldAlbums != newAlbums -> notifyItemChanged(0)
            }
        }

        class HeaderViewHolder(
            private val headerView: ArtistAlbumHeaderView
        ) : RecyclerView.ViewHolder(headerView) {

            fun bind(
                albums: List<Album>,
                onAlbumClick: (Album) -> Unit
            ) {
                headerView.setOnAlbumClickListener(onAlbumClick)
                headerView.submitAlbums(albums)
            }
        }
    }

    companion object {
        fun newInstance(set: MusicSet): MusicListFragment {
            return MusicListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_SET, set)
                }
            }
        }
    }

}
