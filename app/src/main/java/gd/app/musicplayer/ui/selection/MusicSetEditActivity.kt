package gd.app.musicplayer.ui.selection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResultListener
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.smallestScreenWidthDp
import gd.app.musicplayer.core.common.extension.stableId
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.databinding.ActivityMusicSetEditBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.feature.library.ARG_MUSIC_SET
import gd.app.musicplayer.feature.library.folder.isHiddenFoldersEntry
import gd.app.musicplayer.feature.library.options.DeleteConfirmDialogFragment
import gd.app.musicplayer.feature.playlist.PlaylistSelectActivity
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MusicSetEditActivity : BaseActivity() {

    @Inject
    lateinit var playTracksUseCase: PlayTracksUseCase

    @Inject
    lateinit var enqueueTracksUseCase: EnqueueTracksUseCase

    private val viewModel: EditViewModel by viewModels()

    private lateinit var binding: ActivityMusicSetEditBinding
    private lateinit var adapter: MusicSetEditAdapter
    private lateinit var rootMusicSet: MusicSet
    private lateinit var sessionId: String

    private var emptyView: View? = null
    private var selectAllView: ImageView? = null

    private var currentItems: List<MusicSet> = emptyList()
    private val selectedKeys = linkedSetOf<String>()

    private var viewMode: Int = MUSIC_SET_VIEW_MODE_LIST
    private var useProvidedItems = false
    private var pendingRestoreScroll: ScrollState? = null

    private val recyclerView: RecyclerView
        get() = binding.layoutRecyclerview.recyclerview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootMusicSet = readMusicSetFromIntent() ?: run {
            finish()
            return
        }

        sessionId = resolveSessionId(savedInstanceState)

        binding = ActivityMusicSetEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        emptyView = inflateEmptyView()

        setupToolbar()
        setupBottomMenu()

        lifecycleScope.launch {
            viewMode = resolveViewMode(rootMusicSet)

            setupAdapter()
            restoreState(savedInstanceState)
            setupRecyclerView()
            observeItems()
            refreshUi()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val scrollState = captureScrollState()

        setupRecyclerView()
        restoreScrollState(pendingRestoreScroll ?: scrollState)
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
        if (isFinishing) {
            TemporaryMusicSetSelectionStore.remove(sessionId)
        }

        super.onDestroy()
    }

    private fun resolveSessionId(savedInstanceState: Bundle?): String {
        return savedInstanceState?.getString(STATE_SESSION_ID)
            ?: intent.getStringExtra(EXTRA_SESSION_ID)
            ?: UUID.randomUUID().toString()
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

    private fun setupAdapter() {
        adapter = MusicSetEditAdapter(
            viewMode = viewMode,
            accentColor = themeEngine.currentTheme().accentColor,
            onToggleSelection = ::toggleSelection
        )
    }

    private fun setupRecyclerView() = with(recyclerView) {
        clearItemDecorations()

        adapter = this@MusicSetEditActivity.adapter
        clipToPadding = false

        if (viewMode == MUSIC_SET_VIEW_MODE_GRID) {
            setupGridRecycler()
        } else {
            setupListRecycler()
        }
    }

    private fun RecyclerView.setupGridRecycler() {
        val spacing = dpToPx(
            if (smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP) {
                GRID_SPACING_TABLET_DP
            } else {
                GRID_SPACING_PHONE_DP
            }
        )

        layoutManager = GridLayoutManager(
            this@MusicSetEditActivity,
            resolveSpanCount()
        )

        setPadding(spacing, spacing, spacing, spacing)
        addItemDecoration(SpacingItemDecoration.all(spacing))
    }

    private fun RecyclerView.setupListRecycler() {
        layoutManager = LinearLayoutManager(this@MusicSetEditActivity)
        setPadding(0, 0, 0, 0)
    }

    private fun setupBottomMenu() {
        binding.musicEditLayout.bindBulkActionClicks { view ->
            when (view.id) {
                R.id.main_info_play -> handleAction(EditAction.Play)
                R.id.main_info_add -> handleAction(EditAction.AddToPlaylist)
                R.id.main_info_enqueue -> handleAction(EditAction.Enqueue)
                R.id.main_info_share -> handleAction(EditAction.Share)
                R.id.main_info_delete -> handleAction(EditAction.Delete)
            }
        }

        binding.musicEditLayout.updateBulkActionEnabled(false)
    }

    private fun observeItems() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                observeMusicSetItems().collect(::showItems)
            }
        }
    }

    private fun observeMusicSetItems(): Flow<List<MusicSet>> {
        return when (rootMusicSet) {
            is MusicSet.Playlists -> {
                viewModel.playlists.map { playlists ->
                    playlists.map { playlist -> playlist as MusicSet }
                }
            }

            else -> viewModel.observeMusicSets(rootMusicSet)
        }
    }

    private fun showItems(items: List<MusicSet>) {
        currentItems = items

        syncSelectionWithCurrentItems()
        submitRows()
        refreshUi()
        restorePendingStateIfNeeded()
    }

    private fun submitRows() {
        adapter.submitMusicSets(
            items = currentItems,
            selectedKeys = selectedKeys
        )
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        val temporaryState = TemporaryMusicSetSelectionStore.get(sessionId)

        if (temporaryState != null) {
            restoreTemporaryState(temporaryState)
            return
        }

        savedInstanceState
            ?.getStringArrayList(STATE_SELECTED_KEYS)
            ?.let(::restoreSelectedKeys)

        restoreScrollFromBundle(savedInstanceState)
        restorePreselectedItem()
    }

    private fun restoreTemporaryState(state: TemporaryMusicSetSelectionStore.State) {
        useProvidedItems = state.allItems.isNotEmpty()
        currentItems = state.allItems

        selectedKeys.clear()
        selectedKeys.addAll(state.selectedItems.map { it.stableId })

        pendingRestoreScroll = state.scrollState

        submitRows()
    }

    private fun restoreSelectedKeys(keys: List<String>) {
        selectedKeys.clear()
        selectedKeys.addAll(keys)
    }

    private fun restoreScrollFromBundle(savedInstanceState: Bundle?) {
        val position = savedInstanceState?.getInt(
            STATE_SCROLL_POSITION,
            RecyclerView.NO_POSITION
        ) ?: RecyclerView.NO_POSITION

        if (position == RecyclerView.NO_POSITION) return

        pendingRestoreScroll = ScrollState(
            position = position,
            offset = savedInstanceState?.getInt(STATE_SCROLL_OFFSET, 0) ?: 0
        )
    }

    private fun restorePreselectedItem() {
        readPreselectedMusicSetFromIntent()
            ?.takeUnless(MusicSet::isHiddenFoldersEntry)
            ?.let { item ->
                selectedKeys.add(item.stableId)
            }
    }

    private fun restorePendingStateIfNeeded() {
        if (currentItems.isEmpty()) return

        pendingRestoreScroll?.let { scrollState ->
            restoreScrollState(scrollState)
            pendingRestoreScroll = null
            return
        }

        scrollToPreselectedItem()
    }

    private fun scrollToPreselectedItem() {
        val preselected = readPreselectedMusicSetFromIntent() ?: return
        val preselectedKey = preselected.stableId

        val index = currentItems.indexOfFirst { item ->
            item.stableId == preselectedKey
        }

        if (index >= 0) {
            recyclerView.layoutManager?.scrollToPosition(index)
        }
    }

    private fun toggleSelection(item: MusicSet) {
        if (item.isHiddenFoldersEntry()) return

        val key = item.stableId

        if (!selectedKeys.add(key)) {
            selectedKeys.remove(key)
        }

        submitRows()
        refreshUi()
    }

    private fun toggleSelectAll() {
        val selectableKeys = selectableItems()
            .mapTo(linkedSetOf()) { it.stableId }

        if (selectableKeys.isEmpty()) return

        if (selectedKeys.containsAll(selectableKeys)) {
            selectedKeys.removeAll(selectableKeys)
        } else {
            selectedKeys.addAll(selectableKeys)
        }

        submitRows()
        refreshUi()
    }

    private fun selectedItems(): List<MusicSet> {
        return currentItems.filter { item ->
            item.stableId in selectedKeys
        }
    }

    private fun selectableItems(): List<MusicSet> {
        return currentItems.filterNot(MusicSet::isHiddenFoldersEntry)
    }

    private fun syncSelectionWithCurrentItems() {
        val validKeys = currentItems.mapTo(hashSetOf()) { it.stableId }
        selectedKeys.retainAll(validKeys)
    }

    private fun refreshUi() {
        val selectedCount = selectedKeys.size
        val selectableCount = selectableItems().size

        binding.toolbar.title = musicSelectionTitle(
            selectedCount = selectedCount,
            emptyTitleRes = R.string.batch_edit
        )

        selectAllView?.renderSelectAllState(
            SelectionUiState(
                selectedCount = selectedCount,
                selectableCount = selectableCount
            )
        )

        binding.musicEditLayout.updateBulkActionEnabled(selectedCount > 0)

        val isEmpty = currentItems.isEmpty()
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun handleAction(action: EditAction) {
        if (selectedKeys.isEmpty()) return

        lifecycleScope.launch {
            val tracks = resolveSelectedTracks()

            if (tracks.isEmpty()) {
                Toast.makeText(
                    this@MusicSetEditActivity,
                    R.string.music_empty,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            when (action) {
                EditAction.Play -> {
                    playTracksUseCase(tracks, 0)
                    clearSelection()
                }

                EditAction.AddToPlaylist -> {
                    PlaylistSelectActivity.start(this@MusicSetEditActivity, tracks)
                    clearSelection()
                }

                EditAction.Enqueue -> {
                    enqueueTracksUseCase(tracks)
                    Toast.makeText(
                        this@MusicSetEditActivity,
                        getString(R.string.enqueue_msg_count, tracks.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    clearSelection()
                }

                EditAction.Share -> {
                    MusicShareSupport.share(this@MusicSetEditActivity, tracks)
                    clearSelection()
                }

                EditAction.Delete -> {
                    confirmDeleteTracks(tracks)
                }
            }
        }
    }

    private suspend fun resolveSelectedTracks(): List<Music> {
        return selectedItems()
            .flatMap { item ->
                viewModel.observeTracks(item).first()
            }
            .distinctBy(Music::id)
    }

    private fun confirmDeleteTracks(tracks: List<Music>) {
        val resultKey = "music_set_edit_delete_confirm_result"
        supportFragmentManager.setFragmentResultListener(
            resultKey,
            this
        ) { _, bundle ->
            if (!bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_CONFIRMED, false)) return@setFragmentResultListener
            val deleteFromDevice = bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_EXTRA_CHECKED, true)
            deleteTracks(tracks, deleteFromDevice)
        }

        val dialog = if (tracks.size == 1) {
            DeleteConfirmDialogFragment.forTrackDelete(
                resultKey = resultKey,
                trackTitle = tracks.first().title
            )
        } else {
            DeleteConfirmDialogFragment.forTracksDelete(
                resultKey = resultKey,
                trackCount = tracks.size
            )
        }
        dialog.show(supportFragmentManager, DeleteConfirmDialogFragment::class.java.simpleName)
    }

    private fun deleteTracks(tracks: List<Music>, deleteFromDevice: Boolean) {
        lifecycleScope.launch {
            val deletedCount = if (deleteFromDevice) {
                viewModel.deleteTracks(tracks)
            } else {
                viewModel.deleteTracksFromLibrary(tracks)
                tracks.size
            }

            Toast.makeText(
                this@MusicSetEditActivity,
                if (deletedCount > 0) {
                    R.string.succeed
                } else {
                    R.string.feature_not_implemented
                },
                Toast.LENGTH_SHORT
            ).show()

            clearSelection()
        }
    }

    private fun clearSelection() {
        selectedKeys.clear()
        submitRows()
        refreshUi()
    }

    private fun clearItemDecorations() {
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
    }

    private fun inflateEmptyView(): View? {
        val stub = findViewById<ViewStub>(R.id.layout_list_empty) ?: return null

        return stub.inflate().also { view ->
            view.visibility = View.GONE
            view.findViewById<TextView>(R.id.empty_text)
                .setText(R.string.music_empty)
        }
    }

    private fun restoreScrollState(scrollState: ScrollState?) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val state = scrollState ?: return

        layoutManager.scrollToPositionWithOffset(
            state.position,
            state.offset
        )
    }

    private fun captureScrollState(): ScrollState? {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val position = layoutManager.findFirstVisibleItemPosition()

        if (position == RecyclerView.NO_POSITION) return null

        val child = layoutManager.findViewByPosition(position) ?: return null

        return ScrollState(
            position = position,
            offset = child.top - recyclerView.paddingTop
        )
    }

    private fun persistTemporaryState() {
        TemporaryMusicSetSelectionStore.put(
            sessionId = sessionId,
            state = TemporaryMusicSetSelectionStore.State(
                allItems = ArrayList(selectableItems()),
                selectedItems = ArrayList(selectedItems()),
                scrollState = captureScrollState()
            )
        )
    }

    private suspend fun resolveViewMode(musicSet: MusicSet): Int {
        return if (musicSet is MusicSet.Folders) {
            MUSIC_SET_VIEW_MODE_LIST
        } else {
            viewModel.getViewMode(musicSet)
        }
    }

    private fun resolveSpanCount(): Int {
        val tablet = isTablet()
        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return when {
            !tablet && !landscape -> 2
            !tablet && landscape -> 3
            tablet && !landscape -> 3
            else -> 4
        }
    }

    private fun readMusicSetFromIntent(): MusicSet? {
        return intent.parcelable(ARG_MUSIC_SET)
    }

    private fun readPreselectedMusicSetFromIntent(): MusicSet? {
        return intent.parcelable(EXTRA_PRESELECTED_SET)
    }

    companion object {
        private const val EXTRA_PRESELECTED_SET = "preselected_set"
        private const val EXTRA_SESSION_ID = "session_id"

        private const val STATE_SESSION_ID = "state_session_id"
        private const val STATE_SELECTED_KEYS = "state_selected_keys"
        private const val STATE_SCROLL_POSITION = "state_scroll_position"
        private const val STATE_SCROLL_OFFSET = "state_scroll_offset"

        private const val TABLET_SMALLEST_WIDTH_DP = 600
        private const val GRID_SPACING_PHONE_DP = 2f
        private const val GRID_SPACING_TABLET_DP = 16f

        fun start(
            context: Context,
            musicSet: MusicSet,
            visibleItems: List<MusicSet> = emptyList(),
            preselectedSet: MusicSet? = null
        ) {
            val sessionId = UUID.randomUUID().toString()
            val sanitizedItems = visibleItems.filterNot(MusicSet::isHiddenFoldersEntry)

            val selectedItems = buildList {
                preselectedSet
                    ?.takeUnless(MusicSet::isHiddenFoldersEntry)
                    ?.let(::add)
            }

            TemporaryMusicSetSelectionStore.put(
                sessionId = sessionId,
                state = TemporaryMusicSetSelectionStore.State(
                    allItems = ArrayList(sanitizedItems),
                    selectedItems = ArrayList(selectedItems),
                    scrollState = null
                )
            )

            context.startActivityCompat(
                Intent(context, MusicSetEditActivity::class.java).apply {
                    putExtra(ARG_MUSIC_SET, musicSet)
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    preselectedSet?.let {
                        putExtra(EXTRA_PRESELECTED_SET, it)
                    }
                }
            )
        }
    }
}

private enum class EditAction {
    Play,
    AddToPlaylist,
    Enqueue,
    Share,
    Delete
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

    fun get(sessionId: String): State? {
        return states[sessionId]
    }

    fun put(
        sessionId: String,
        state: State
    ) {
        states[sessionId] = state
    }

    fun remove(sessionId: String) {
        states.remove(sessionId)
    }
}
