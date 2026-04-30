package gd.app.musicplayer.ui.feature.selection

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
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityMusicEditBinding
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.EditBottomMenuController
import gd.app.musicplayer.core.ui.view.RecyclerIndexBar
import gd.app.musicplayer.core.ui.view.MusicRecyclerView
import gd.app.musicplayer.ui.feature.library.ARG_MUSIC
import gd.app.musicplayer.ui.feature.library.ARG_MUSIC_SET
import gd.app.musicplayer.core.extension.parcelable
import gd.app.musicplayer.core.extension.startActivityCompat
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicEditActivity : BaseActivity(),
    MusicEditAdapter.SelectionCountChangedListener {

    private val viewModel: SelectionViewModel by viewModels()

    private lateinit var binding: ActivityMusicEditBinding
    private lateinit var adapter: MusicEditAdapter
    private lateinit var musicRecyclerView: MusicRecyclerView
    private lateinit var selectAllImage: ImageView
    private lateinit var indexBar: RecyclerIndexBar
    private lateinit var emptyStateController: RecyclerEmptyStateController

    private lateinit var musicSet: MusicSet

    private var selectedMusic: Music? = null
    private var initialTopOffset: Int = 0
    private var shouldScrollToInitialMusic = true
    private var shouldApplyInitialSelection = true

    private val searchTextWatcher = object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            handleSearchTextChanged(editable?.toString().orEmpty())
        }

        override fun beforeTextChanged(
            text: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) = Unit

        override fun onTextChanged(
            text: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) = Unit
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMusicEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!readIntentData()) {
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupBottomMenu()
        observeTracks()
    }

    private fun readIntentData(): Boolean {
        musicSet = intent.parcelable(ARG_MUSIC_SET) ?: return false
        selectedMusic = intent.parcelable(ARG_MUSIC)
        initialTopOffset = intent.getIntExtra(ARG_TOP_OFFSET, intent.getIntExtra(ARG_OFFSET, 0))
        return true
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.musicEditLayout,
            toolbar = binding.toolbar
        )

        selectAllImage = binding.toolbar.installSelectAllAction(layoutInflater) { view ->
            toggleSelectAll(view)
        }
    }

    private fun toggleSelectAll(view: View) {
        if (adapter.itemCount == 0) return

        view.isSelected = !view.isSelected
        adapter.setAllSelected(view.isSelected)

        updateSelectionTitle(adapter.getSelectedItems().size)
    }

    private fun setupRecyclerView() {
        musicRecyclerView = binding.root.findViewById<MusicRecyclerView>(R.id.recyclerview).apply {
            layoutManager = LinearLayoutManager(
                this@MusicEditActivity,
                LinearLayoutManager.VERTICAL,
                false
            )
        }

        adapter = buildAdapter()

        musicRecyclerView.adapter = adapter

        indexBar = binding.root.findViewById<RecyclerIndexBar>(R.id.recyclerview_index).apply {
            onLabelSelected = { label -> scrollToLabel(label) }
        }
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = musicRecyclerView,
            emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)
        ).apply {
            setEmptyMessage(getString(R.string.music_empty))
        }
    }

    private fun buildAdapter(): MusicEditAdapter =
        MusicEditAdapter(
            recyclerView = musicRecyclerView,
            musicSet = musicSet,
            dragEnabled = musicSet.id > 0,
            onOrderChanged = viewModel::updateTrackOrder
        ).apply {
            setSelectionCountListener(this@MusicEditActivity)
        }

    private fun setupSearch() {
        binding.searchEditClear.setOnClickListener {
            binding.searchEditText.setText("")
        }

        binding.searchEditText.addTextChangedListener(searchTextWatcher)
    }

    private fun setupBottomMenu() {
        EditBottomMenuController(
            activity = this,
            musicSet = musicSet,
            menuContainer = binding.musicEditLayout
        ).bindMenu()
    }

    private fun observeTracks() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeTracks(musicSet).collect { musicList ->
                    renderMusicList(musicList)
                }
            }
        }
    }

    private fun renderMusicList(musicList: List<Music>) {
        adapter.submitList(musicList)
        applyInitialSelectionIfNeeded()

        updateSelectionTitle(adapter.getSelectedItems().size)
        scrollToInitialMusicIfNeeded(musicList)
        updateFilteredListChrome()
    }

    private fun applyInitialSelectionIfNeeded() {
        if (!shouldApplyInitialSelection) return
        shouldApplyInitialSelection = false
        selectedMusic?.let(adapter::selectItem)
    }

    private fun scrollToInitialMusicIfNeeded(musicList: List<Music>) {
        if (!shouldScrollToInitialMusic) return

        shouldScrollToInitialMusic = false

        val music = selectedMusic ?: return
        val index = musicList.indexOfFirst { it.id == music.id && it.data == music.data }

        if (index < 0) return

        val layoutManager = musicRecyclerView.layoutManager as? LinearLayoutManager ?: return

        layoutManager.scrollToPositionWithOffset(index, initialTopOffset)

        musicRecyclerView.post {
            layoutManager.scrollToPosition(index)
        }
    }

    override fun onSelectionCountChanged(count: Int) {
        updateSelectionTitle(count)
    }

    private fun updateSelectionTitle(selectedCount: Int) {
        selectAllImage.renderSelectAllState(
            SelectionUiState(
                selectedCount = selectedCount,
                hasSelectableItems = adapter.itemCount > 0,
                allSelectableItemsSelected = adapter.areAllFilteredItemsSelected()
            )
        )

        binding.toolbar.title = musicSelectionTitle(selectedCount)
    }

    private fun handleSearchTextChanged(text: String) {
        val keyword = text.trim().lowercase()

        adapter.setSearchKeyword(keyword)

        binding.searchEditClear.visibility =
            if (keyword.isEmpty()) View.GONE else View.VISIBLE

        updateSelectionTitle(adapter.getSelectedItems().size)
        updateFilteredListChrome()
    }

    private fun updateFilteredListChrome() {
        val filteredItems = adapter.getFilteredItems()
        emptyStateController.setVisible(filteredItems.isEmpty())
        indexBar.submitLabels(buildIndexLabels(filteredItems))
    }

    private fun buildIndexLabels(items: List<Music>): List<String> {
        return items.mapNotNull { music ->
            music.title.trim().firstOrNull()?.uppercaseChar()?.toString()
        }.distinct()
    }

    private fun scrollToLabel(label: String) {
        val index = adapter.getFilteredItems().indexOfFirst { music ->
            music.title.trim().startsWith(label, ignoreCase = true)
        }
        if (index >= 0) {
            musicRecyclerView.scrollToPosition(index)
        }
    }

    fun getSelectedItems(): List<Music> {
        return adapter.getSelectedItems()
    }

    override fun onDestroy() {
        binding.searchEditText.removeTextChangedListener(searchTextWatcher)
        super.onDestroy()
    }

    companion object {
        private const val ARG_OFFSET = "offset"
        private const val ARG_TOP_OFFSET = "topOffset"

        fun start(
            context: Context,
            musicSet: MusicSet,
            selectedMusic: Music? = null,
            offset: Int = 0
        ) {
            val intent = Intent(context, MusicEditActivity::class.java).apply {
                putExtra(ARG_MUSIC_SET, musicSet)
                selectedMusic?.let { putExtra(ARG_MUSIC, it) }
                putExtra(ARG_TOP_OFFSET, offset)
            }

            context.startActivityCompat(intent)
        }
    }
}
