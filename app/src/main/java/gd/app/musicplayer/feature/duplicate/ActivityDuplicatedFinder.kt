package gd.app.musicplayer.feature.duplicate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.ActivityDuplicatedFinderBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ActivityDuplicatedFinder : BaseActivity() {

    private val viewModel: ActivityDuplicateViewModel by viewModels()

    private lateinit var binding: ActivityDuplicatedFinderBinding
    private lateinit var duplicateBinder: ActivityDuplicateBinder
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: RecyclerView
    private lateinit var selectLayout: View
    private lateinit var selectAllView: AppCompatImageView
    private lateinit var selectTextView: TextView
    private lateinit var deleteView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDuplicatedFinderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindDisplayViews()
        setupToolbar()
        setupRecycler()
        setupEmptyState()
        setupInteractions()
        observeUi()

        if (hasAudioPermission()) {
            viewModel.startScanIfNeeded()
        } else {
            requestAudioPermission()
        }
    }

    private fun bindDisplayViews() {
        recyclerView = findViewById(R.id.recyclerview)
        selectLayout = findViewById(R.id.duplicated_finder_select_layout)
        selectAllView = findViewById(R.id.duplicated_finder_select_all)
        selectTextView = findViewById(R.id.duplicated_finder_select_text)
        deleteView = findViewById(R.id.duplicated_finder_delete)
    }

    override fun onAudioPermissionGranted() {
        viewModel.startScanIfNeeded()
    }

    override fun onDestroy() {
        binding.duplicatedFinderProgress.stopAnimationImmediately()
        super.onDestroy()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.music_find_duplicate
        )
        binding.toolbar.setNavigationOnClickListener {
            handleBackPressed()
        }
    }

    private fun setupRecycler() {
        duplicateBinder = ActivityDuplicateBinder(
            onGroupClick = viewModel::toggleGroupExpansion,
            onTrackClick = viewModel::toggleTrackSelection
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = duplicateBinder
    }

    private fun setupEmptyState() {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)
        ).apply {
            setActionButtonVisible(false)
            setExtraTextVisible(false)
            setEmptyMessage(getString(R.string.music_empty))
        }
    }

    private fun setupInteractions() {
        selectAllView.setOnClickListener {
            viewModel.toggleSelectAll()
        }
        deleteView.setOnClickListener {
            confirmDelete()
        }
        onBackPressedDispatcher.addCallback(this) {
            handleBackPressed()
        }
    }

    private fun observeUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::render)
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is DuplicateFinderEvent.DeleteCompleted -> {
                                ToastUtil.show(
                                    this@ActivityDuplicatedFinder,
                                    Toast.LENGTH_SHORT,
                                    getString(R.string.delete_success)
                                )
                            }

                            DuplicateFinderEvent.DeleteFailed -> {
                                ToastUtil.show(
                                    this@ActivityDuplicatedFinder,
                                    Toast.LENGTH_SHORT,
                                    getString(R.string.delete_failed)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun render(state: DuplicateFinderUiState) {
        renderProgressState(state)
        renderResultState(state)
    }

    private fun renderProgressState(state: DuplicateFinderUiState) {
        if (state.isScanning) {
            if (binding.duplicatedFinderFlipper.displayedChild != FLIPPER_PROGRESS) {
                binding.duplicatedFinderFlipper.displayedChild = FLIPPER_PROGRESS
            }
            binding.duplicatedFinderProgress.startAnimation()
        } else {
            binding.duplicatedFinderProgress.stopAnimationSmoothly()
            if (binding.duplicatedFinderFlipper.displayedChild != FLIPPER_RESULTS) {
                binding.duplicatedFinderFlipper.displayedChild = FLIPPER_RESULTS
            }
        }

        binding.duplicatedFinderPercent.text = buildPercentText(state)
        binding.duplicatedFinderText.text = state.currentTrackTitle
    }

    private fun renderResultState(state: DuplicateFinderUiState) {
        duplicateBinder.submit(state.groups, state.selectedIds)
        selectLayout.isVisible = state.hasGroups
        deleteView.isVisible = state.hasGroups
        deleteView.isEnabled = state.selectedIds.isNotEmpty() && !state.isDeleting
        selectAllView.isSelected = state.allDuplicatesSelected
        selectTextView.text = getString(
            R.string.duplicated_finder_select_all,
            state.selectedIds.size
        )

        if (!state.isScanning && !state.hasGroups) {
            emptyStateController.showEmpty()
        } else {
            emptyStateController.showList()
        }
    }

    private fun buildPercentText(state: DuplicateFinderUiState): String {
        if (state.totalCount <= 0) return "0%"
        val percent = ((state.scannedCount + if (state.isScanning) 1 else 0) * 100 / state.totalCount)
            .coerceIn(0, 100)
        return "$percent%"
    }

    private fun confirmDelete() {
        val selectedCount = viewModel.uiState.value.selectedIds.size
        if (selectedCount <= 0) return

        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_x_songs, selectedCount))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteSelected()
            }
            .show()
    }

    private fun handleBackPressed() {
        if (viewModel.uiState.value.isScanning) {
            AlertDialog.Builder(this)
                .setTitle(R.string.exit)
                .setMessage(R.string.scan_interrupt)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exit) { _, _ ->
                    viewModel.cancelScan()
                    finish()
                }
                .show()
        } else {
            finish()
        }
    }

    companion object {
        private const val FLIPPER_PROGRESS = 0
        private const val FLIPPER_RESULTS = 1

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, ActivityDuplicatedFinder::class.java))
        }
    }
}
