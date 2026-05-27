package gd.app.musicplayer.ui.selection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.core.designsystem.view.RecyclerIndexBar
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
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.EditBottomMenuController
import gd.app.musicplayer.util.SimpleTextWatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicEditActivity : BaseActivity(),
    MusicEditAdapter.SelectionCountChangedListener {

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
    private lateinit var selectAllImage: ImageView

    private lateinit var musicSet: MusicSet

    private var selectedMusic: Music? = null
    private var initialTopOffset: Int = 0

    private var shouldApplyInitialSelection = true
    private var shouldScrollToInitialMusic = true

    private val recyclerView: MusicRecyclerView
        get() = binding.layoutRecyclerview.recyclerview

    private val indexBar: RecyclerIndexBar
        get() = binding.layoutRecyclerview.recyclerviewIndex

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
        observeTracks()
    }

    override fun onDestroy() {
        binding.searchEditText.removeTextChangedListener(searchWatcher)
        super.onDestroy()
    }

    override fun onSelectionCountChanged(count: Int) {
        renderSelectionChrome(selectedCount = count)
    }

    fun getSelectedItems(): List<Music> {
        return adapter.getSelectedItems()
    }

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

        selectAllImage = binding.toolbar.installSelectAllAction(layoutInflater) {
            toggleSelectAll()
        }
    }

    private fun setupRecyclerView() = with(recyclerView) {
        layoutManager = LinearLayoutManager(
            this@MusicEditActivity,
            LinearLayoutManager.VERTICAL,
            false
        )

        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        adapter = buildAdapter().also {
            this@MusicEditActivity.adapter = it
        }

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = this,
            emptyViewStub = binding.layoutRecyclerview.layoutListEmpty
        ).apply {
            setEmptyMessage(getString(R.string.music_empty))
        }

        indexBar.onLabelSelected = ::scrollToLabel
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

    private fun setupSearch() = with(binding) {
        searchEditClear.setOnClickListener {
            searchEditText.text = null
        }

        searchEditText.addTextChangedListener(searchWatcher)
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

    private fun observeTracks() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeTracks(musicSet).collect(::renderTracks)
            }
        }
    }

    private fun renderTracks(tracks: List<Music>) {
        adapter.submitList(tracks)

        applyInitialSelectionIfNeeded()
        scrollToInitialMusicIfNeeded(tracks)

        renderSelectionChrome(adapter.getSelectedItems().size)
        renderFilteredListChrome()
    }

    private fun applyInitialSelectionIfNeeded() {
        if (!shouldApplyInitialSelection) return

        shouldApplyInitialSelection = false

        selectedMusic?.let { music ->
            adapter.selectItem(music)
        }
    }

    private fun scrollToInitialMusicIfNeeded(tracks: List<Music>) {
        if (!shouldScrollToInitialMusic) return

        val music = selectedMusic
        if (music == null) {
            shouldScrollToInitialMusic = false
            return
        }

        val index = tracks.indexOfFirst { item ->
            item.id == music.id && item.data == music.data
        }

        if (index < 0) {
            shouldScrollToInitialMusic = false
            return
        }

        shouldScrollToInitialMusic = false

        recyclerView.post {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                ?: return@post

            layoutManager.scrollToPositionWithOffset(
                index,
                initialTopOffset
            )
        }
    }

    private fun toggleSelectAll() {
        if (adapter.itemCount == 0) return

        val shouldSelectAll = !adapter.areAllFilteredItemsSelected()
        adapter.setAllSelected(shouldSelectAll)

        renderSelectionChrome(adapter.getSelectedItems().size)
    }

    private fun onSearchTextChanged(text: String) {
        val keyword = text.trim()

        adapter.setSearchKeyword(keyword)

        binding.searchEditClear.visibility =
            if (keyword.isEmpty()) View.GONE else View.VISIBLE

        renderSelectionChrome(adapter.getSelectedItems().size)
        renderFilteredListChrome()
    }

    private fun renderSelectionChrome(selectedCount: Int) {
        val filteredItemCount = adapter.getFilteredItems().size

        selectAllImage.renderSelectAllState(
            SelectionUiState(
                selectedCount = selectedCount,
                selectableCount = filteredItemCount
            )
        )

        binding.toolbar.title = musicSelectionTitle(
            selectedCount = selectedCount,
            emptyTitleRes = R.string.batch_edit
        )
    }

    private fun renderFilteredListChrome() {
        val filteredItems = adapter.getFilteredItems()

        emptyStateController.setVisible(filteredItems.isEmpty())
        indexBar.submitLabels(filteredItems.indexLabels())
    }

    private fun scrollToLabel(label: String) {
        val index = adapter.getFilteredItems().indexOfFirst { music ->
            music.title.trim().startsWith(label, ignoreCase = true)
        }

        if (index >= 0) {
            recyclerView.scrollToPosition(index)
        }
    }

    private fun List<Music>.indexLabels(): List<String> {
        return mapNotNull { music ->
            music.title
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
        }.distinct()
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

                    selectedMusic?.let { music ->
                        putExtra(ARG_MUSIC, music)
                    }
                }
            )
        }
    }
}
