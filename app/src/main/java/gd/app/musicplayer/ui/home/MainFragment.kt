package gd.app.musicplayer.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.data.local.preference.GuidePreferenceStore
import gd.app.musicplayer.databinding.FragmentMainBinding
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.base.WrapContentLinearLayoutManager
import gd.app.musicplayer.ui.common.guide.DragGuideDialogFragment
import gd.app.musicplayer.ui.library.albums.AlbumActivity
import gd.app.musicplayer.ui.library.albums.AlbumMusicActivity
import gd.app.musicplayer.ui.playlist.PlaylistInputDialog
import gd.app.musicplayer.ui.search.SearchActivity
import gd.app.musicplayer.ui.shell.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainFragment : ViewBindingFragment<FragmentMainBinding>() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var guidePreferenceStore: GuidePreferenceStore

    private val mainAdapter by lazy(LazyThreadSafetyMode.NONE) {
        MainAdapter(::onMainCategoryClick)
    }

    private val playlistAdapter by lazy(LazyThreadSafetyMode.NONE) {
        MainPlaylistAdapter(
            onPlaylistClick = ::openPlaylist,
            onAddClick = ::openCreatePlaylistDialog,
            onPlaylistOrderChanged = viewModel::updatePlaylistOrder
        )
    }

    override fun onCreateBinding(inflater: LayoutInflater): FragmentMainBinding {
        return FragmentMainBinding.inflate(inflater)
    }

    override fun onBindingCreated(binding: FragmentMainBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        setupInsets(binding)
        setupToolbar(binding)
        setupMainGrid(binding)
        setupPlaylistCarousel(binding)
        observeUiState()
        maybeShowPlaylistDragGuide()
    }

    private fun setupInsets(binding: FragmentMainBinding) = with(binding) {
        root.applySystemBarInsets(statusBarSpace)
    }

    private fun setupToolbar(binding: FragmentMainBinding) = with(binding.toolbar) {
        setOnMenuItemClickListener(::onMenuItemClick)
        setNavigationOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
    }

    private fun setupMainGrid(binding: FragmentMainBinding) = with(binding.mainInfoGrid) {
        adapter = mainAdapter
        numColumns = MAIN_GRID_COLUMN_COUNT
    }

    private fun setupPlaylistCarousel(binding: FragmentMainBinding) = with(binding) {
        mainInfoPlaylistContainer.apply {
            layoutManager = WrapContentLinearLayoutManager(
                requireContext(),
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = playlistAdapter
            setHasFixedSize(true)

            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

            if (itemDecorationCount == 0) {
                addItemDecoration(
                    SpacingItemDecoration.right(requireContext().dpToPx(PLAYLIST_ITEM_SPACING_DP))
                )
            }

            ItemTouchHelper(PlaylistDragCallback(playlistAdapter)).attachToRecyclerView(this)
        }

        mainInfoPlaylistAdd.setOnClickListener {
            openCreatePlaylistDialog()
        }

        mainInfoPlaylist.setOnClickListener(::openAllPlaylists)
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

    private fun maybeShowPlaylistDragGuide() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!guidePreferenceStore.shouldShowHomePlaylistDragGuide()) return@launch

            guidePreferenceStore.markHomePlaylistDragGuideShown()

            if (parentFragmentManager.findFragmentByTag(DRAG_GUIDE_TAG) == null) {
                DragGuideDialogFragment
                    .newHorizontalInstance()
                    .showSafely(parentFragmentManager, DRAG_GUIDE_TAG)
            }
        }
    }

    private fun onMainCategoryClick(item: MainItem) {
        val context = requireContext()
        when (item.category) {
            MainCategory.Library -> AlbumActivity.start(context)
            MainCategory.Folders -> AlbumActivity.start(context, MusicSet.Folders)
            MainCategory.Favorites -> AlbumMusicActivity.start(context, MusicSet.Favorites)
            MainCategory.RecentlyPlayed -> AlbumMusicActivity.start(context, MusicSet.RecentlyPlayed)
            MainCategory.RecentlyAdded -> AlbumMusicActivity.start(context, MusicSet.RecentlyAdded)
            MainCategory.MostPlayed -> AlbumMusicActivity.start(context, MusicSet.MostPlayed)
        }
    }

    private fun openPlaylist(playlist: MusicSet.Playlist) {
        AlbumMusicActivity.start(requireContext(), playlist)
    }

    private fun openAllPlaylists(@Suppress("UNUSED_PARAMETER") view: View) {
        AlbumActivity.start(requireContext(), MusicSet.Playlists)
    }

    private fun openCreatePlaylistDialog() {
        if (childFragmentManager.findFragmentByTag(CREATE_PLAYLIST_DIALOG_TAG) != null) return

        PlaylistInputDialog.forMode(PlaylistInputDialog.MODE_CREATE_AND_RETURN)
            .show(childFragmentManager, CREATE_PLAYLIST_DIALOG_TAG)
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                SearchActivity.start(requireContext())
                true
            }

            else -> false
        }
    }

    private class PlaylistDragCallback(
        private val adapter: MainPlaylistAdapter
    ) : ItemTouchHelper.Callback() {

        override fun isLongPressDragEnabled(): Boolean = true

        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return 0

            val dragFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            return makeMovementFlags(dragFlags, 0)
        }

        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.canMove(
                current.bindingAdapterPosition,
                target.bindingAdapterPosition
            )
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                adapter.startDrag()
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.onItemMove(
                viewHolder.bindingAdapterPosition,
                target.bindingAdapterPosition
            )
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            adapter.finishDrag()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }

    private companion object {
        const val MAIN_GRID_COLUMN_COUNT = 3
        const val PLAYLIST_ITEM_SPACING_DP = 8f
        const val CREATE_PLAYLIST_DIALOG_TAG = "main_create_playlist_dialog"
        const val DRAG_GUIDE_TAG = "home_playlist_drag_guide"
    }
}
