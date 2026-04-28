package gd.app.musicplayer.ui.feature.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.ActivityScanMusicBinding
import gd.app.musicplayer.ui.hidden.HiddenFoldersActivity
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.navigateBack
import gd.app.musicplayer.core.extension.startActivityCompat
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanMusicActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    private val viewModel: ScanViewModel by viewModels()
    private lateinit var binding: ActivityScanMusicBinding
    private val scanCheckbox by lazy { binding.root.findViewById<ImageView>(R.id.scan_checkbox) }
    private val scanCheckbox2 by lazy { binding.root.findViewById<ImageView>(R.id.scan_checkbox2) }
    private val scanCheckbox3 by lazy { binding.root.findViewById<ImageView>(R.id.scan_checkbox3) }
    private val excludeDurationEditText by lazy { binding.root.findViewById<EditText>(R.id.excludeDurationEditText) }
    private val excludeSizeEditText by lazy { binding.root.findViewById<EditText>(R.id.excludeSizeEditText) }
    private val scanProgress by lazy { binding.root.findViewById<SeekBar>(R.id.scan_progress) }
    private val scanPath by lazy { binding.root.findViewById<TextView>(R.id.scan_path) }
    private val scanReportSongs by lazy { binding.root.findViewById<TextView>(R.id.scan_report_songs) }
    private val scanReportAdded by lazy { binding.root.findViewById<TextView>(R.id.scan_report_added) }
    private val scanReportFiltered by lazy { binding.root.findViewById<TextView>(R.id.scan_report_filtered) }
    private val scanDeleteDetails by lazy { binding.root.findViewById<TextView>(R.id.scan_delete_details) }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, ScanMusicActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScanMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeViewModel()
    }

    

//    override fun applyTheme() {
//        super.applyTheme()
//        binding.musicScanProgress?.setColor(255)
//    }

    private fun initViews() {
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
        binding.toolbar.navigateBack(this)
        binding.toolbar.setOnMenuItemClickListener(this)
        binding.scanStartStop.setOnClickListener {
            if (viewModel.uiState.value.isScanning) {
                viewModel.cancelScan()
            } else {
                startScan()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun startScan() {
        if (!hasAudioPermission()) {
            requestAudioPermission()
            return
        }

        viewModel.startScan(
            ScanOptions(
                excludeShort = scanCheckbox.isSelected,
                excludeBySize = scanCheckbox2.isSelected,
                excludeRingtone = scanCheckbox3.isSelected,
                durationSec = excludeDurationEditText.text?.toString()?.toIntOrNull()
                    ?.coerceIn(1, 3600) ?: 60,
                sizeKb = excludeSizeEditText.text?.toString()?.toIntOrNull()
                    ?.coerceIn(1, 1_048_576) ?: 50
            )
        )
    }

    private fun renderState(state: ScanUiState) {
        bindOptions(state.options)
        when {
            state.isScanning -> renderScanningState(state)
            state.result != null -> renderResultState(state.result)
            else -> renderIdleState()
        }
    }

    private fun bindOptions(options: ScanOptions) {
        scanCheckbox.isSelected = options.excludeShort
        scanCheckbox2.isSelected = options.excludeBySize
        scanCheckbox3.isSelected = options.excludeRingtone
        excludeDurationEditText.isEnabled = options.excludeShort
        excludeSizeEditText.isEnabled = options.excludeBySize
        if (excludeDurationEditText.text?.toString() != options.durationSec.toString()) {
            excludeDurationEditText.setText(options.durationSec.toString())
        }
        if (excludeSizeEditText.text?.toString() != options.sizeKb.toString()) {
            excludeSizeEditText.setText(options.sizeKb.toString())
        }
    }

    private fun renderIdleState() {
        binding.scanViewFlipper.displayedChild = 0
        binding.scanStartStop.setText(R.string.scan_start)
        binding.scanLibraryInfo.setText(R.string.scan_condition_title)
        binding.musicScanProgress?.stopAnimationImmediately()
    }

    private fun renderScanningState(state: ScanUiState) {
        binding.scanViewFlipper.displayedChild = 1
        binding.scanStartStop.setText(R.string.scan_stop)
        binding.scanLibraryInfo.text = getString(R.string.scan_media) + " " + state.progressPercent + "%"
        scanProgress.setMax(100)
        scanProgress.setProgress(state.progressPercent)
        scanPath.text = state.currentPath
        binding.musicScanProgress?.startAnimation()
    }

    private fun renderResultState(result: ScanResultSummary) {
        binding.musicScanProgress?.stopAnimationSmoothly()
        binding.scanViewFlipper.displayedChild = 2
        binding.scanStartStop.setText(R.string.scan_start)
        binding.scanLibraryInfo.setText(R.string.scan_end)
        scanReportSongs.text = getString(R.string.scan_result, result.importedCount.toString())
        scanReportAdded.text = getString(R.string.scan_result_1, result.addedCount.toString())
        scanReportFiltered.text = getString(R.string.scan_result_2, result.filteredOutCount.toString())
        scanDeleteDetails.text = getString(R.string.scan_result_3, result.deletedCount.toString())
    }

    override fun onAudioPermissionGranted() {
        startScan()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_setting) {
            HiddenFoldersActivity.start(this)
            return true
        }
        return false
    }

    @Suppress("unused")
    fun onCheckedChanged(view: View) {
        view.isSelected = !view.isSelected
        when (view.id) {
            R.id.scan_checkbox -> excludeDurationEditText.isEnabled = view.isSelected
            R.id.scan_checkbox2 -> excludeSizeEditText.isEnabled = view.isSelected
        }
    }
}
