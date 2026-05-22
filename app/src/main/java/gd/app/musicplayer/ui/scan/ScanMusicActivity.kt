package gd.app.musicplayer.ui.scan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.readLongOrNull
import gd.app.musicplayer.core.common.extension.setTextIfDifferent
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityScanMusicBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.feature.library.deleted.DeletedMusicActivity
import gd.app.musicplayer.feature.library.hidden.HiddenFoldersActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanMusicActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    private val viewModel: ScanViewModel by viewModels()

    private lateinit var binding: ActivityScanMusicBinding

    private var openSettingsAfterPermission = false

    private val scanSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val selectedPaths = result.data
                ?.getStringArrayListExtra(ScanSettingActivity.EXTRA_SELECT_PATHS)
                .orEmpty()

            startScanWithPaths(selectedPaths)
        }

    private val beforeBinding
        get() = requireNotNull(binding.layoutBeforeScanning) {
            "layout_before_scanning include is missing from activity_scan_music.xml"
        }

    private val scanningBinding
        get() = requireNotNull(binding.layoutScanning) {
            "layout_scanning include is missing from activity_scan_music.xml"
        }

    private val resultBinding
        get() = requireNotNull(binding.layoutAfterScanning) {
            "layout_after_scanning include is missing from activity_scan_music.xml"
        }

    private val backCallback = object : OnBackPressedCallback(enabled = true) {
        override fun handleOnBackPressed() {
            if (viewModel.uiState.value.phase == ScanPhase.Scanning) {
                viewModel.cancelScan()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, backCallback)

        setupSystemBars()
        setupToolbar()
        setupViews()
        observeUiState()
    }

    private fun setupSystemBars() {
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.background)
    }

    private fun setupToolbar() {
        binding.toolbar.navigateBack(this)
        binding.toolbar.setOnMenuItemClickListener(this)
    }

    private fun setupViews() {
        binding.scanStartStop.setOnClickListener {
            onPrimaryActionClick()
        }

        beforeBinding.scanCheckbox.setOnClickListener {
            toggleDurationFilter()
        }

        beforeBinding.scanCheckbox2.setOnClickListener {
            toggleSizeFilter()
        }

        beforeBinding.scanCheckbox3.setOnClickListener {
            beforeBinding.scanCheckbox3.isSelected = !beforeBinding.scanCheckbox3.isSelected
        }

        resultBinding.scanHideClickParent.setOnClickListener {
            openHiddenFolders()
        }

        resultBinding.scanDeleteParent.setOnClickListener {
            DeletedMusicActivity.start(this)
        }

        scanningBinding.scanProgress.apply {
            isEnabled = false
            setMax(PROGRESS_MAX)
            setProgress(0)
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ScanUiState) {
        renderOptions(state.options)
        renderLibraryInfo(state.libraryInfo)
        renderPhase(state)
    }

    private fun renderOptions(options: ScanOptions) = with(beforeBinding) {
        scanCheckbox.isSelected = options.excludeBySeconds
        scanCheckbox2.isSelected = options.excludeBySize
        scanCheckbox3.isSelected = options.excludeRingtone

        excludeDurationEditText.setTextIfDifferent(options.excludeSeconds.toString())
        excludeSizeEditText.setTextIfDifferent(options.excludeSizeKb.toString())
    }

    private fun renderPhase(state: ScanUiState) {
        when (state.phase) {
            ScanPhase.Idle -> renderIdle()
            ScanPhase.Scanning -> renderScanning(state)
            ScanPhase.Result -> renderResult(state.result)
        }
    }

    private fun renderIdle() {
        binding.scanViewFlipper.displayedChild = CHILD_BEFORE_SCANNING
        binding.scanStartStop.setText(R.string.scan_start)
        binding.musicScanProgress?.stopAnimationImmediately()

        scanningBinding.scanProgress.isVisible = false
        scanningBinding.scanPath.text = null

        setSettingsMenuVisible(true)
    }

    @SuppressLint("SetTextI18n")
    private fun renderScanning(state: ScanUiState) {
        val progress = state.progressPercent.coerceIn(0, PROGRESS_MAX)

        binding.scanViewFlipper.displayedChild = CHILD_SCANNING
        binding.scanStartStop.setText(R.string.scan_stop)

        scanningBinding.scanPath.text = when (state.step) {
            ScanStep.FindingFiles -> state.currentPath
            ScanStep.ParsingFiles -> getString(R.string.parse_file) + progress + "%"
            ScanStep.WritingDatabase -> getString(R.string.write_to_database)
        }
        scanningBinding.scanProgress.apply {
            setProgress(progress)
            isVisible = state.step == ScanStep.ParsingFiles
        }

        binding.musicScanProgress?.startAnimation()
        setSettingsMenuVisible(false)
    }

    private fun renderResult(result: ScanResultSummary?) {
        if (result == null) {
            renderIdle()
            return
        }

        binding.scanViewFlipper.displayedChild = CHILD_AFTER_SCANNING
        binding.scanStartStop.setText(R.string.scan_end)
        binding.musicScanProgress?.stopAnimationSmoothly()

        scanningBinding.scanProgress.isVisible = false

        resultBinding.scanReportSongs.text = getString(
            R.string.scan_result,
            formatTrackCount(result.importedCount)
        )
        resultBinding.scanReportAdded.text = getString(
            R.string.scan_result_1,
            formatTrackCount(result.addedCount)
        )
        resultBinding.scanReportFiltered.text = getString(
            R.string.scan_result_2,
            formatTrackCount(result.filteredOutCount)
        )

        resultBinding.scanHideParent.isVisible = result.hiddenCount > 0

        val hasDeletedTracks = result.deletedCount > 0
        resultBinding.scanDeleteParent.isVisible = hasDeletedTracks
        resultBinding.scanDeleteDetails.text = if (hasDeletedTracks) {
            getString(R.string.scan_result_3, formatTrackCount(result.deletedCount))
        } else {
            null
        }

        setSettingsMenuVisible(true)
    }

    private fun renderLibraryInfo(info: ScanLibraryInfo) {
        binding.scanLibraryInfo.text = getString(R.string.songs) +
            ": ${info.songs}  " +
            getString(R.string.albums) +
            ": ${info.albums}  " +
            getString(R.string.artists) +
            ": ${info.artists}"
    }

    private fun onPrimaryActionClick() {
        when (viewModel.uiState.value.phase) {
            ScanPhase.Idle -> startScanIfReady()
            ScanPhase.Scanning -> viewModel.cancelScan()
            ScanPhase.Result -> onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun startScanIfReady() {
        if (!hasAudioPermission()) {
            requestAudioPermission()
            return
        }

        startScanWithPaths(emptyList())
    }

    private fun startScanWithPaths(selectedPaths: List<String>) {
        val options = readScanOptionsOrNull() ?: return
        viewModel.startScan(
            options.copy(selectedScanPaths = selectedPaths)
        )
    }

    private fun openScanSettings() {
        if (!hasAudioPermission()) {
            openSettingsAfterPermission = true
            requestAudioPermission()
            return
        }

        val options = readScanOptionsOrNull() ?: return
        scanSettingsLauncher.launch(
            ScanSettingActivity.intent(
                context = this,
                selectedPaths = options.selectedScanPaths
            )
        )
    }

    private fun readScanOptionsOrNull(): ScanOptions? = with(beforeBinding) {
        val excludeBySeconds = scanCheckbox.isSelected
        val excludeBySize = scanCheckbox2.isSelected

        val excludeSeconds = excludeDurationEditText.readLongOrNull(
            required = excludeBySeconds,
            defaultValue = DEFAULT_EXCLUDE_SECONDS,
            minValue = MIN_EXCLUDE_SECONDS,
            maxValue = MAX_EXCLUDE_SECONDS
        ) ?: return null

        val excludeSizeKb = excludeSizeEditText.readLongOrNull(
            required = excludeBySize,
            defaultValue = DEFAULT_EXCLUDE_SIZE_KB,
            minValue = MIN_EXCLUDE_SIZE_KB,
            maxValue = MAX_EXCLUDE_SIZE_KB
        ) ?: return null

        ScanOptions(
            excludeBySeconds = excludeBySeconds,
            excludeBySize = excludeBySize,
            excludeRingtone = scanCheckbox3.isSelected,
            excludeSeconds = excludeSeconds,
            excludeSizeKb = excludeSizeKb,
            selectedScanPaths = viewModel.uiState.value.options.selectedScanPaths
        )
    }

    private fun toggleDurationFilter() = with(beforeBinding) {
        scanCheckbox.isSelected = !scanCheckbox.isSelected
    }

    private fun toggleSizeFilter() = with(beforeBinding) {
        scanCheckbox2.isSelected = !scanCheckbox2.isSelected
    }

    private fun setSettingsMenuVisible(visible: Boolean) {
        binding.toolbar.menu.findItem(R.id.menu_setting)?.isVisible = visible
    }

    private fun formatTrackCount(value: Int): String {
        return resources.getQuantityString(R.plurals.plurals_track, value, value)
    }

    private fun openHiddenFolders() {
        HiddenFoldersActivity.start(this)
    }

    override fun onAudioPermissionGranted() {
        if (openSettingsAfterPermission) {
            openSettingsAfterPermission = false
            openScanSettings()
        } else {
            startScanIfReady()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_setting -> {
                if (viewModel.uiState.value.phase == ScanPhase.Scanning) {
                    false
                } else {
                    openScanSettings()
                    true
                }
            }

            else -> false
        }
    }

    companion object {
        private const val CHILD_BEFORE_SCANNING = 0
        private const val CHILD_SCANNING = 1
        private const val CHILD_AFTER_SCANNING = 2

        private const val PROGRESS_MAX = 100

        private const val DEFAULT_EXCLUDE_SECONDS = 60L
        private const val MIN_EXCLUDE_SECONDS = 1L
        private const val MAX_EXCLUDE_SECONDS = 3_600L

        private const val DEFAULT_EXCLUDE_SIZE_KB = 50L
        private const val MIN_EXCLUDE_SIZE_KB = 1L
        private const val MAX_EXCLUDE_SIZE_KB = 1_048_576L

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, ScanMusicActivity::class.java))
        }
    }
}
