package gd.app.musicplayer.ui.feature.selection

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
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applyLengthFilter
import gd.app.musicplayer.core.extension.hideKeyboard
import gd.app.musicplayer.core.extension.parcelable
import gd.app.musicplayer.core.ui.view.MusicRecyclerView
import gd.app.musicplayer.core.ui.view.RecyclerIndexBar
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSelectBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.SortByContextMenu
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicSelectActivity : BaseActivity(), View.OnClickListener, TextWatcher, AdapterView.OnItemClickListener {

    private val viewModel: MusicSelectViewModel by viewModels()

    private lateinit var binding: ActivityMusicSelectBinding
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: MusicRecyclerView
    private lateinit var recyclerIndexBar: RecyclerIndexBar
    private lateinit var musicAdapter: MusicSelectAdapter
    private lateinit var folderAdapter: FolderSelectAdapter

    private val indexPositions = mutableListOf<Pair<String, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPlaylist = intent.parcelable<MusicSet>(EXTRA_MUSIC_SET)
        if (targetPlaylist == null) {
            finish()
            return
        }

        binding = ActivityMusicSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeViewModel()
        viewModel.initialize(targetPlaylist)
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

        musicAdapter = MusicSelectAdapter(
            accentColor = themeRepo.getAccentColor(),
            onSelectionToggle = viewModel::onSongClicked
        )
        folderAdapter = FolderSelectAdapter(
            accentColor = themeRepo.getAccentColor(),
            onItemClick = viewModel::onFolderClicked
        )
        recyclerView.adapter = musicAdapter

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
        binding.mainInfoSelectall.setOnClickListener(this)
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
        if (state.header.isBrowsingFolders) {
            if (recyclerView.adapter !== folderAdapter) {
                recyclerView.adapter = folderAdapter
            }
            folderAdapter.submitFolders(state.content.folderItems, state.header.query)
            emptyStateController.setEmptyImage(R.drawable.folder_empty_image)
            emptyStateController.setEmptyMessage(getString(R.string.folder_is_empty))
        } else {
            if (recyclerView.adapter !== musicAdapter) {
                recyclerView.adapter = musicAdapter
            }
            musicAdapter.submitMusicList(
                items = state.content.songItems,
                selectedIds = state.actions.selectedSongIds,
                lockedIds = state.actions.lockedSongIds,
                highlightQuery = state.header.query
            )
            emptyStateController.setEmptyImage(R.drawable.music_empty_image)
            emptyStateController.setEmptyMessage(getString(R.string.music_empty))
        }

        binding.searchEditClear.isVisible = state.header.query.isNotEmpty()
        binding.selectAllBanner.isVisible = !state.header.isBrowsingFolders
        binding.selectAllGroup.isVisible = !state.header.isBrowsingFolders && state.content.songItems.isNotEmpty()
        binding.buttonConfirm.isVisible = !state.header.isBrowsingFolders && state.actions.selectedSongIds.isNotEmpty()
        binding.buttonConfirm.isEnabled = state.actions.isConfirmEnabled
        emptyStateController.setVisible(state.content.isEmpty)

        val toolbarTitle = if (state.header.isBrowsingFolders) {
            getString(R.string.add_songs)
        } else {
            musicSelectionTitle(state.actions.selectedSongIds.size)
        }
        binding.toolbar.title = toolbarTitle
        binding.toolbar.menu.findItem(R.id.menu_switch)?.setIcon(
            if (state.header.isBrowsingFolders || state.header.selectedMusicSet is MusicSet.Folder) {
                R.drawable.vector_menu_switch_music
            } else {
                R.drawable.vector_menu_switch_folder
            }
        )

        val spinnerLabels = state.header.spinnerItems.map(::labelForSet).toTypedArray()
        val selectedIndex = state.header.spinnerItems.indexOfFirst { sameMusicSet(it, state.header.selectedMusicSet) }
        binding.mainInfoSpinner.setEntries(spinnerLabels)
        binding.mainInfoSpinner.setSelection(selectedIndex)

        binding.mainInfoSelectall.renderSelectAllState(
            SelectionUiState(
                selectedCount = state.actions.selectedSongIds.size,
                hasSelectableItems = state.content.songItems.any { it.id !in state.actions.lockedSongIds },
                allSelectableItemsSelected = musicAdapter.areAllSelectableVisibleSongsSelected()
            )
        )

        updateIndexBar(state)
    }

    private fun handleEvent(event: MusicSelectEvent) {
        when (event) {
            is MusicSelectEvent.ConfirmCompleted -> {
                val messageRes = if (event.result.insertedCount > 0) {
                    R.string.succeed
                } else {
                    R.string.list_contains_music
                }
                ToastUtil.show(this, messageRes)
                val target = viewModel.uiState.value.targetPlaylist
                if (target != null) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_MUSIC_SET, target))
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
                SortByContextMenu(
                    context = this,
                    musicSet = if (state.header.isBrowsingFolders) MusicSet.Folders else state.header.selectedMusicSet,
                    selectionMode = true,
                    currentSortStyle = state.header.currentSortStyle,
                    currentSortDescending = state.header.currentSortDescending,
                    onSortChanged = { style, descending ->
                        viewModel.onSortChanged(style, descending)
                    },
                    accentColor = themeRepo.getAccentColor(),
                    popupBackgroundProvider = { menuContext ->
                        themeRepo.getCorePalette().getPopupBackgroundDrawable(menuContext)
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

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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
        val source = if (state.header.isBrowsingFolders) {
            state.content.folderItems.map { it.name }
        } else {
            state.content.songItems.map { it.title }
        }
        source.forEachIndexed { index, value ->
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
            else -> set.name
        }
    }

    private fun sameMusicSet(first: MusicSet, second: MusicSet): Boolean {
        if (first::class != second::class) return false
        return when {
            first is MusicSet.Folder && second is MusicSet.Folder -> first.folderPath == second.folderPath
            first is MusicSet.Playlist && second is MusicSet.Playlist -> first.id == second.id
            else -> first.id == second.id
        }
    }

//    private fun applyMusicSelectTheme(theme: ThemePalette) {
//        val selectState = ColorStateList(
//            arrayOf(
//                intArrayOf(android.R.attr.state_selected),
//                intArrayOf()
//            ),
//            intArrayOf(theme.accentColor, theme.itemTextColor)
//        )
//
//        binding.mainInfoSelectall.imageTintList = selectState
//        binding.buttonConfirm.background = DrawableUtil.roundedRipple(
//            fillColor = theme.accentColor,
//            rippleColor = theme.confirmRippleColor,
//            radius = 1000f
//        )
//        binding.buttonConfirm.setTextColor(0xFFFFFFFF.toInt())
//        binding.musicSelectTitleDivider.setBackgroundColor(theme.dividerColor)
//        recyclerIndexBar.setTextColor(theme.titleColor)
//
//        binding.toolbar.navigationIcon?.setTint(theme.titleColor)
//        binding.toolbar.overflowIcon?.setTint(theme.titleColor)
//        for (index in 0 until binding.toolbar.menu.size()) {
//            binding.toolbar.menu.getItem(index).icon?.setTint(theme.titleColor)
//        }
//
//        val hintColor = ColorUtils.setAlphaComponent(theme.titleColor, 128)
//        binding.searchEditText.setTextColor(theme.titleColor)
//        binding.searchEditText.setHintTextColor(hintColor)
//        binding.searchEditClear.imageTintList = ColorStateList.valueOf(theme.titleColor)
//        binding.mainInfoSpinner.setTextColor(theme.titleColor)
//    }

    override fun onDestroy() {
        recyclerIndexBar.submitLabels(emptyList())
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MUSIC_SET = "KEY_MUSIC_SET"

        @JvmStatic
        fun start(context: Context, musicSet: MusicSet) {
            context.startActivity(Intent(context, MusicSelectActivity::class.java).apply {
                putExtra(EXTRA_MUSIC_SET, musicSet)
            })
        }
    }
}
