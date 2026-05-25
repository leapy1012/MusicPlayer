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
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.popupTitleColor
import gd.app.musicplayer.databinding.ActivityMusicSelectBinding
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.SortByContextMenu
import gd.app.musicplayer.util.SimpleTextWatcher
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicSelectActivity : BaseActivity() {

    private val viewModel: MusicSelectViewModel by viewModels()

    private lateinit var binding: ActivityMusicSelectBinding
    private lateinit var emptyStateController: RecyclerEmptyStateController

    private lateinit var musicAdapter: MusicSelectAdapter
    private lateinit var folderAdapter: FolderSelectAdapter
    private lateinit var concatAdapter: ConcatAdapter

    private var indexEntries: List<IndexEntry> = emptyList()
    private var renderingSpinner = false

    private val searchWatcher = SimpleTextWatcher { query ->
        viewModel.onQueryChanged(query)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = intent.parcelable<MusicSet>(EXTRA_MUSIC_SET)

        if (target == null || !isSupportedTarget(target)) {
            finish()
            return
        }

        binding = ActivityMusicSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupActions()
        setupBackPress()
        observeViewModel()

        viewModel.initialize(target)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus) {
            binding.searchEditText.hideKeyboard()
        }
    }

    override fun onDestroy() {
        binding.searchEditText.removeTextChangedListener(searchWatcher)
        binding.layoutRecyclerview.recyclerviewIndex.submitLabels(emptyList())
        super.onDestroy()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.add_songs
        )

        binding.toolbar.inflateMenu(R.menu.menu_activity_music_select)
        binding.toolbar.setOnMenuItemClickListener(::onToolbarMenuItemClicked)
    }

    private fun setupRecyclerView() = with(binding.layoutRecyclerview.recyclerview) {
        layoutManager = LinearLayoutManager(this@MusicSelectActivity)
        itemAnimator = null

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

        adapter = concatAdapter

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = this,
            emptyViewStub = binding.layoutRecyclerview.layoutListEmpty
        )
    }

    private fun setupSearch() = with(binding) {
        searchEditText.applyLengthFilter(MAX_SEARCH_LENGTH)
        searchEditText.addTextChangedListener(searchWatcher)
        searchEditText.setOnEditorActionListener { _, _, _ ->
            searchEditText.hideKeyboard()
            false
        }

        searchEditClear.setOnClickListener {
            searchEditText.text = null
        }
    }

    private fun setupActions() = with(binding) {
        selectAllGroup.setOnClickListener {
            viewModel.onSelectAllClicked()
        }

        buttonConfirm.setOnClickListener {
            viewModel.confirmSelection()
        }

        mainInfoSpinner.setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            onSpinnerItemClicked(position)
        }

        layoutRecyclerview.recyclerviewIndex.onLabelSelected = ::scrollToIndexLabel
    }

    private fun setupBackPress() {
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
        renderIndexBar(state)
    }

    private fun renderList(state: MusicSelectUiState) {
        if (state.header.isBrowsingFolders) {
            renderFolderMode(state)
        } else {
            renderSongMode(state)
        }

        emptyStateController.setVisible(state.content.isEmpty)
    }

    private fun renderFolderMode(state: MusicSelectUiState) {
        folderAdapter.submitFolders(
            items = state.content.folderItems,
            highlightQuery = state.header.query
        )

        musicAdapter.submitMusicList(
            items = emptyList(),
            selectedIds = emptySet(),
            lockedIds = emptySet(),
            highlightQuery = state.header.query
        )

        emptyStateController.setEmptyImage(R.drawable.folder_empty_image)
        emptyStateController.setEmptyMessage(getString(R.string.folder_is_empty))
    }

    private fun renderSongMode(state: MusicSelectUiState) {
        folderAdapter.submitFolders(
            items = emptyList(),
            highlightQuery = state.header.query
        )

        musicAdapter.submitMusicList(
            items = state.content.songItems,
            selectedIds = state.actions.selectedSongIds,
            lockedIds = state.actions.lockedSongIds,
            highlightQuery = state.header.query
        )

        emptyStateController.setEmptyImage(R.drawable.music_empty_image)
        emptyStateController.setEmptyMessage(getString(R.string.music_empty))
    }

    private fun renderSearch(state: MusicSelectUiState) {
        binding.searchEditClear.isVisible = state.header.query.isNotEmpty()
    }

    private fun renderActions(state: MusicSelectUiState) = with(binding) {
        val isSongMode = !state.header.isBrowsingFolders
        val hasSongs = state.content.songItems.isNotEmpty()
        val hasSelection = state.actions.selectedSongIds.isNotEmpty()

        selectAllBanner.isVisible = isSongMode
        selectAllGroup.isVisible = isSongMode && hasSongs
        buttonConfirm.isVisible = isSongMode && hasSelection
        buttonConfirm.isEnabled = state.actions.isConfirmEnabled
    }

    private fun renderToolbar(state: MusicSelectUiState) = with(binding.toolbar) {
        title = if (state.header.isBrowsingFolders) {
            getString(R.string.add_songs)
        } else {
            musicSelectionTitle(
                selectedCount = state.actions.selectedSongIds.size,
                emptyTitleRes = R.string.add_songs
            )
        }

        menu.findItem(R.id.menu_switch)?.setIcon(
            if (state.shouldShowMusicSwitchIcon()) {
                R.drawable.vector_menu_switch_music
            } else {
                R.drawable.vector_menu_switch_folder
            }
        )

        menu.findItem(R.id.menu_sort)?.isVisible = state.shouldShowSortMenu()
    }

    private fun renderSpinner(state: MusicSelectUiState) {
        val spinnerLabels = state.header.spinnerItems
            .map(::labelForSet)
            .toTypedArray()

        val selectedIndex = state.header.spinnerItems.indexOfFirst { item ->
            item.sameIdentityAs(state.header.selectedMusicSet)
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
                selectableCount = state.selectableVisibleSongCount()
            )
        )
    }

    private fun renderIndexBar(state: MusicSelectUiState) {
        indexEntries = state.indexEntries()
        binding.layoutRecyclerview.recyclerviewIndex.submitLabels(
            indexEntries.map(IndexEntry::label)
        )
    }

    private fun handleEvent(event: MusicSelectEvent) {
        when (event) {
            is MusicSelectEvent.ConfirmCompleted -> {
                ToastUtil.show(
                    this,
                    if (event.insertedCount > 0) {
                        R.string.succeed
                    } else {
                        R.string.list_contains_music
                    }
                )

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
                showSortMenu(item)
                true
            }

            else -> false
        }
    }

    private fun showSortMenu(item: MenuItem) {
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
    }

    private fun onSpinnerItemClicked(position: Int) {
        if (renderingSpinner) return

        val selected = viewModel.uiState.value.header.spinnerItems
            .getOrNull(position)
            ?: return

        viewModel.onSpinnerMusicSetSelected(selected)
    }

    private fun scrollToIndexLabel(label: String) {
        val position = indexEntries
            .firstOrNull { entry -> entry.label == label }
            ?.position
            ?: return

        (binding.layoutRecyclerview.recyclerview.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, 0)
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

    private fun MusicSelectUiState.shouldShowMusicSwitchIcon(): Boolean {
        return header.isBrowsingFolders || header.selectedMusicSet is MusicSet.Folder
    }

    private fun MusicSelectUiState.shouldShowSortMenu(): Boolean {
        return header.isBrowsingFolders || header.selectedMusicSet !is MusicSet.Folder
    }

    private fun MusicSelectUiState.selectableVisibleSongCount(): Int {
        return content.songItems.count { music ->
            music.id !in actions.lockedSongIds
        }
    }

    private fun MusicSelectUiState.indexEntries(): List<IndexEntry> {
        val source = if (header.isBrowsingFolders) {
            content.folderItems.map { item -> item.name }
        } else {
            content.songItems.map { item -> item.title }
        }

        val entries = linkedMapOf<String, Int>()

        source.forEachIndexed { index, value ->
            val label = value.toIndexLabel() ?: return@forEachIndexed
            entries.putIfAbsent(label, index)
        }

        return entries.map { (label, position) ->
            IndexEntry(
                label = label,
                position = position
            )
        }
    }

    private fun String.toIndexLabel(): String? {
        val first = trim().firstOrNull()?.uppercaseChar() ?: return null
        return if (first.isLetter()) first.toString() else "#"
    }

    private fun MusicSet.sameIdentityAs(other: MusicSet): Boolean {
        if (this::class != other::class) return false

        return when {
            this is MusicSet.Folder && other is MusicSet.Folder -> {
                folderPath == other.folderPath
            }

            this is MusicSet.Playlist && other is MusicSet.Playlist -> {
                id == other.id
            }

            else -> id == other.id
        }
    }

    companion object {
        private const val EXTRA_MUSIC_SET = "KEY_MUSIC_SET"
        private const val MAX_SEARCH_LENGTH = 120

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

private data class IndexEntry(
    val label: String,
    val position: Int
)