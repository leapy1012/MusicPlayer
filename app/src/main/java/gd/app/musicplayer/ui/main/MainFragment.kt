package gd.app.musicplayer.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentMainBinding
import gd.app.musicplayer.feature.shell.MainActivity
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.library.AlbumActivity
import gd.app.musicplayer.feature.library.AlbumMusicActivity
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.core.ui.extension.applySystemBarInsets
import gd.app.musicplayer.core.ui.extension.dpToPx
import gd.app.musicplayer.ui.common.base.WrapContentLinearLayoutManager
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainFragment : ViewBindingFragment<FragmentMainBinding>() {
    private val viewModel: MainViewModel by viewModels()

    private val mainAdapter by lazy(LazyThreadSafetyMode.NONE) {
        MainAdapter(emptyList(), ::onMainCategoryClick)
    }
    private val playlistAdapter by lazy(LazyThreadSafetyMode.NONE) {
        MainPlaylistAdapter(
            context = requireContext(),
            onPlaylistClick = ::openPlaylist,
            onAddClick = ::openCreatePlaylistDialog,
            onPlaylistOrderChanged = viewModel::updatePlaylistOrder
        )
    }

    private val playlistLayoutManager by lazy(LazyThreadSafetyMode.NONE) {
        WrapContentLinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
    }

    private val createPlaylistDialogTag = "main_create_playlist_dialog"

    override fun onCreateBinding(inflater: LayoutInflater): FragmentMainBinding =
        FragmentMainBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentMainBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        setupInsets(binding)
        setupToolbar(binding)
        setupMainGrid(binding)
        setupPlaylist(binding)
        observeUiState()
    }

    private fun setupInsets(binding: FragmentMainBinding) = with(binding) {
        root.applySystemBarInsets(statusBarSpace, root)
    }

    private fun setupToolbar(binding: FragmentMainBinding) = with(binding.toolbar) {
        setOnMenuItemClickListener(::onMenuItemClick)
        setNavigationOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
    }

    private fun setupMainGrid(binding: FragmentMainBinding) = binding.mainInfoGrid.apply {
        adapter = mainAdapter
        numColumns = 3
    }

    private fun setupPlaylist(binding: FragmentMainBinding) {
        binding.mainInfoPlaylistContainer.apply {
            layoutManager = playlistLayoutManager
            adapter = playlistAdapter
            setHasFixedSize(true)

            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration.right(requireContext().dpToPx(8f)))
            }

            ItemTouchHelper(
                DragItemTouchHelperCallback.Builder(
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                    0
                )
                    .setDragEnabled(true)
                    .onItemDragListener(playlistAdapter::onItemDragged)
                    .build()
            ).attachToRecyclerView(this)

            binding.mainInfoPlaylistAdd.setOnClickListener(::onAddPlaylistClick)
            binding.mainInfoPlaylist.setOnClickListener(::onPlaylistsClick)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: MainUiState) {
        val binding = requireBinding()

        mainAdapter.submitList(state.items)
        playlistAdapter.submitPlaylists(state.playlists)
        binding.mainInfoPlaylistCount.text = "(${state.playlistCount})"
    }

    private fun onMainCategoryClick(category: MainItem) {
        val context = requireContext()
        when (category.titleRes) {
            R.string.library -> AlbumActivity.start(context)
            R.string.folder -> AlbumActivity.start(context, MusicSet.Folders)
            R.string.favorite -> AlbumMusicActivity.start(context, MusicSet.Favorites)
            R.string.recent_play -> AlbumMusicActivity.start(context, MusicSet.RecentlyPlayed)
            R.string.recent_add -> AlbumMusicActivity.start(context, MusicSet.RecentlyAdded)
            R.string.most_play -> AlbumMusicActivity.start(context, MusicSet.MostPlayed)
        }
    }

    private fun openPlaylist(playlist: MusicSet.Playlist) {
        AlbumMusicActivity.start(requireContext(), playlist)
    }

    private fun onAddPlaylistClick(view: android.view.View) {
        openCreatePlaylistDialog()
    }

    private fun onPlaylistsClick(view: android.view.View) {
        AlbumActivity.start(requireContext(), MusicSet.Playlists)
    }

    private fun openCreatePlaylistDialog() {
        PlaylistInputDialog.forMode(PlaylistInputDialog.MODE_CREATE_AND_RETURN)
            .show(childFragmentManager, createPlaylistDialogTag)
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                SearchActivity.start(requireContext())
                true
            }

            else -> {
                false
            }
        }
    }
}
