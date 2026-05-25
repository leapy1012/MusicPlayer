package gd.app.musicplayer.ui.duplicate

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.MessageDialog
import gd.app.musicplayer.databinding.ActivityDuplicatedFinderBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.feature.library.options.DeleteConfirmDialogFragment
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DuplicateFinderActivity : BaseActivity() {

    @Inject
    lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

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
        setupDialogResults()
        observeUi()

        if (hasAudioPermission()) {
            viewModel.startScanIfNeeded()
        } else {
            requestAudioPermission()
        }
    }

    private fun bindDisplayViews() {
        recyclerView = binding.root.findViewById(R.id.recyclerview)
        selectLayout = binding.root.findViewById(R.id.duplicated_finder_select_layout)
        selectAllView = binding.root.findViewById(R.id.duplicated_finder_select_all)
        selectTextView = binding.root.findViewById(R.id.duplicated_finder_select_text)
        deleteView = binding.root.findViewById(R.id.duplicated_finder_delete)
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
            onTrackClick = viewModel::toggleTrackSelection,
            applyTheme = ::applyThemeTo
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

    private fun setupDialogResults() {
        supportFragmentManager.setFragmentResultListener(DELETE_SELECTED_RESULT_KEY, this) { _, bundle ->
            if (bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_CONFIRMED)) {
                viewModel.deleteSelected(
                    deleteSourceFile = bundle.getBoolean(
                        DeleteConfirmDialogFragment.RESULT_EXTRA_CHECKED,
                        true
                    )
                )
            }
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
                                    this@DuplicateFinderActivity,
                                    Toast.LENGTH_SHORT,
                                    getString(R.string.delete_success)
                                )
                            }

                            DuplicateFinderEvent.DeleteFailed -> {
                                ToastUtil.show(
                                    this@DuplicateFinderActivity,
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
        val state = viewModel.uiState.value
        val selectedTracks = state.groups
            .asSequence()
            .flatMap { it.tracks.asSequence() }
            .filter { it.id in state.selectedIds }
            .distinctBy(Music::id)
            .toList()
        val selectedCount = selectedTracks.size
        if (selectedCount <= 0) return

        DeleteConfirmDialogFragment.forTracksDelete(
            resultKey = DELETE_SELECTED_RESULT_KEY,
            trackCount = selectedCount,
            tracks = selectedTracks
        ).show(supportFragmentManager, DeleteConfirmDialogFragment::class.java.simpleName)
    }

    private fun handleBackPressed() {
        if (viewModel.uiState.value.isScanning) {
            val config = materialDialogConfigFactory
                .createMaterialMessageDialogConfig(this)
                .apply {
                    titleText = getString(R.string.exit)
                    messageText = getString(R.string.scan_interrupt)
                    negativeButtonText = getString(R.string.cancel)
                    positiveButtonText = getString(R.string.exit)
                    positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                        dialog.dismiss()
                        viewModel.cancelScan()
                        finish()
                    }
                }
            MessageDialog.show(this, config)
        } else {
            finish()
        }
    }

    companion object {
        private const val FLIPPER_PROGRESS = 0
        private const val FLIPPER_RESULTS = 1
        private const val DELETE_SELECTED_RESULT_KEY = "duplicate_delete_selected"

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, DuplicateFinderActivity::class.java))
        }
    }
}
