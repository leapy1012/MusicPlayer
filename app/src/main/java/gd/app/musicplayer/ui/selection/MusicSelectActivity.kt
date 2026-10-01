package gd.app.musicplayer.ui.selection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.coui.appcompat.searchview.COUISearchBar
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyLengthFilter
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.titleColor
import gd.app.musicplayer.databinding.ActivityMusicSelectBinding
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.inflateThemedMenu
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
            binding.searchBar.searchEditText?.hideKeyboard()
        }
    }

    override fun onDestroy() {
        binding.searchBar.searchEditText?.removeTextChangedListener(searchWatcher)
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

        binding.toolbar.inflateThemedMenu(R.menu.menu_activity_music_select, ::applyThemeTo)
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

    private fun setupSearch() {
        val editText = binding.searchBar.searchEditText ?: return
        editText.applyLengthFilter(MAX_SEARCH_LENGTH)
        editText.addTextChangedListener(searchWatcher)
        editText.setOnEditorActionListener { _, _, _ ->
            editText.hideKeyboard()
            false
        }
        binding.searchBar.setSearchAnimateType(COUISearchBar.TYPE_INSTANT_SEARCH)
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
        renderActions(state)
        renderToolbar(state)
        renderSpinner(state)
        renderSelectAll(state)
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

        emptyStateController.setEmptyLottieAsset("music_empty.json")
        emptyStateController.setEmptyMessage(getString(R.string.music_empty))
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

        menu.findItem(R.id.menu_switch)?.let { item ->
            item.icon = tintedMenuIcon(
                if (state.shouldShowMusicSwitchIcon()) {
                    R.drawable.vector_menu_switch_music
                } else {
                    R.drawable.vector_menu_switch_folder
                }
            )
        }

        menu.findItem(R.id.menu_sort)?.let { item ->
            item.isVisible = state.shouldShowSortMenu()
            item.icon = tintedMenuIcon(R.drawable.vector_sort_by)
        }
    }

    private fun tintedMenuIcon(iconRes: Int): android.graphics.drawable.Drawable? {
        val drawable = AppCompatResources.getDrawable(this, iconRes)?.mutate() ?: return null
        val color = resolveToolbarIconColor()
        DrawableCompat.setTint(drawable, color)
        return drawable
    }

    private fun resolveToolbarIconColor(): Int {
        val palette = themeRepo.getCorePalette()
        return if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
            val typed = obtainStyledAttributes(
                intArrayOf(com.coui.appcompat.R.attr.couiColorPrimaryNeutral)
            )
            val color = typed.getColor(0, 0xE6000000.toInt())
            typed.recycle()
            color
        } else {
            palette.titleColor
        }
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

    private fun handleEvent(event: MusicSelectEvent) {
        when (event) {
            is MusicSelectEvent.ConfirmSubmitted -> {
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_MUSIC_SET, event.targetPlaylist)
                )
                finish()
            }
        }
    }

    private fun onToolbarMenuItemClicked(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_switch -> {
                viewModel.onSwitchSourceClicked()
                binding.searchBar.searchEditText?.hideKeyboard()
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
        binding.searchBar.searchEditText?.hideKeyboard()

        val state = viewModel.uiState.value
        SortByContextMenu(
            context = this,
            musicSet = if (state.header.isBrowsingFolders) {
                MusicSet.Folders
            } else {
                state.header.selectedMusicSet
            },
            currentSortStyle = state.header.currentSortStyle,
            currentSortDescending = state.header.currentSortDescending,
            onSortChanged = viewModel::onSortChanged
        ).show(binding.toolbar.findViewById(item.itemId) ?: binding.toolbar)
    }

    private fun onSpinnerItemClicked(position: Int) {
        if (renderingSpinner) return

        val selected = viewModel.uiState.value.header.spinnerItems
            .getOrNull(position)
            ?: return

        viewModel.onSpinnerMusicSetSelected(selected)
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
