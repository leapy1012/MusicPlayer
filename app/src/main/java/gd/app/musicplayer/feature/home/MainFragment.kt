package gd.app.musicplayer.feature.home

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.datastore.GuidePreferenceStore
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.databinding.FragmentMainBinding
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.base.WrapContentLinearLayoutManager
import gd.app.musicplayer.ui.common.base.applyCouiLeftTitle
import gd.app.musicplayer.ui.common.guide.DragGuideDialogFragment
import gd.app.musicplayer.feature.library.albums.AlbumActivity
import gd.app.musicplayer.feature.library.albums.AlbumMusicActivity
import gd.app.musicplayer.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.ui.shell.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainFragment : ViewBindingFragment<FragmentMainBinding>() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var guidePreferenceStore: GuidePreferenceStore

    private val mainAdapter by lazy(LazyThreadSafetyMode.NONE) {
        MainAdapter(
            onItemClick = ::onMainCategoryClick,
            isPictureTheme = ::isPictureTheme,
            applyTheme = ::applyThemeTo
        )
    }

    private val playlistAdapter by lazy(LazyThreadSafetyMode.NONE) {
        MainPlaylistAdapter(
            onPlaylistClick = ::openPlaylist,
            onAddClick = ::openCreatePlaylistDialog,
            onPlaylistOrderChanged = viewModel::updatePlaylistOrder,
            isPictureTheme = ::isPictureTheme,
            applyTheme = ::applyThemeTo
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

    override fun onThemeChanged(palette: ThemePalette?) {
        super.onThemeChanged(palette)
        mainAdapter.notifyDataSetChanged()
        playlistAdapter.notifyDataSetChanged()
    }

    private fun isPictureTheme(): Boolean {
        return themeEngine.currentTheme().getThemeType() == ThemeManager.THEME_TYPE_PICTURE
    }

    private fun setupInsets(binding: FragmentMainBinding) = with(binding) {
        root.applySystemBarInsets(statusBarSpace)
    }

    private fun setupToolbar(binding: FragmentMainBinding) = with(binding.toolbar) {
        applyCouiLeftTitle()
        // Not app:menu — COUIToolbar builds that menu view before its click listener exists.
        inflateMenu(R.menu.menu_fragment_main)
        setOnMenuItemClickListener(::onMenuItemClick)
        setNavigationOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
    }

    private fun setupMainGrid(binding: FragmentMainBinding) = with(binding.mainInfoGrid) {
        adapter = mainAdapter
        numColumns = columnCount(resources.configuration)
    }

    private fun setupPlaylistCarousel(binding: FragmentMainBinding) = with(binding) {
        mainInfoPlaylistContainer.apply {
            updatePlaylistItemSize(resources.displayMetrics.widthPixels)
            addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
                val width = right - left
                if (width != oldRight - oldLeft) {
                    view.post { updatePlaylistItemSize(width) }
                }
            }

            layoutManager = WrapContentLinearLayoutManager(
                requireContext(),
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = playlistAdapter
            setHasFixedSize(true)
            setOverScrollEnable(true)

            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration.right(playlistItemSpacingPx()))
            }

            ItemTouchHelper(PlaylistDragCallback(playlistAdapter)).attachToRecyclerView(this)
        }

        mainInfoPlaylistAdd.setOnClickListener {
            openCreatePlaylistDialog()
        }

        mainInfoPlaylist.setOnClickListener(::openAllPlaylists)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val binding = binding ?: return
        binding.mainInfoGrid.numColumns = columnCount(newConfig)
        binding.mainInfoPlaylistContainer.doOnNextLayout { updatePlaylistItemSize(it.width) }
    }

    /** Sized so playlist cards line up with the category grid columns above. */
    private fun updatePlaylistItemSize(availableWidth: Int) {
        val recycler = binding?.mainInfoPlaylistContainer ?: return
        if (availableWidth <= 0) return
        val columns = columnCount(resources.configuration)
        val contentWidth = availableWidth - recycler.paddingStart - recycler.paddingEnd
        playlistAdapter.setItemSize((contentWidth - playlistItemSpacingPx() * (columns - 1)) / columns)
    }

    private fun playlistItemSpacingPx(): Int = requireContext().dpToPx(PLAYLIST_ITEM_SPACING_DP)

    private fun columnCount(configuration: Configuration): Int {
        return if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LANDSCAPE_COLUMN_COUNT
        } else {
            PORTRAIT_COLUMN_COUNT
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
            if (!adapter.canDrag(viewHolder.bindingAdapterPosition)) return 0

            val dragFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            return makeMovementFlags(dragFlags, 0)
        }

        override fun getAnimationDuration(
            recyclerView: RecyclerView,
            animationType: Int,
            animateDx: Float,
            animateDy: Float
        ): Long {
            return if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
                DRAG_SETTLE_DURATION_MS
            } else {
                super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
            }
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
                viewHolder?.itemView?.alpha = DRAGGING_ALPHA
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
            viewHolder.itemView.alpha = 1f
            adapter.finishDrag()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }

    private companion object {
        const val PORTRAIT_COLUMN_COUNT = 3
        const val LANDSCAPE_COLUMN_COUNT = 6
        const val PLAYLIST_ITEM_SPACING_DP = 12f
        const val DRAGGING_ALPHA = 0.8f
        const val DRAG_SETTLE_DURATION_MS = 300L
        const val CREATE_PLAYLIST_DIALOG_TAG = "main_create_playlist_dialog"
        const val DRAG_GUIDE_TAG = "home_playlist_drag_guide"
    }
}
