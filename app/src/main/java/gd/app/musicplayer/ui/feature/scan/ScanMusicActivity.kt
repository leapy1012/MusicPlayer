package gd.app.musicplayer.ui.feature.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.navigateBack
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.ActivityScanMusicBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.hidden.HiddenFoldersActivity
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
    private val scanHideParent by lazy { binding.root.findViewById<LinearLayout>(R.id.scan_hide_parent) }
    private val scanHideClickParent by lazy { binding.root.findViewById<LinearLayout>(R.id.scan_hide_click_parent) }
    private val scanDeleteParent by lazy { binding.root.findViewById<LinearLayout>(R.id.scan_delete_parent) }

    private var currentPhase: ScanPhase = ScanPhase.IDLE

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, ScanMusicActivity::class.java))
        }
    }

    private enum class ScanPhase {
        IDLE,
        SCANNING,
        RESULT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeViewModel()
    }

    private fun initViews() {
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
        binding.toolbar.navigateBack(this)
        binding.toolbar.setOnMenuItemClickListener(this)

        binding.scanStartStop.setOnClickListener(::onPrimaryActionClicked)
        scanHideClickParent.setOnClickListener { HiddenFoldersActivity.start(this) }

        scanPath.movementMethod = LinkMovementMethod.getInstance()
        scanProgress.isEnabled = false
        scanProgress.visibility = View.INVISIBLE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun validateScanInput(): Boolean {
        if (TextUtils.isEmpty(excludeDurationEditText.editableText)) {
            ToastUtil.show(this, R.string.equalizer_edit_input_error)
            return false
        }
        if (TextUtils.isEmpty(excludeSizeEditText.editableText)) {
            ToastUtil.show(this, R.string.equalizer_edit_input_error)
            return false
        }
        return true
    }

    private fun onPrimaryActionClicked(view: View) {
        when (currentPhase) {
            ScanPhase.IDLE -> startScan()
            ScanPhase.SCANNING -> viewModel.cancelScan()
            ScanPhase.RESULT -> onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun startScan() {
        if (!hasAudioPermission()) {
            requestAudioPermission()
            return
        }
        if (!validateScanInput()) return

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

    private fun formatTrackCount(value: Int): String {
        return resources.getQuantityString(R.plurals.plurals_track, value, value)
    }

    private fun renderIdleState() {
        currentPhase = ScanPhase.IDLE
        binding.scanViewFlipper.displayedChild = 0
        binding.scanStartStop.setText(R.string.scan_start)
        binding.scanLibraryInfo.setText(R.string.scan_condition_title)
        binding.musicScanProgress?.stopAnimationImmediately()

        scanProgress.visibility = View.GONE
        scanPath.text = ""
        binding.toolbar.menu.findItem(R.id.menu_setting)?.isVisible = true
    }

    private fun renderScanningState(state: ScanUiState) {
        currentPhase = ScanPhase.SCANNING
        binding.scanViewFlipper.displayedChild = 1
        binding.scanStartStop.setText(R.string.scan_stop)
        binding.scanLibraryInfo.text = getString(R.string.scan_media) + " " + state.progressPercent + "%"

        scanProgress.setMax(100)
        scanProgress.setProgress(state.progressPercent)
        scanProgress.visibility = View.VISIBLE

        scanPath.text = state.currentPath
        binding.musicScanProgress?.startAnimation()
        binding.toolbar.menu.findItem(R.id.menu_setting)?.isVisible = false
    }

    private fun renderResultState(result: ScanResultSummary) {
        currentPhase = ScanPhase.RESULT
        binding.musicScanProgress?.stopAnimationSmoothly()
        binding.scanViewFlipper.displayedChild = 2
        binding.scanStartStop.setText(R.string.scan_end)
        binding.scanLibraryInfo.setText(R.string.scan_end)

        scanProgress.visibility = View.GONE

        scanReportSongs.text = getString(R.string.scan_result, formatTrackCount(result.importedCount))
        scanReportAdded.text = getString(R.string.scan_result_1, formatTrackCount(result.addedCount))
        scanReportFiltered.text = getString(R.string.scan_result_2, formatTrackCount(result.filteredOutCount))

        val hasDeleted = result.deletedCount > 0
        scanDeleteParent.visibility = if (hasDeleted) View.VISIBLE else View.GONE
        if (hasDeleted) {
            scanDeleteDetails.text = getString(R.string.scan_result_3, formatTrackCount(result.deletedCount))
        }
        scanHideParent.visibility = View.VISIBLE
        binding.toolbar.menu.findItem(R.id.menu_setting)?.isVisible = true
    }

    override fun onAudioPermissionGranted() {
        startScan()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_setting && currentPhase != ScanPhase.SCANNING) {
            HiddenFoldersActivity.start(this)
            return true
        }
        return false
    }

    override fun onBackPressed() {
        if (currentPhase == ScanPhase.SCANNING) {
            viewModel.cancelScan()
            return
        }
        super.onBackPressed()
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
