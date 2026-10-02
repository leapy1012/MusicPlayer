package gd.app.musicplayer.ui.selection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.coui.appcompat.searchview.COUISearchBar
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.databinding.ActivityMusicEditBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.hidden.HideSelectionUseCase
import gd.app.musicplayer.domain.usecase.library.RemoveTrackFromGeneratedMusicSetUseCase
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.RemoveFromPlayingQueueUseCase
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.RemoveTracksFromPlaylistUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksFromLibraryUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.feature.library.ARG_MUSIC
import gd.app.musicplayer.feature.library.ARG_MUSIC_SET
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.inflateThemedMenu
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.EditBottomMenuController
import gd.app.musicplayer.util.SimpleTextWatcher
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MusicEditActivity :
    BaseActivity(),
    MusicEditAdapter.SelectionCountChangedListener,
    Toolbar.OnMenuItemClickListener {

    @Inject lateinit var playTracksUseCase: PlayTracksUseCase
    @Inject lateinit var playNextTracksUseCase: PlayNextTracksUseCase
    @Inject lateinit var enqueueTracksUseCase: EnqueueTracksUseCase
    @Inject lateinit var removeFromPlayingQueueUseCase: RemoveFromPlayingQueueUseCase
    @Inject lateinit var removeTracksFromPlaylistUseCase: RemoveTracksFromPlaylistUseCase
    @Inject lateinit var removeTrackFromGeneratedMusicSetUseCase: RemoveTrackFromGeneratedMusicSetUseCase
    @Inject lateinit var deleteTracksFromLibraryUseCase: DeleteTracksFromLibraryUseCase
    @Inject lateinit var deleteTracksUseCase: DeleteTracksUseCase
    @Inject lateinit var hideSelectionUseCase: HideSelectionUseCase
    @Inject lateinit var addTracksToPlaylistsUseCase: AddTracksToPlaylistsUseCase

    private val viewModel: EditViewModel by viewModels()

    private lateinit var binding: ActivityMusicEditBinding
    private lateinit var adapter: MusicEditAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController

    private lateinit var musicSet: MusicSet

    private var selectedMusic: Music? = null
    private var initialTopOffset: Int = 0

    private var shouldApplyInitialSelection = true
    private var shouldScrollToInitialMusic = true
    private var appliedTracks: List<Music> = emptyList()

    private val recyclerView: MusicRecyclerView
        get() = binding.layoutRecyclerview.recyclerview

    private val searchWatcher = SimpleTextWatcher { text ->
        onSearchTextChanged(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!readIntentData()) {
            finish()
            return
        }

        binding = ActivityMusicEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupBottomMenu()
        loadTracksOnce()
    }

    override fun onDestroy() {
        binding.searchBar.searchEditText?.removeTextChangedListener(searchWatcher)
        super.onDestroy()
    }

    override fun onSelectionCountChanged(count: Int) {
        renderSelectionChrome(selectedCount = count)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_select_all -> {
                toggleSelectAll()
                true
            }
            else -> false
        }
    }

    fun getSelectedItems(): List<Music> = adapter.getSelectedItems()

    private fun readIntentData(): Boolean {
        musicSet = intent.parcelable(ARG_MUSIC_SET) ?: return false
        selectedMusic = intent.parcelable(ARG_MUSIC)
        initialTopOffset = intent.getIntExtra(
            ARG_TOP_OFFSET,
            intent.getIntExtra(ARG_OFFSET, DEFAULT_SCROLL_OFFSET)
        )
        return true
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.musicEditLayout,
            toolbar = binding.toolbar
        )
        binding.toolbar.menu.clear()
        binding.toolbar.inflateThemedMenu(R.menu.menu_fragment_select, ::applyThemeTo)
        binding.toolbar.setOnMenuItemClickListener(this)
    }

    private fun setupRecyclerView() = with(recyclerView) {
        layoutManager = LinearLayoutManager(
            this@MusicEditActivity,
            LinearLayoutManager.VERTICAL,
            false
        )
        // Original j0.H uses notifyDataSetChanged — no insert animations.
        itemAnimator = null
        adapter = buildAdapter().also {
            this@MusicEditActivity.adapter = it
        }
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = this,
            emptyViewStub = binding.layoutRecyclerview.layoutListEmpty
        ).apply {
            setEmptyMessage(getString(R.string.music_empty))
        }
    }

    private fun buildAdapter(): MusicEditAdapter {
        return MusicEditAdapter(
            recyclerView = recyclerView,
            accentColor = themeRepo.getAccentColor(),
            musicSet = musicSet,
            dragEnabled = musicSet.supportsDragReorder(),
            onOrderChanged = viewModel::updateTrackOrder
        ).apply {
            setSelectionCountListener(this@MusicEditActivity)
        }
    }

    private fun setupSearch() {
        val editText = binding.searchBar.searchEditText ?: return
        editText.addTextChangedListener(searchWatcher)
        binding.searchBar.setSearchAnimateType(COUISearchBar.TYPE_INSTANT_SEARCH)
    }

    private fun setupBottomMenu() {
        EditBottomMenuController(
            activity = this,
            musicSet = musicSet,
            menuContainer = binding.musicEditLayout,
            playTracksUseCase = playTracksUseCase,
            playNextTracksUseCase = playNextTracksUseCase,
            enqueueTracksUseCase = enqueueTracksUseCase,
            removeFromPlayingQueueUseCase = removeFromPlayingQueueUseCase,
            removeTracksFromPlaylistUseCase = removeTracksFromPlaylistUseCase,
            removeTrackFromGeneratedMusicSetUseCase = removeTrackFromGeneratedMusicSetUseCase,
            deleteTracksFromLibraryUseCase = deleteTracksFromLibraryUseCase,
            deleteTracksUseCase = deleteTracksUseCase,
            hideSelectionUseCase = hideSelectionUseCase,
            addTracksToPlaylistsUseCase = addTracksToPlaylistsUseCase
        ).bindMenu()
    }

    /**
     * Original ActivityEdit: G0 → background J0 → one UI M0.
     * No continuous Room/DataStore observe on open (that was the extra cost).
     * Mutations call [reloadTracks] once, same as original re-invoking B()/G0.
     */
    private fun loadTracksOnce() {
        lifecycleScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                viewModel.getTracks(musicSet)
            }
            applyTracks(tracks, isInitial = true)
        }
    }

    /** One-shot refresh after delete/hide (original re-runs G0 via library callback). */
    fun reloadTracks() {
        lifecycleScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                viewModel.getTracks(musicSet, forceRefresh = true)
            }
            applyTracks(tracks, isInitial = false)
        }
    }

    private fun applyTracks(
        tracks: List<Music>,
        isInitial: Boolean
    ) {
        appliedTracks = tracks
        val preselected = if (isInitial && shouldApplyInitialSelection) {
            shouldApplyInitialSelection = false
            selectedMusic
        } else {
            null
        }
        adapter.replaceAll(
            items = tracks,
            preselected = preselected
        )
        if (isInitial) {
            scrollToInitialMusicIfNeeded(tracks)
        }
        renderSelectionChrome(adapter.getSelectedItems().size)
        renderFilteredListChrome()
    }

    private fun scrollToInitialMusicIfNeeded(tracks: List<Music>) {
        if (!shouldScrollToInitialMusic) return
        val music = selectedMusic
        if (music == null) {
            shouldScrollToInitialMusic = false
            return
        }
        val index = tracks.indexOfFirst { it.id == music.id && it.data == music.data }
        // Original ActivityEdit.M0 only restores when index > 0.
        if (index <= 0) {
            shouldScrollToInitialMusic = false
            return
        }
        shouldScrollToInitialMusic = false
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        layoutManager.scrollToPositionWithOffset(index, initialTopOffset)
        // Original posts scrollToPosition after offset restore; no-op when already fully visible.
        recyclerView.post {
            layoutManager.scrollToPosition(index)
        }
    }

    private fun toggleSelectAll() {
        if (adapter.itemCount == 0) return
        adapter.setAllSelected(!adapter.areAllFilteredItemsSelected())
        renderSelectionChrome(adapter.getSelectedItems().size)
    }

    private fun onSearchTextChanged(text: String) {
        adapter.setSearchKeyword(text.trim())
        renderSelectionChrome(adapter.getSelectedItems().size)
        renderFilteredListChrome()
    }

    private fun renderSelectionChrome(selectedCount: Int) {
        val state = SelectionUiState(
            selectedCount = selectedCount,
            selectableCount = adapter.getFilteredItems().size
        )
        binding.toolbar.menu.findItem(R.id.menu_select_all)?.let { item ->
            item.isEnabled = state.hasSelectableItems
            item.icon?.alpha = when {
                !state.hasSelectableItems -> 102
                state.allSelected -> 255
                else -> 180
            }
        }
        binding.toolbar.title = musicSelectionTitle(
            selectedCount = selectedCount,
            emptyTitleRes = R.string.batch_edit
        )
    }

    private fun renderFilteredListChrome() {
        emptyStateController.setVisible(adapter.getFilteredItems().isEmpty())
    }

    private fun MusicSet.supportsDragReorder(): Boolean {
        return this is MusicSet.Playlist || this is MusicSet.Favorites
    }

    companion object {
        private const val ARG_OFFSET = "offset"
        private const val ARG_TOP_OFFSET = "topOffset"
        private const val DEFAULT_SCROLL_OFFSET = 0

        fun start(
            context: Context,
            musicSet: MusicSet,
            selectedMusic: Music? = null,
            offset: Int = DEFAULT_SCROLL_OFFSET
        ) {
            context.startActivityCompat(
                Intent(context, MusicEditActivity::class.java).apply {
                    putExtra(ARG_MUSIC_SET, musicSet)
                    putExtra(ARG_TOP_OFFSET, offset)
                    selectedMusic?.let { putExtra(ARG_MUSIC, it) }
                }
            )
        }
    }
}
