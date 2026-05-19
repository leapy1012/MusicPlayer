package gd.app.musicplayer.ui.selection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyLengthFilter
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.core.designsystem.view.RecyclerIndexBar
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.popupTitleColor
import gd.app.musicplayer.databinding.ActivityMusicSelectBinding
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.SortByContextMenu
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicSelectActivity :
    BaseActivity(),
    View.OnClickListener,
    TextWatcher,
    AdapterView.OnItemClickListener {

    private val viewModel: MusicSelectViewModel by viewModels()

    private lateinit var binding: ActivityMusicSelectBinding
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: MusicRecyclerView
    private lateinit var recyclerIndexBar: RecyclerIndexBar

    private lateinit var musicAdapter: MusicSelectAdapter
    private lateinit var folderAdapter: FolderSelectAdapter
    private lateinit var concatAdapter: ConcatAdapter

    private val indexPositions = mutableListOf<Pair<String, Int>>()

    private var renderingSpinner = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = intent.parcelable<MusicSet>(EXTRA_MUSIC_SET)

        if (target == null || !isSupportedTarget(target)) {
            finish()
            return
        }

        binding = ActivityMusicSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeViewModel()

        viewModel.initialize(target)
    }

    private fun initViews() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.add_songs
        )

        binding.toolbar.inflateMenu(R.menu.menu_activity_music_select)
        binding.toolbar.setOnMenuItemClickListener(::onToolbarMenuItemClicked)

        recyclerView = binding.root.findViewById(R.id.recyclerview)
        recyclerIndexBar = binding.root.findViewById(R.id.recyclerview_index)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null

        musicAdapter = MusicSelectAdapter(
            accentColor = themeRepo.getAccentColor(),
            onSelectionToggle = viewModel::onSongClicked
        )

        folderAdapter = FolderSelectAdapter(
            accentColor = themeRepo.getAccentColor(),
            onItemClick = viewModel::onFolderClicked
        )

        concatAdapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true)
                .build(),
            folderAdapter,
            musicAdapter
        )

        recyclerView.adapter = concatAdapter

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)
        )

        binding.searchEditText.applyLengthFilter(120)
        binding.searchEditText.addTextChangedListener(this)
        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            binding.searchEditText.hideKeyboard()
            false
        }

        binding.searchEditClear.setOnClickListener(this)
        binding.selectAllGroup.setOnClickListener(this)
        binding.buttonConfirm.setOnClickListener(this)
        binding.mainInfoSpinner.setOnItemClickListener(this)

        recyclerIndexBar.onLabelSelected = { label ->
            val targetIndex = indexPositions.firstOrNull { it.first == label }?.second

            if (targetIndex != null) {
                (recyclerView.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(targetIndex, 0)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!viewModel.onBackPressedInSelection()) {
                finish()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::render)
                }

                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun render(state: MusicSelectUiState) {
        renderList(state)
        renderSearch(state)
        renderActions(state)
        renderToolbar(state)
        renderSpinner(state)
        renderSelectAll(state)
        updateIndexBar(state)
    }

    private fun renderList(state: MusicSelectUiState) {
        if (state.header.isBrowsingFolders) {
            folderAdapter.submitFolders(state.content.folderItems,state.header.query)

            musicAdapter.submitMusicList(
                items = emptyList(),
                selectedIds = emptySet(),
                lockedIds = emptySet(),
                highlightQuery = state.header.query
            )

            emptyStateController.setEmptyImage(R.drawable.folder_empty_image)
            emptyStateController.setEmptyMessage(getString(R.string.folder_is_empty))
        } else {
            folderAdapter.submitFolders(emptyList(),highlightQuery = state.header.query)

            musicAdapter.submitMusicList(
                items = state.content.songItems,
                selectedIds = state.actions.selectedSongIds,
                lockedIds = state.actions.lockedSongIds,
                highlightQuery = state.header.query
            )

            emptyStateController.setEmptyImage(R.drawable.music_empty_image)
            emptyStateController.setEmptyMessage(getString(R.string.music_empty))
        }

        emptyStateController.setVisible(state.content.isEmpty)
    }

    private fun renderSearch(state: MusicSelectUiState) {
        binding.searchEditClear.isVisible = state.header.query.isNotEmpty()
    }

    private fun renderActions(state: MusicSelectUiState) {
        val isSongMode = !state.header.isBrowsingFolders

        binding.selectAllBanner.isVisible = isSongMode
        binding.selectAllGroup.isVisible = isSongMode && state.content.songItems.isNotEmpty()
        binding.buttonConfirm.isVisible = isSongMode && state.actions.selectedSongIds.isNotEmpty()
        binding.buttonConfirm.isEnabled = state.actions.isConfirmEnabled
    }

    private fun renderToolbar(state: MusicSelectUiState) {
        binding.toolbar.title = if (state.header.isBrowsingFolders) {
            getString(R.string.add_songs)
        } else {
            musicSelectionTitle(state.actions.selectedSongIds.size)
        }

        binding.toolbar.menu.findItem(R.id.menu_switch)?.setIcon(
            if (state.header.isBrowsingFolders || state.header.selectedMusicSet is MusicSet.Folder) {
                R.drawable.vector_menu_switch_music
            } else {
                R.drawable.vector_menu_switch_folder
            }
        )

        binding.toolbar.menu.findItem(R.id.menu_sort)?.isVisible =
            state.header.isBrowsingFolders || state.header.selectedMusicSet !is MusicSet.Folder
    }

    private fun renderSpinner(state: MusicSelectUiState) {
        val spinnerLabels = state.header.spinnerItems
            .map(::labelForSet)
            .toTypedArray()

        val selectedIndex = state.header.spinnerItems.indexOfFirst {
            sameMusicSet(it, state.header.selectedMusicSet)
        }

        renderingSpinner = true

        binding.mainInfoSpinner.setEntries(spinnerLabels)

        if (selectedIndex >= 0) {
            binding.mainInfoSpinner.setSelection(selectedIndex)
        }

        renderingSpinner = false
    }

    private fun renderSelectAll(state: MusicSelectUiState) {
        binding.mainInfoSelectall.renderSelectAllState(
            SelectionUiState(
                selectedCount = state.actions.selectedSongIds.size,
                hasSelectableItems = state.hasSelectableVisibleSongs(),
                allSelectableItemsSelected = state.areAllSelectableVisibleSongsSelected()
            )
        )
    }

    private fun handleEvent(event: MusicSelectEvent) {
        when (event) {
            is MusicSelectEvent.ConfirmCompleted -> {
                val messageRes = if (event.insertedCount > 0) {
                    R.string.succeed
                } else {
                    R.string.list_contains_music
                }

                ToastUtil.show(this, messageRes)

                viewModel.uiState.value.targetPlaylist?.let { target ->
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_MUSIC_SET, target)
                    )
                }

                finish()
            }
        }
    }

    private fun onToolbarMenuItemClicked(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_switch -> {
                viewModel.onSwitchSourceClicked()
                binding.searchEditText.hideKeyboard()
                true
            }

            R.id.menu_sort -> {
                binding.searchEditText.hideKeyboard()

                val state = viewModel.uiState.value

                val palette = themeRepo.getCorePalette()

                SortByContextMenu(
                    context = this,
                    musicSet = if (state.header.isBrowsingFolders) {
                        MusicSet.Folders
                    } else {
                        state.header.selectedMusicSet
                    },
                    selectionMode = true,
                    currentSortStyle = state.header.currentSortStyle,
                    currentSortDescending = state.header.currentSortDescending,
                    onSortChanged = viewModel::onSortChanged,
                    accentColor = palette.accentColor,
                    popupTextColor = palette.popupTitleColor,
                    popupBackgroundProvider = { menuContext ->
                        palette.getPopupBackgroundDrawable(menuContext)
                    }
                ).show(binding.toolbar.findViewById(item.itemId) ?: binding.toolbar)

                true
            }

            else -> false
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.search_edit_clear -> binding.searchEditText.setText("")
            R.id.main_info_selectall -> viewModel.onSelectAllClicked()
            R.id.button_confirm -> viewModel.confirmSelection()
        }
    }

    override fun afterTextChanged(editable: Editable) {
        viewModel.onQueryChanged(editable.toString())
    }

    override fun beforeTextChanged(
        s: CharSequence?,
        start: Int,
        count: Int,
        after: Int
    ) = Unit

    override fun onTextChanged(
        s: CharSequence?,
        start: Int,
        before: Int,
        count: Int
    ) = Unit

    override fun onItemClick(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        if (renderingSpinner) return

        val selected = viewModel.uiState.value.header.spinnerItems.getOrNull(position) ?: return
        viewModel.onSpinnerMusicSetSelected(selected)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus) {
            binding.searchEditText.hideKeyboard()
        }
    }

    private fun updateIndexBar(state: MusicSelectUiState) {
        indexPositions.clear()

        val labelsSource = if (state.header.isBrowsingFolders) {
            state.content.folderItems.map { it.name }
        } else {
            state.content.songItems.map { it.title }
        }

        labelsSource.forEachIndexed { index, value ->
            val first = value.trim().firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
            val label = if (first.isLetter()) first.toString() else "#"

            if (indexPositions.none { it.first == label }) {
                indexPositions += label to index
            }
        }

        recyclerIndexBar.submitLabels(indexPositions.map { it.first })
    }

    private fun labelForSet(set: MusicSet): String {
        return when (set) {
            is MusicSet.Tracks -> getString(R.string.all_songs)
            is MusicSet.Favorites -> getString(R.string.favorite)
            is MusicSet.RecentlyAdded -> getString(R.string.recently_added)
            is MusicSet.MostPlayed -> getString(R.string.mostly_played)
            is MusicSet.RecentlyPlayed -> getString(R.string.recently_played)
            else -> set.name
        }
    }

    private fun MusicSelectUiState.hasSelectableVisibleSongs(): Boolean {
        return content.songItems.any { music ->
            music.id !in actions.lockedSongIds
        }
    }

    private fun MusicSelectUiState.areAllSelectableVisibleSongsSelected(): Boolean {
        val selectableVisibleIds = content.songItems
            .asSequence()
            .map { music -> music.id }
            .filterNot { id -> id in actions.lockedSongIds }
            .toSet()

        return selectableVisibleIds.isNotEmpty() &&
                actions.selectedSongIds.containsAll(selectableVisibleIds)
    }

    private fun sameMusicSet(
        first: MusicSet,
        second: MusicSet
    ): Boolean {
        if (first::class != second::class) return false

        return when {
            first is MusicSet.Folder && second is MusicSet.Folder -> {
                first.folderPath == second.folderPath
            }

            first is MusicSet.Playlist && second is MusicSet.Playlist -> {
                first.id == second.id
            }

            else -> first.id == second.id
        }
    }

    override fun onDestroy() {
        recyclerIndexBar.submitLabels(emptyList())
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MUSIC_SET = "KEY_MUSIC_SET"

        @JvmStatic
        fun start(
            context: Context,
            musicSet: MusicSet
        ) {
            require(isSupportedTarget(musicSet)) {
                "MusicSelectActivity only supports MusicSet.Playlist and MusicSet.Favorites."
            }

            context.startActivity(
                Intent(context, MusicSelectActivity::class.java).apply {
                    putExtra(EXTRA_MUSIC_SET, musicSet)
                }
            )
        }

        private fun isSupportedTarget(musicSet: MusicSet): Boolean {
            return musicSet is MusicSet.Playlist ||
                    musicSet is MusicSet.Favorites
        }
    }
}
