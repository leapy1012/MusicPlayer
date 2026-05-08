package gd.app.musicplayer.ui.feature.library

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.ThemeRepo
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.ui.feature.scan.ScanMusicActivity
import gd.app.musicplayer.ui.feature.selection.MusicSetEditActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.menu.ContextMenu
import gd.app.musicplayer.ui.common.menu.ContextMenuAction
import gd.app.musicplayer.ui.folder.FolderFooterAdapter
import gd.app.musicplayer.ui.folder.isHiddenFoldersEntry
import gd.app.musicplayer.ui.hidden.HiddenFoldersActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicSetListFragment : BaseListFragment() {

    @Inject lateinit var themeRepo: ThemeRepo

    private val viewModel: MusicSetListViewModel by viewModels()

    private lateinit var adapter: MusicSetAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController

    override fun onBindingCreated(
        binding: LayoutRecyclerviewBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupAdapter()
        setupEmptyStateController(binding)
        observeUiState()
        observeEvents()

        viewModel.bind(musicSet)
    }

    private fun setupAdapter() {
        adapter = MusicSetAdapter(
            musicSetType = musicSet,
            viewMode = MusicSetAdapter.VIEW_MODE_LIST,
            onItemClick = ::onMusicSetClicked,
            onItemLongClick = ::onMusicSetLongClicked,
            onItemMenuClick = { set, _ ->
                showMusicSetOptionsDialog(set)
            }
        )

        val displayAdapter = if (musicSet is MusicSet.Folders) {
            ConcatAdapter(
                adapter,
                FolderFooterAdapter {
                    ScanMusicActivity.start(requireContext())
                }
            )
        } else {
            adapter
        }

        setupRecyclerView(displayAdapter)
        applyViewMode(MusicSetAdapter.VIEW_MODE_LIST)
    }

    private fun setupEmptyStateController(binding: LayoutRecyclerviewBinding) {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.recyclerview,
            emptyViewStub = binding.layoutListEmpty
        ).apply {
            setActionButtonVisible(true)
            setExtraTextVisible(true)
            setActionButtonText(getString(R.string.rescan_library))
            setActionClickListener {
                ScanMusicActivity.start(requireContext())
            }
            setExtraText(getString(R.string.music_empty_add))
            setEmptyMessage(getString(R.string.music_empty))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }

    private fun render(state: MusicSetListUiState) {
        adapter.submitList(state.items)
        adapter.setViewMode(state.viewMode)

        applyViewMode(state.viewMode)

        emptyStateController.setVisible(state.isEmpty)
    }

    private fun handleEvent(event: MusicSetListEvent) {
        when (event) {
            is MusicSetListEvent.RestoreFolderScroll -> {
                restoreFolderScrollState(
                    position = event.position,
                    offset = event.offset
                )
            }
        }
    }

    private fun onMusicSetClicked(set: MusicSet) {
        when {
            set.isHiddenFoldersEntry() -> {
                HiddenFoldersActivity.start(requireContext())
            }

            else -> {
                AlbumMusicActivity.start(
                    context = requireContext(),
                    musicSet = set
                )
            }
        }
    }

    private fun onMusicSetLongClicked(selectedSet: MusicSet) {
        if (selectedSet.isHiddenFoldersEntry()) return

        MusicSetEditActivity.start(
            context = requireContext(),
            musicSet = musicSet,
            visibleItems = adapter.currentList,
            preselectedSet = selectedSet
        )
    }

    private fun showMusicSetOptionsDialog(set: MusicSet) {
        MusicSetOptionsDialog
            .newInstance(set)
            .show(
                parentFragmentManager,
                MusicSetOptionsDialog::class.java.simpleName
            )
    }

    override fun showMoreMenu(anchor: View) {
        val state = viewModel.uiState.value
        ContextMenu(
            context = requireContext(),
            musicSet = musicSet,
            theme = themeRepo.getCorePalette(),
            onAction = ::handleMenuAction,
            selectedViewMode = state.viewMode,
            currentSortStyle = state.currentSortStyle,
            currentSortDescending = state.sortDescending
        ).show(anchor)
    }

    private fun handleMenuAction(action: ContextMenuAction) {
        when (action) {
            ContextMenuAction.Select -> {
                openSelection()
            }

            ContextMenuAction.ViewAsList -> {
                viewModel.onViewModeChanged(MusicSetAdapter.VIEW_MODE_LIST)
            }

            ContextMenuAction.ViewAsGrid -> {
                viewModel.onViewModeChanged(MusicSetAdapter.VIEW_MODE_GRID)
            }

            is ContextMenuAction.SortChanged -> {
                viewModel.onSortChanged(action.sortKey, action.descending)
            }

            else -> Unit
        }
    }

    private fun openSelection() {
        MusicSetEditActivity.start(
            context = requireContext(),
            musicSet = musicSet,
            visibleItems = adapter.currentList
        )
    }

    private fun applyViewMode(viewMode: Int) {
        if (musicSet is MusicSet.Folders) {
            setListLayoutManager()
            return
        }

        if (viewMode == MusicSetAdapter.VIEW_MODE_GRID) {
            setGridLayoutManager()
        } else {
            setListLayoutManager()
        }
    }

    private fun restoreFolderScrollState(
        position: Int,
        offset: Int
    ) {
        if (musicSet !is MusicSet.Folders) return

        val layoutManager =
            requireBinding().recyclerview.layoutManager as? LinearLayoutManager
                ?: return

        requireBinding().recyclerview.post {
            layoutManager.scrollToPositionWithOffset(
                position,
                offset
            )
        }
    }

    companion object {
        fun newInstance(set: MusicSet): MusicSetListFragment {
            return MusicSetListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_SET, set)
                }
            }
        }
    }
}
