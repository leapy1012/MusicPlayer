package gd.app.musicplayer.ui.selection

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicSetEditBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.library.ARG_MUSIC_SET
import gd.app.musicplayer.ui.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.library.folder.isHiddenFoldersEntry

import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MusicSetEditActivity : BaseActivity() {

    private val viewModel: EditViewModel by viewModels()

    private lateinit var binding: ActivityMusicSetEditBinding
    private lateinit var adapter: MusicSetEditAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var rootMusicSet: MusicSet
    private lateinit var sessionId: String
    private var emptyView: View? = null
    private var selectAllView: ImageView? = null
    private var currentItems: List<MusicSet> = emptyList()
    private var pendingRestoreScroll: ScrollState? = null
    private val selectedKeys = linkedSetOf<String>()
    private var viewMode: Int = MUSIC_SET_VIEW_MODE_LIST
    private var useProvidedItems = false
    @Inject
    lateinit var playTracksUseCase: PlayTracksUseCase

    @Inject lateinit var enqueueTracksUseCase: EnqueueTracksUseCase


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootMusicSet = readMusicSetFromIntent() ?: run {
            finish()
            return
        }

        sessionId = savedInstanceState?.getString(STATE_SESSION_ID)
            ?: intent.getStringExtra(EXTRA_SESSION_ID)
                    ?: UUID.randomUUID().toString()

        binding = ActivityMusicSetEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recyclerView = findViewById(R.id.recyclerview)
        emptyView = inflateEmptyView()

        setupToolbar()
        setupBottomMenu()

        lifecycleScope.launch {
            viewMode = resolveViewMode(rootMusicSet)

            adapter = MusicSetEditAdapter(
                viewMode = viewMode,
                accentColor = themeEngine.currentTheme().accentColor,
                onToggleSelection = ::toggleSelection
            )

            restoreState(savedInstanceState)
            setupRecyclerView()
            observeItems()
            refreshUi()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupRecyclerView()
        restoreScrollState(pendingRestoreScroll ?: captureScrollState())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistTemporaryState()
        outState.putString(STATE_SESSION_ID, sessionId)
        outState.putStringArrayList(STATE_SELECTED_KEYS, ArrayList(selectedKeys))
        captureScrollState()?.let { scrollState ->
            outState.putInt(STATE_SCROLL_POSITION, scrollState.position)
            outState.putInt(STATE_SCROLL_OFFSET, scrollState.offset)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        persistTemporaryState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            TemporaryMusicSetSelectionStore.remove(sessionId)
        }
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.musicEditLayout,
            toolbar = binding.toolbar
        )
        selectAllView = binding.toolbar.installSelectAllAction(layoutInflater) {
            toggleSelectAll()
        }
    }

    private fun setupRecyclerView() {
        clearItemDecorations()
        recyclerView.adapter = adapter
        recyclerView.clipToPadding = false

        if (viewMode == MUSIC_SET_VIEW_MODE_GRID) {
            val spacing = dpToPx(if (resources.configuration.smallestScreenWidthDp >= 600) 16f else 2f)
            recyclerView.layoutManager = GridLayoutManager(this, resolveSpanCount())
            recyclerView.setPadding(spacing, spacing, spacing, spacing)
            recyclerView.addItemDecoration(SpacingItemDecoration.all(spacing))
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.setPadding(0, 0, 0, 0)
        }
    }

    private fun setupBottomMenu() {
        binding.musicEditLayout.bindBulkActionClicks { child ->
            when (child.id) {
                R.id.main_info_play -> handleAction(ACTION_PLAY)
                R.id.main_info_add -> handleAction(ACTION_ADD_TO)
                R.id.main_info_enqueue -> handleAction(ACTION_ENQUEUE)
                R.id.main_info_share -> handleAction(ACTION_SHARE)
                R.id.main_info_delete -> handleAction(ACTION_DELETE)
            }
        }
        binding.musicEditLayout.updateBulkActionEnabled(false)
    }

    private fun observeItems() {
        if (useProvidedItems) return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                observeMusicSetItems().collect(::showItems)
            }
        }
    }

    private fun showItems(items: List<MusicSet>) {
        currentItems = items
        syncSelectionWithCurrentItems()
        adapter.submitMusicSets(currentItems, selectedItems())
        refreshUi()
        restorePendingStateIfNeeded()
    }

    private fun observeMusicSetItems(): Flow<List<MusicSet>> {
        return when (rootMusicSet) {
            is MusicSet.Playlists -> viewModel.playlists.map { items -> items.map { it as MusicSet } }
            else -> viewModel.observeMusicSets(rootMusicSet)
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        TemporaryMusicSetSelectionStore.get(sessionId)?.let { state ->
            useProvidedItems = state.allItems.isNotEmpty()
            currentItems = state.allItems
            selectedKeys.clear()
            selectedKeys.addAll(state.selectedItems.map(::musicSetKey))
            pendingRestoreScroll = state.scrollState
            adapter.submitMusicSets(currentItems, selectedItems())
            return
        }

        savedInstanceState?.getStringArrayList(STATE_SELECTED_KEYS)?.let { restored ->
            selectedKeys.clear()
            selectedKeys.addAll(restored)
        }

        val scrollPosition = savedInstanceState?.getInt(STATE_SCROLL_POSITION, RecyclerView.NO_POSITION)
            ?: RecyclerView.NO_POSITION
        if (scrollPosition != RecyclerView.NO_POSITION) {
            pendingRestoreScroll = ScrollState(
                position = scrollPosition,
                offset = savedInstanceState?.getInt(STATE_SCROLL_OFFSET, 0) ?: 0
            )
        }

        readPreselectedMusicSetFromIntent()?.let { preselected ->
            selectedKeys.add(musicSetKey(preselected))
        }
    }

    private fun restorePendingStateIfNeeded() {
        if (currentItems.isEmpty()) return

        pendingRestoreScroll?.let { scrollState ->
            restoreScrollState(scrollState)
            pendingRestoreScroll = null
            return
        }

        val preselected = readPreselectedMusicSetFromIntent() ?: return
        val index = currentItems.indexOfFirst { musicSetKey(it) == musicSetKey(preselected) }
        if (index >= 0) {
            recyclerView.layoutManager?.scrollToPosition(index)
        }
    }

    private fun toggleSelection(item: MusicSet) {
        if (item.isHiddenFoldersEntry()) return

        val key = musicSetKey(item)
        if (!selectedKeys.add(key)) {
            selectedKeys.remove(key)
        }
        adapter.updateSelection(selectedItems())
        refreshUi()
    }

    private fun toggleSelectAll() {
        val selectableItems = currentItems.filterNot(MusicSet::isHiddenFoldersEntry)
        val allKeys = selectableItems.mapTo(linkedSetOf(), ::musicSetKey)
        if (allKeys.isNotEmpty() && selectedKeys.containsAll(allKeys)) {
            selectedKeys.removeAll(allKeys)
        } else {
            selectedKeys.addAll(allKeys)
        }
        adapter.updateSelection(selectedItems())
        refreshUi()
    }

    private fun selectedItems(): List<MusicSet> =
        currentItems.filter { musicSetKey(it) in selectedKeys }

    private fun syncSelectionWithCurrentItems() {
        val validKeys = currentItems.mapTo(hashSetOf(), ::musicSetKey)
        selectedKeys.retainAll(validKeys)
    }

    private fun refreshUi() {
        val selectedCount = selectedKeys.size
        binding.toolbar.title = musicSelectionTitle(
            selectedCount = selectedCount,
            emptyTitleRes = R.string.batch_edit
        )

        val selectableItems = currentItems.filterNot(MusicSet::isHiddenFoldersEntry)
        selectAllView?.let { selectAll ->
            selectAll.renderSelectAllState(
                SelectionUiState(
                    selectedCount = selectedCount,
                    hasSelectableItems = selectableItems.isNotEmpty(),
                    allSelectableItemsSelected = selectableItems.isNotEmpty() &&
                        selectableItems.all { musicSetKey(it) in selectedKeys }
                )
            )
        }

        binding.musicEditLayout.updateBulkActionEnabled(hasSelection())

        val isEmpty = currentItems.isEmpty()
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun handleAction(action: String) {
        if (!hasSelection()) return

        lifecycleScope.launch {
            val tracks = resolveSelectedTracks()
            if (tracks.isEmpty()) {
                Toast.makeText(this@MusicSetEditActivity, R.string.music_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }

            when (action) {
                ACTION_ADD_TO -> ActivityPlaylistSelect.start(this@MusicSetEditActivity, tracks)
                ACTION_PLAY -> playTracksUseCase(this@MusicSetEditActivity, tracks, 0)

                ACTION_ENQUEUE -> {
                    enqueueTracksUseCase(this@MusicSetEditActivity, tracks)
                    Toast.makeText(
                        this@MusicSetEditActivity,
                        getString(R.string.enqueue_msg_count, tracks.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                ACTION_SHARE -> MusicShareSupport.share(this@MusicSetEditActivity, tracks)
                ACTION_DELETE -> confirmDeleteTracks(tracks)
            }

            if (action != ACTION_DELETE) {
                clearSelection()
            }
        }
    }

    private suspend fun resolveSelectedTracks(): List<Music> {
        return selectedItems()
            .flatMap { item -> viewModel.observeTracks(item).first() }
            .distinctBy(Music::id)
    }

    private fun confirmDeleteTracks(tracks: List<Music>) {
        val message = if (tracks.size == 1) {
            tracks.first().title
        } else {
            getString(R.string.remove_songs_from_list_msg, tracks.size.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val deletedCount = viewModel.deleteTracks(tracks)
                    Toast.makeText(
                        this@MusicSetEditActivity,
                        if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented,
                        Toast.LENGTH_SHORT
                    ).show()
                    clearSelection()
                }
            }
            .show()
    }

    private fun clearSelection() {
        selectedKeys.clear()
        adapter.updateSelection(emptyList())
        refreshUi()
    }

    private fun hasSelection(): Boolean = selectedKeys.isNotEmpty()

    private fun clearItemDecorations() {
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
    }

    private fun inflateEmptyView(): View? {
        val stub = findViewById<ViewStub>(R.id.layout_list_empty) ?: return null
        return stub.inflate().also { view ->
            view.visibility = View.GONE
            view.findViewById<TextView>(R.id.empty_text).setText(R.string.music_empty)
        }
    }

    private fun restoreScrollState(scrollState: ScrollState?) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val state = scrollState ?: return
        layoutManager.scrollToPositionWithOffset(state.position, state.offset)
    }

    private fun captureScrollState(): ScrollState? {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return null
        val child = layoutManager.findViewByPosition(position) ?: return null
        return ScrollState(position, child.top - recyclerView.paddingTop)
    }

    private fun persistTemporaryState() {
        TemporaryMusicSetSelectionStore.put(
            sessionId,
            TemporaryMusicSetSelectionStore.State(
                allItems = ArrayList(currentItems.filterNot(MusicSet::isHiddenFoldersEntry)),
                selectedItems = ArrayList(selectedItems()),
                scrollState = captureScrollState()
            )
        )
    }

    private suspend fun resolveViewMode(musicSet: MusicSet): Int =
        if (musicSet is MusicSet.Folders) {
            MUSIC_SET_VIEW_MODE_LIST
        } else {
            viewModel.getViewMode(musicSet)
        }

    private fun resolveSpanCount(): Int {
        val isTablet = isTablet()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return when {
            !isTablet && !landscape -> 2
            !isTablet && landscape -> 3
            isTablet && !landscape -> 3
            else -> 4
        }
    }

    private fun readMusicSetFromIntent(): MusicSet? = intent.parcelable(ARG_MUSIC_SET)

    private fun readPreselectedMusicSetFromIntent(): MusicSet? = intent.parcelable(EXTRA_PRESELECTED_SET)

    companion object {
        private const val EXTRA_PRESELECTED_SET = "preselected_set"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val STATE_SESSION_ID = "state_session_id"
        private const val STATE_SELECTED_KEYS = "state_selected_keys"
        private const val STATE_SCROLL_POSITION = "state_scroll_position"
        private const val STATE_SCROLL_OFFSET = "state_scroll_offset"
        private const val ACTION_PLAY = "play"
        private const val ACTION_ADD_TO = "add_to"
        private const val ACTION_ENQUEUE = "enqueue"
        private const val ACTION_SHARE = "share"
        private const val ACTION_DELETE = "delete"
        fun start(
            context: Context,
            musicSet: MusicSet,
            visibleItems: List<MusicSet> = emptyList(),
            preselectedSet: MusicSet? = null
        ) {
            val sessionId = UUID.randomUUID().toString()
            val sanitizedItems = visibleItems.filterNot(MusicSet::isHiddenFoldersEntry)
            val selectedItems = buildList {
                preselectedSet?.takeUnless(MusicSet::isHiddenFoldersEntry)?.let(::add)
            }
            TemporaryMusicSetSelectionStore.put(
                sessionId,
                TemporaryMusicSetSelectionStore.State(
                    allItems = ArrayList(sanitizedItems),
                    selectedItems = ArrayList(selectedItems),
                    scrollState = null
                )
            )
            context.startActivityCompat(Intent(context, MusicSetEditActivity::class.java).apply {
                putExtra(ARG_MUSIC_SET, musicSet)
                putExtra(EXTRA_SESSION_ID, sessionId)
                preselectedSet?.let { putExtra(EXTRA_PRESELECTED_SET, it) }
            })
        }
    }

}

private data class ScrollState(
    val position: Int,
    val offset: Int
)

private object TemporaryMusicSetSelectionStore {
    data class State(
        val allItems: ArrayList<MusicSet>,
        val selectedItems: ArrayList<MusicSet>,
        val scrollState: ScrollState?
    )

    private val states = mutableMapOf<String, State>()

    fun get(sessionId: String): State? = states[sessionId]

    fun put(sessionId: String, state: State) {
        states[sessionId] = state
    }

    fun remove(sessionId: String) {
        states.remove(sessionId)
    }
}
