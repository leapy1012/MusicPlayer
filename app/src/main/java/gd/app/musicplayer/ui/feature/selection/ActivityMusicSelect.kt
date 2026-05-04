package gd.app.musicplayer.ui.feature.selection

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSelectBinding
import gd.app.musicplayer.domain.usecase.selection.MusicSelectConfirmRequest
import gd.app.musicplayer.domain.usecase.selection.MusicSelectLoadRequest
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.SortByContextMenu
import gd.app.musicplayer.core.extension.applyLengthFilter
import gd.app.musicplayer.core.extension.hideKeyboard
import gd.app.musicplayer.core.extension.parcelable
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.core.ui.view.RecyclerIndexBar
import gd.app.musicplayer.core.ui.view.MusicRecyclerView
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ActivityMusicSelect : BaseActivity(), View.OnClickListener, TextWatcher,
    AdapterView.OnItemClickListener {

    private val viewModel: MusicSelectViewModel by viewModels()
    private val preferenceUtil by lazy { appDependencies.preferenceUtil }
    private val themeRepo by lazy { appDependencies.themeRepo }

    private lateinit var binding: ActivityMusicSelectBinding
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: MusicRecyclerView
    private lateinit var recyclerIndexBar: RecyclerIndexBar
    private lateinit var musicAdapter: MusicSelectAdapter
    private lateinit var folderAdapter: FolderSelectAdapter

    private var targetPlaylist: MusicSet? = null
    private var selectedMusicSet: MusicSet = MusicSet.Tracks
    private var selectedMusicSetBeforeSourceMode: MusicSet = MusicSet.Tracks
    private var isSourceMode: Boolean = false

    private var allLoadedSongs: List<Music> = emptyList()
    private var visibleSongs: List<Music> = emptyList()
    private var allFolders: List<MusicSet.Folder> = emptyList()
    private var visibleFolders: List<MusicSet.Folder> = emptyList()
    private var spinnerItems: List<MusicSet> = emptyList()

    private val selectedSongIds = linkedSetOf<Long>()
    private var lockedSongIds: Set<Long> = emptySet()

    private val indexPositions = mutableListOf<Pair<String, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPlaylist = intent.parcelable(EXTRA_MUSIC_SET)
        if (targetPlaylist == null) {
            finish()
            return
        }

        binding = ActivityMusicSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeViewModel()
        requestLoad()
    }

    

//    override fun applyTheme() {
//        applyMusicSelectTheme(themeRepo.getCorePalette(this))
//    }

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

        musicAdapter = MusicSelectAdapter(::toggleSongSelection)
        folderAdapter = FolderSelectAdapter(::onFolderSelected)
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
        recyclerIndexBar.onLabelSelected = { label: String ->
            val targetIndex = indexPositions.firstOrNull { it.first == label }?.second
            if (targetIndex != null) {
                (recyclerView.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(targetIndex, 0)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!isSourceMode && selectedMusicSet is MusicSet.Folder) {
                isSourceMode = true
                refreshUiForMode()
                requestLoad()
            } else {
                finish()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MusicSelectAsyncEvent.LoadCompleted -> handleLoadCompleted(event)
                        is MusicSelectAsyncEvent.ConfirmCompleted -> handleConfirmCompleted(event)
                    }
                }
            }
        }
    }

    private fun handleLoadCompleted(event: MusicSelectAsyncEvent.LoadCompleted) {
        val query = binding.searchEditText.text?.toString().orEmpty()

        if (event.request.sourceMode != isSourceMode) {
            return
        }

        if (isSourceMode) {
            allFolders = event.result.sourceSets.filterIsInstance<MusicSet.Folder>()
            applyFolderFilter(query)
            return
        }

        allLoadedSongs = event.result.selectedSetSongs
        lockedSongIds = event.result.targetSetSongs.mapTo(hashSetOf(), Music::id)
        selectedSongIds.retainAll(allLoadedSongs.mapTo(hashSetOf(), Music::id))
        updateSpinnerItems(event.result.spinnerCandidates)
        applySongFilter(query)
    }

    private fun handleConfirmCompleted(event: MusicSelectAsyncEvent.ConfirmCompleted) {
        binding.buttonConfirm.isEnabled = true

        val messageRes = if (event.result.insertedCount > 0) {
            R.string.succeed
        } else {
            R.string.list_contains_music
        }
        ToastUtil.show(this, messageRes)

        targetPlaylist?.let { playlist ->
            setResult(RESULT_OK, Intent().putExtra(EXTRA_MUSIC_SET, playlist))
        }
        finish()
    }

    private fun requestLoad() {
        refreshUiForMode()
        viewModel.load(
            MusicSelectLoadRequest(
                sourceMode = isSourceMode,
                selectedSet = selectedMusicSet,
                sourceCategory = MusicSet.Folders,
                targetSet = targetPlaylist
            )
        )
    }

    private fun onToolbarMenuItemClicked(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_switch -> {
                if (isSourceMode || selectedMusicSet is MusicSet.Folder) {
                    isSourceMode = false
                    selectedMusicSet = selectedMusicSetBeforeSourceMode
                } else {
                    isSourceMode = true
                    selectedMusicSetBeforeSourceMode = selectedMusicSet
                }
                requestLoad()
                binding.searchEditText.hideKeyboard()
                true
            }

            R.id.menu_sort -> {
                binding.searchEditText.hideKeyboard()
                openSortMenu(binding.toolbar.findViewById(item.itemId) ?: binding.toolbar)
                true
            }

            else -> false
        }
    }

    private fun openSortMenu(anchor: View) {
        if (isSourceMode) {
            openFolderSourceSortMenu(anchor)
            return
        }

        SortByContextMenu(
            context = this,
            musicSet = selectedMusicSet,
            selectionMode = true
        ) { _, _ ->
            requestLoad()
        }.show(anchor)
    }

    private fun openFolderSourceSortMenu(anchor: View) {
        val currentStyle = preferenceUtil.getFolderSortStyle(selectionMode = true)
        val reversed = preferenceUtil.isFolderSortReversed(selectionMode = true)

        PopupMenu(this, anchor).apply {
            menu.add(0, SORT_FOLDER_NAME, 0, getString(R.string.sort_title))
            menu.add(0, SORT_FOLDER_TRACK_COUNT, 1, getString(R.string.sort_track_number))
            menu.add(0, SORT_FOLDER_DATE, 2, getString(R.string.sort_add_time))
            menu.add(0, SORT_FOLDER_REVERSE, 3, getString(R.string.sort_reverse_all))

            menu.findItem(
                when (currentStyle) {
                    "track_count" -> SORT_FOLDER_TRACK_COUNT
                    "date" -> SORT_FOLDER_DATE
                    else -> SORT_FOLDER_NAME
                }
            )?.isChecked = true
            menu.findItem(SORT_FOLDER_REVERSE)?.isChecked = reversed

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    SORT_FOLDER_NAME -> preferenceUtil.setFolderSortStyle("name", selectionMode = true)
                    SORT_FOLDER_TRACK_COUNT -> preferenceUtil.setFolderSortStyle("track_count", selectionMode = true)
                    SORT_FOLDER_DATE -> preferenceUtil.setFolderSortStyle("date", selectionMode = true)
                    SORT_FOLDER_REVERSE -> preferenceUtil.setFolderSortReversed(
                        !preferenceUtil.isFolderSortReversed(selectionMode = true),
                        selectionMode = true
                    )
                }
                requestLoad()
                true
            }
        }.show()
    }

    private fun onFolderSelected(folder: MusicSet.Folder) {
        if (selectedMusicSet == folder && !isSourceMode) return
        selectedSongIds.clear()
        selectedMusicSet = folder
        isSourceMode = false
        requestLoad()
    }

    private fun updateSpinnerItems(candidates: List<MusicSet>) {
        spinnerItems = if (selectedMusicSet is MusicSet.Folder) {
            candidates.filterIsInstance<MusicSet.Folder>()
        } else {
            buildList {
                add(MusicSet.Tracks)
                add(MusicSet.Favorites)
                add(MusicSet.RecentlyAdded)
                candidates
                    .filterNot { it.id == targetPlaylist?.id }
                    .forEach { candidate ->
                        if (none { sameMusicSet(it, candidate) }) {
                            add(candidate)
                        }
                    }
            }
        }

        val labels = spinnerItems.map(::labelForSet).toTypedArray()
        val selectedIndex = spinnerItems.indexOfFirst { sameMusicSet(it, selectedMusicSet) }
        binding.mainInfoSpinner.setEntries(labels)
        binding.mainInfoSpinner.setSelection(selectedIndex)
    }

    private fun applySongFilter(queryRaw: String) {
        val query = queryRaw.trim()
        visibleSongs = if (query.isBlank()) {
            allLoadedSongs
        } else {
            allLoadedSongs.filter { music ->
                music.title.contains(query, ignoreCase = true)
            }
        }

        musicAdapter.submitMusicList(
            items = visibleSongs,
            selectedIds = selectedSongIds,
            lockedIds = lockedSongIds,
            highlightQuery = query
        )

        binding.searchEditClear.isVisible = query.isNotEmpty()
        binding.selectAllGroup.isVisible = visibleSongs.isNotEmpty()
        emptyStateController.setVisible(visibleSongs.isEmpty())
        refreshSelectionUi()
        updateIndexBar()
    }

    private fun applyFolderFilter(queryRaw: String) {
        val query = queryRaw.trim()
        visibleFolders = if (query.isBlank()) {
            allFolders
        } else {
            allFolders.filter { folder ->
                folder.name.contains(query, ignoreCase = true) ||
                    folder.folderPath.contains(query, ignoreCase = true)
            }
        }

        folderAdapter.submitFolders(visibleFolders, query)
        binding.searchEditClear.isVisible = query.isNotEmpty()
        emptyStateController.setVisible(visibleFolders.isEmpty())
        refreshSelectionUi()
        updateIndexBar()
    }

    private fun refreshUiForMode() {
        binding.selectAllBanner.isVisible = !isSourceMode
        binding.buttonConfirm.isVisible = !isSourceMode && selectedSongIds.isNotEmpty()

        if (isSourceMode) {
            if (recyclerView.adapter !== folderAdapter) {
                recyclerView.adapter = folderAdapter
            }
            emptyStateController.setEmptyImage(R.drawable.folder_empty_image)
            emptyStateController.setEmptyMessage(getString(R.string.folder_is_empty))
            binding.toolbar.title = getString(R.string.add_songs)
            binding.toolbar.menu.findItem(R.id.menu_switch)
                ?.setIcon(R.drawable.vector_menu_switch_music)
        } else {
            if (recyclerView.adapter !== musicAdapter) {
                recyclerView.adapter = musicAdapter
            }
            emptyStateController.setEmptyImage(R.drawable.music_empty_image)
            emptyStateController.setEmptyMessage(getString(R.string.music_empty))
            binding.toolbar.menu.findItem(R.id.menu_switch)?.setIcon(
                if (selectedMusicSet is MusicSet.Folder) {
                    R.drawable.vector_menu_switch_music
                } else {
                    R.drawable.vector_menu_switch_folder
                }
            )
            refreshSelectionUi()
        }
    }

    private fun refreshSelectionUi() {
        if (isSourceMode) {
            binding.toolbar.title = getString(R.string.add_songs)
            return
        }

        val count = selectedSongIds.size
        binding.toolbar.title = musicSelectionTitle(count)
        binding.buttonConfirm.renderSelectionVisibility(count)
        binding.mainInfoSelectall.renderSelectAllState(
            SelectionUiState(
                selectedCount = count,
                hasSelectableItems = visibleSongs.any { it.id !in lockedSongIds },
                allSelectableItemsSelected = musicAdapter.areAllSelectableVisibleSongsSelected()
            )
        )
    }

    private fun updateIndexBar() {
        indexPositions.clear()
        val source = if (isSourceMode) {
            visibleFolders.map { it.name }
        } else {
            visibleSongs.map { it.title }
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

    private fun toggleSongSelection(music: Music) {
        if (music.id in lockedSongIds) return
        if (!selectedSongIds.add(music.id)) {
            selectedSongIds.remove(music.id)
        }
        musicAdapter.updateSelection(selectedSongIds)
        refreshSelectionUi()
    }

    private fun confirmSelection() {
        if (selectedSongIds.isEmpty()) {
            ToastUtil.show(this, R.string.select_musics_empty)
            return
        }

        val target = targetPlaylist ?: return
        val selectedSongs = allLoadedSongs.filter { it.id in selectedSongIds }
        if (selectedSongs.isEmpty()) {
            ToastUtil.show(this, R.string.select_musics_empty)
            return
        }

        binding.buttonConfirm.isEnabled = false
        viewModel.confirm(
            MusicSelectConfirmRequest(
                selectedSongs = selectedSongs,
                targetSet = target
            )
        )
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.search_edit_clear -> binding.searchEditText.setText("")
            R.id.main_info_selectall -> {
                val selectableIds = visibleSongs
                    .asSequence()
                    .map(Music::id)
                    .filterNot { it in lockedSongIds }
                    .toSet()
                val allSelected = selectableIds.isNotEmpty() && selectedSongIds.containsAll(selectableIds)
                if (allSelected) {
                    selectedSongIds.removeAll(selectableIds)
                } else {
                    selectedSongIds.addAll(selectableIds)
                }
                musicAdapter.updateSelection(selectedSongIds)
                refreshSelectionUi()
            }

            R.id.button_confirm -> confirmSelection()
        }
    }

    override fun afterTextChanged(editable: Editable) {
        if (isSourceMode) {
            applyFolderFilter(editable.toString())
        } else {
            applySongFilter(editable.toString())
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selected = spinnerItems.getOrNull(position) ?: return
        if (sameMusicSet(selectedMusicSet, selected)) {
            return
        }

        selectedSongIds.clear()
        selectedMusicSet = selected
        requestLoad()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            binding.searchEditText.hideKeyboard()
        }
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

    private fun applyMusicSelectTheme(theme: ThemePalette) {
        val selectState = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf()
            ),
            intArrayOf(theme.accentColor, theme.itemTextColor)
        )

        binding.mainInfoSelectall.imageTintList = selectState
        binding.buttonConfirm.background = DrawableUtil.roundedRipple(
            fillColor = theme.accentColor,
            rippleColor = theme.confirmRippleColor,
            radius = 1000f
        )
        binding.buttonConfirm.setTextColor(0xFFFFFFFF.toInt())
        binding.musicSelectTitleDivider.setBackgroundColor(theme.dividerColor)
        recyclerIndexBar.setTextColor(theme.titleColor)

        binding.toolbar.navigationIcon?.setTint(theme.titleColor)
        binding.toolbar.overflowIcon?.setTint(theme.titleColor)
        for (index in 0 until binding.toolbar.menu.size()) {
            binding.toolbar.menu.getItem(index).icon?.setTint(theme.titleColor)
        }

        val hintColor = ColorUtils.setAlphaComponent(theme.titleColor, 128)
        binding.searchEditText.setTextColor(theme.titleColor)
        binding.searchEditText.setHintTextColor(hintColor)
        binding.searchEditClear.imageTintList = ColorStateList.valueOf(theme.titleColor)
        binding.mainInfoSpinner.setTextColor(theme.titleColor)
    }

    override fun onDestroy() {
        recyclerIndexBar.submitLabels(emptyList())
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MUSIC_SET = "KEY_MUSIC_SET"

        private const val SORT_FOLDER_NAME = 1
        private const val SORT_FOLDER_TRACK_COUNT = 2
        private const val SORT_FOLDER_DATE = 3
        private const val SORT_FOLDER_REVERSE = 4

        @JvmStatic
        fun start(context: Context, musicSet: MusicSet) {
            context.startActivity(Intent(context, ActivityMusicSelect::class.java).apply {
                putExtra(EXTRA_MUSIC_SET, musicSet)
            })
        }
    }
}
