package gd.app.musicplayer.ui.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.view.SelectBox
import gd.app.musicplayer.databinding.ActivityScanSettingBinding
import gd.app.musicplayer.databinding.ActivityScanSettingListItemBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanSettingActivity : BaseActivity() {

    private val viewModel: ScanSettingViewModel by viewModels()

    private lateinit var binding: ActivityScanSettingBinding

    private val folderAdapter by lazy {
        ScanFolderAdapter(
            applyTheme = themeEngine::apply,
            onFolderClick = ::openFolder,
            onSelectionChanged = ::onFolderSelectionChanged
        )
    }

    private val folderLayoutManager by lazy {
        LinearLayoutManager(this)
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (viewModel.navigateUp()) return

            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScanSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, backCallback)

        setupToolbar()
        setupFolderList()
        setupStartButton()
        collectUiState()
        collectEvents()

        viewModel.load(intent.selectedScanPaths())
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.scan_specified_folder
        )
    }

    private fun setupFolderList() = with(binding.recyclerView.recyclerview) {
        layoutManager = folderLayoutManager
        adapter = folderAdapter
        setHasFixedSize(true)

        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun setupStartButton() {
        binding.scanSettingPathStart.setOnClickListener {
            finishWithSelectedPaths()
        }
    }

    private fun collectUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun render(state: ScanSettingUiState) {
        renderToolbar(state)
        renderFolders(state)
        restoreScrollIfNeeded(state)
    }

    private fun renderToolbar(state: ScanSettingUiState) = with(binding.toolbar) {
        if (state.currentPath == ROOT_PATH) {
            setTitle(R.string.scan_specified_folder)
            subtitle = null
        } else {
            title = state.currentName
            subtitle = state.currentPath
        }
    }

    private fun renderFolders(state: ScanSettingUiState) {
        folderAdapter.submitFolders(
            folders = state.items,
            selectedPaths = state.selectedPaths
        )
    }

    private fun restoreScrollIfNeeded(state: ScanSettingUiState) {
        if (!state.restoreScroll) return

        binding.recyclerView.recyclerview.post {
            folderLayoutManager.scrollToPositionWithOffset(
                state.scrollPosition,
                state.scrollOffset
            )
        }
    }

    private fun handleEvent(event: ScanSettingEvent?) {
        when (event) {
            ScanSettingEvent.NoSubfolders -> {
                ToastUtil.show(this, R.string.no_subfolders)
                viewModel.consumeEvent()
            }

            null -> Unit
        }
    }

    private fun openFolder(item: ScanFolderItem) {
        val scroll = currentScrollPosition()

        viewModel.openFolder(
            itemPath = item.path,
            scrollPosition = scroll.position,
            scrollOffset = scroll.offset
        )
    }

    private fun onFolderSelectionChanged(
        item: ScanFolderItem,
        selected: Boolean
    ) {
        viewModel.setSelected(item.path, selected)
    }

    private fun currentScrollPosition(): FolderListScrollPosition {
        val position = folderLayoutManager
            .findFirstVisibleItemPosition()
            .coerceAtLeast(0)

        val offset = folderLayoutManager
            .findViewByPosition(position)
            ?.top
            ?: 0

        return FolderListScrollPosition(
            position = position,
            offset = offset
        )
    }

    private fun finishWithSelectedPaths() {
        val selectedPaths = ArrayList(viewModel.uiState.value.selectedPaths)

        setResult(
            RESULT_OK,
            Intent().putStringArrayListExtra(EXTRA_SELECT_PATHS, selectedPaths)
        )

        finish()
    }

    private data class FolderListScrollPosition(
        val position: Int,
        val offset: Int
    )

    companion object {
        const val EXTRA_SELECT_PATHS = "selectPaths"

        fun intent(
            context: Context,
            selectedPaths: Collection<String>
        ): Intent {
            return Intent(context, ScanSettingActivity::class.java)
                .putStringArrayListExtra(
                    EXTRA_SELECT_PATHS,
                    ArrayList(selectedPaths)
                )
        }
    }
}

private class ScanFolderAdapter(
    private val applyTheme: (View) -> Unit,
    private val onFolderClick: (ScanFolderItem) -> Unit,
    private val onSelectionChanged: (ScanFolderItem, Boolean) -> Unit
) : ListAdapter<ScanFolderRow, ScanFolderAdapter.ViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).stableId
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ActivityScanSettingListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        applyTheme(binding.root)

        return ViewHolder(
            binding = binding,
            onFolderClick = onFolderClick,
            onSelectionChanged = onSelectionChanged
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    fun submitFolders(
        folders: List<ScanFolderItem>,
        selectedPaths: Set<String>
    ) {
        val rows = folders.map { folder ->
            ScanFolderRow(
                item = folder,
                selected = normalizeSelectedPath(folder.path) in selectedPaths
            )
        }

        submitList(rows)
    }

    class ViewHolder(
        private val binding: ActivityScanSettingListItemBinding,
        private val onFolderClick: (ScanFolderItem) -> Unit,
        private val onSelectionChanged: (ScanFolderItem, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root),
        SelectBox.OnSelectChangedListener {

        private var boundRow: ScanFolderRow? = null

        init {
            binding.root.setOnClickListener {
                boundRow?.item?.let(onFolderClick)
            }

            binding.scanSettingItemCheckbox.setOnSelectChangedListener(this)
        }

        fun bind(row: ScanFolderRow) = with(binding) {
            boundRow = row

            scanSettingItemTitle.text = row.item.name

            scanSettingItemExtra.text = root.resources.getQuantityString(
                R.plurals.plurals_track,
                row.item.trackCount,
                row.item.trackCount
            )

            scanSettingItemImage.setImageResource(row.item.iconRes)
            scanSettingItemCheckbox.isSelected = row.selected
        }

        override fun onSelectChanged(
            selectBox: SelectBox,
            fromUser: Boolean,
            isSelected: Boolean
        ) {
            if (!fromUser) return

            boundRow?.item?.let { item ->
                onSelectionChanged(item, isSelected)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScanFolderRow>() {
        override fun areItemsTheSame(
            oldItem: ScanFolderRow,
            newItem: ScanFolderRow
        ): Boolean {
            return oldItem.item.path == newItem.item.path
        }

        override fun areContentsTheSame(
            oldItem: ScanFolderRow,
            newItem: ScanFolderRow
        ): Boolean {
            return oldItem == newItem
        }
    }
}

private data class ScanFolderRow(
    val item: ScanFolderItem,
    val selected: Boolean
) {
    val stableId: Long
        get() = item.path.hashCode().toLong()
}

private fun Intent.selectedScanPaths(): List<String> {
    return getStringArrayListExtra(ScanSettingActivity.EXTRA_SELECT_PATHS).orEmpty()
}