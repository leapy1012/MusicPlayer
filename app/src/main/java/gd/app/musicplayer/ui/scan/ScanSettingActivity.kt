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
import androidx.recyclerview.widget.LinearLayoutManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

@AndroidEntryPoint
class ScanSettingActivity : BaseActivity() {

    private val viewModel: ScanSettingViewModel by viewModels()

    private lateinit var binding: ActivityScanSettingBinding
    private lateinit var adapter: ScanFolderAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!viewModel.navigateUp()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, backCallback)

        setupToolbar()
        setupRecyclerView()
        setupStartButton()
        observeUiState()
        observeEvents()

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

    private fun setupRecyclerView() {
        adapter = ScanFolderAdapter(
            applyTheme = { view -> themeEngine.apply(view) },
            onFolderClick = { item ->
                viewModel.openFolder(
                    itemPath = item.path,
                    scrollPosition = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0),
                    scrollOffset = layoutManager.findViewByPosition(
                        layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
                    )?.top ?: 0
                )
            },
            onSelectionChanged = { item, selected ->
                viewModel.setSelected(item.path, selected)
            }
        )

        layoutManager = LinearLayoutManager(this)

        binding.root.findViewById<RecyclerView>(R.id.recyclerview).apply {
            layoutManager = this@ScanSettingActivity.layoutManager
            adapter = this@ScanSettingActivity.adapter
            setHasFixedSize(true)

            (itemAnimator as? SimpleItemAnimator)
                ?.supportsChangeAnimations = false
        }
    }

    private fun setupStartButton() {
        binding.scanSettingPathStart.setOnClickListener {
            val selectedPaths = ArrayList(viewModel.uiState.value.selectedPaths)
            setResult(
                Activity.RESULT_OK,
                Intent().putStringArrayListExtra(EXTRA_SELECT_PATHS, selectedPaths)
            )
            finish()
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        ScanSettingEvent.NoSubfolders -> {
                            ToastUtil.show(this@ScanSettingActivity, R.string.no_subfolders)
                            viewModel.consumeEvent()
                        }

                        null -> Unit
                    }
                }
            }
        }
    }

    private fun render(state: ScanSettingUiState) {
        if (state.currentPath == ROOT_PATH) {
            binding.toolbar.setTitle(R.string.scan_specified_folder)
            binding.toolbar.subtitle = null
        } else {
            binding.toolbar.title = state.currentName
            binding.toolbar.subtitle = state.currentPath
        }

        adapter.submitFolders(
            items = state.items,
            selectedPaths = state.selectedPaths
        )

        if (state.restoreScroll) {
            binding.root.findViewById<RecyclerView>(R.id.recyclerview).post {
                layoutManager.scrollToPositionWithOffset(
                    state.scrollPosition,
                    state.scrollOffset
                )
            }
        }
    }

    companion object {
        const val EXTRA_SELECT_PATHS = "selectPaths"

        fun intent(context: Context, selectedPaths: Collection<String>): Intent {
            return Intent(context, ScanSettingActivity::class.java)
                .putStringArrayListExtra(EXTRA_SELECT_PATHS, ArrayList(selectedPaths))
        }
    }
}

private class ScanFolderAdapter(
    private val applyTheme: (View) -> Unit,
    private val onFolderClick: (ScanFolderItem) -> Unit,
    private val onSelectionChanged: (ScanFolderItem, Boolean) -> Unit
) : RecyclerView.Adapter<ScanFolderAdapter.ViewHolder>() {

    private var items: List<ScanFolderItem> = emptyList()
    private var selectedPaths: Set<String> = emptySet()

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemId(position: Int): Long {
        return items[position].path.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        applyTheme(holder.itemView)
        val item = items[position]
        holder.bind(item, selectedPaths.contains(normalizeSelectedPath(item.path)))
    }

    fun submitFolders(
        items: List<ScanFolderItem>,
        selectedPaths: Set<String>
    ) {
        this.items = items
        this.selectedPaths = selectedPaths
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ActivityScanSettingListItemBinding,
        private val onFolderClick: (ScanFolderItem) -> Unit,
        private val onSelectionChanged: (ScanFolderItem, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root), SelectBox.OnSelectChangedListener {

        private var boundItem: ScanFolderItem? = null

        init {
            binding.root.setOnClickListener {
                boundItem?.let(onFolderClick)
            }
            binding.scanSettingItemCheckbox.setOnSelectChangedListener(this)
        }

        fun bind(item: ScanFolderItem, selected: Boolean) {
            boundItem = item
            binding.scanSettingItemTitle.text = item.name
            binding.scanSettingItemExtra.text =
                binding.root.resources.getQuantityString(
                    R.plurals.plurals_track,
                    item.trackCount,
                    item.trackCount
                )

            binding.scanSettingItemImage.setImageResource(item.iconRes)

            binding.scanSettingItemCheckbox.isSelected = selected
        }

        override fun onSelectChanged(
            selectBox: SelectBox,
            fromUser: Boolean,
            isSelected: Boolean
        ) {
            if (!fromUser) return
            boundItem?.let { item -> onSelectionChanged(item, isSelected) }
        }
    }
}

private fun Intent.selectedScanPaths(): List<String> {
    return getStringArrayListExtra(ScanSettingActivity.EXTRA_SELECT_PATHS).orEmpty()
}
