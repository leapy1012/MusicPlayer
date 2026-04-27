package gd.app.musicplayer.feature.library

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
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.feature.scan.ScanMusicActivity
import gd.app.musicplayer.feature.selection.MusicSetEditActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.feature.menu.MusicSetContextMenu
import gd.app.musicplayer.ui.folder.FolderFooterAdapter
import gd.app.musicplayer.ui.folder.isHiddenFoldersEntry
import gd.app.musicplayer.ui.hidden.HiddenFoldersActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicSetListFragment : BaseListFragment() {
    private val viewModel: MusicSetListViewModel by viewModels()

    private lateinit var adapter: MusicSetAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController

    companion object {
        fun newInstance(set: MusicSet) =
            MusicSetListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_SET, set)
                }
            }
    }

    override fun onBindingCreated(binding: LayoutRecyclerviewBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        viewModel.bind(musicSet)

        setAdapter()
        setupEmptyStateController()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.items)
                    emptyStateController.setVisible(state.isEmpty)
                    applyViewMode(state.viewMode)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MusicSetListEvent.RestoreFolderScroll -> restoreFolderScrollState(
                            event.position,
                            event.offset
                        )
                    }
                }
            }
        }
    }

    private fun setupEmptyStateController() {
        val binding = requireBinding()
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.recyclerview,
            emptyViewStub = binding.layoutListEmpty
        ).apply {
            setActionButtonVisible(true)
            setExtraTextVisible(true)
            setActionButtonText(getString(R.string.rescan_library))
            setActionClickListener { ScanMusicActivity.Companion.start(requireContext()) }
            setExtraText(getString(R.string.music_empty_add))
            setEmptyMessage(getString(R.string.music_empty))
        }
    }

    private fun setAdapter() {
        adapter = MusicSetAdapter(
            musicSetType = musicSet,
            viewMode = MusicSetAdapter.VIEW_MODE_LIST,
            onItemClick = { set ->
                when {
                    set.isHiddenFoldersEntry() -> HiddenFoldersActivity.start(requireContext())
                    else -> {
                        saveFolderScrollState()
                        AlbumMusicActivity.start(requireContext(), set)
                    }
                }
            },
            onItemLongClick = { selectedSet ->
                if (selectedSet.isHiddenFoldersEntry()) {
                    return@MusicSetAdapter
                }
                saveFolderScrollState()
                MusicSetEditActivity.Companion.start(
                    context = requireContext(),
                    musicSet = musicSet,
                    visibleItems = adapter.currentList,
                    preselectedSet = selectedSet
                )
            },
            onItemMenuClick = { set, _ ->
                MusicSetOptionsDialog.newInstance(set)
                    .show(parentFragmentManager, MusicSetOptionsDialog::class.java.simpleName)
            }
        )

        val displayAdapter =
            if (musicSet is MusicSet.Folders) ConcatAdapter(
                adapter,
                FolderFooterAdapter { ScanMusicActivity.Companion.start(requireContext()) }
            ) else adapter

        setupRecyclerView(displayAdapter)
        applyViewMode(MusicSetAdapter.VIEW_MODE_LIST)
    }

    override fun showMoreMenu(anchor: View) {
        MusicSetContextMenu(
            context = requireContext(),
            musicSet = musicSet,
            onSelect = {
                MusicSetEditActivity.Companion.start(
                    context = requireContext(),
                    musicSet = musicSet,
                    visibleItems = adapter.currentList
                )
            },
            onViewModeChanged = { mode ->
                adapter.setViewMode(mode)
                viewModel.onViewModeChanged(mode)
            },
            onSortChanged = { _, _ -> }
        ).show(anchor)
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

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }

    private fun saveFolderScrollState() {
        if (musicSet !is MusicSet.Folders) return
        val layoutManager = requireBinding().recyclerview.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position < 0) return
        val view = layoutManager.findViewByPosition(position) ?: return
        viewModel.onFolderScrollSaved(
            position = position,
            offset = view.top - requireBinding().recyclerview.paddingTop
        )
    }

    private fun restoreFolderScrollState(position: Int, offset: Int) {
        if (musicSet !is MusicSet.Folders) return
        val layoutManager = requireBinding().recyclerview.layoutManager as? LinearLayoutManager ?: return
        requireBinding().recyclerview.post {
            layoutManager.scrollToPositionWithOffset(position, offset)
        }
    }

}
