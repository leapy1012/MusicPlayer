package gd.app.musicplayer.ui.sleep

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.databinding.ActivitySleepBinding
import gd.app.musicplayer.playback.SleepTimerManager
import gd.app.musicplayer.playback.SleepTimerState
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.player.full.PlayerViewModel

import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.designsystem.dialog.DialogRegistry
import gd.app.musicplayer.data.local.preference.SleepPreferenceStore
import javax.inject.Inject

@AndroidEntryPoint
class SleepActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivitySleepBinding
    private val playerViewModel: PlayerViewModel by viewModels()
    @Inject lateinit var sleepPreferenceStore: SleepPreferenceStore

    private var selectedMinutes: Int = 0
    private var hasPendingChange: Boolean = false
    private var suppressNextManagerEvent: Boolean = false
    private var endAction: Int = SleepTimerState.ACTION_STOP_PLAYBACK

    companion object {
        private const val KEY_LAST_CUSTOM_MINUTES = "sleep_time"

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, SleepActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySleepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        observeSleepTimer()
    }

    override fun onBackPressed() {
        applyAndFinish()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.sleep_timer
        )
    }

    private fun setupViews() {
        binding.sleepItemClose.setOnClickListener(this)
        binding.sleepItem10.setOnClickListener(this)
        binding.sleepItem20.setOnClickListener(this)
        binding.sleepItem30.setOnClickListener(this)
        binding.sleepItem60.setOnClickListener(this)
        binding.sleepItem90.setOnClickListener(this)
        binding.sleepItemCustom.setOnClickListener(this)
        binding.sleepItemOperation1.setOnClickListener(this)
        binding.sleepItemOperation2.setOnClickListener(this)
        binding.sleepItemOperationSelect.setOnClickListener(this)

        binding.sleepItemCustomEdit.setOnEditorActionListener { _, actionId, event ->
            val done = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (done) {
                val value = binding.sleepItemCustomEdit.text?.toString()?.trim()?.toIntOrNull()
                if (value != null && value > 0) {
                    lifecycleScope.launch {
                        sleepPreferenceStore.setLastCustomMinutes(value)
                    }
                }
            }
            false
        }

        binding.sleepItemCustomEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                selectCustom()
                if (!hasPendingChange) hasPendingChange = true
            }
        }

        endAction = SleepTimerState.ACTION_STOP_PLAYBACK
        updateOperationText()
    }

    private fun observeSleepTimer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SleepTimerManager.state.collect { state ->
                    if (suppressNextManagerEvent) {
                        suppressNextManagerEvent = false
                        return@collect
                    }
                    if (!state.isActive) {
                        selectedMinutes = 0
                        hasPendingChange = false
                        renderSelection()
                        return@collect
                    }
                    selectedMinutes = state.durationMinutes
                    endAction = state.action
                    hasPendingChange = false
                    binding.sleepItemOperationSelect.isSelected = state.stopAfterCurrentTrack
                    updateOperationText()
                    renderSelection()
                }
            }
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.sleep_item_close -> {
                hasPendingChange = true
                selectedMinutes = 0
                renderSelection()
            }

            R.id.sleep_item_10 -> selectPreset(10)
            R.id.sleep_item_20 -> selectPreset(20)
            R.id.sleep_item_30 -> selectPreset(30)
            R.id.sleep_item_60 -> selectPreset(60)
            R.id.sleep_item_90 -> selectPreset(90)
            R.id.sleep_item_custom -> {
                hasPendingChange = true
                selectCustom()
            }

            R.id.sleep_item_operation_1 -> showEndActionPicker()
            R.id.sleep_item_operation_2, R.id.sleep_item_operation_select -> {
                val wasSelected = binding.sleepItemOperationSelect.isSelected
                val isSelected = !wasSelected
                binding.sleepItemOperationSelect.isSelected = isSelected
                if (wasSelected) {
                    playerViewModel.setStopAfterCurrentTrack(this, false)
                }
            }
        }
    }

    private fun selectPreset(minutes: Int) {
        hasPendingChange = true
        selectedMinutes = minutes
        renderSelection()
    }

    private fun selectCustom() {
        clearChecks()
        binding.sleepItemCustomCheck.isSelected = true

        if (selectedMinutes > 0 && selectedMinutes !in PRESET_MINUTES) {
            binding.sleepItemCustomEdit.setText(selectedMinutes.toString())
            binding.sleepItemCustomEdit.setSelection(
                binding.sleepItemCustomEdit.text?.length ?: 0
            )
            return
        }

        if (binding.sleepItemCustomEdit.text.isNullOrBlank()) {
            lifecycleScope.launch {
                val last = sleepPreferenceStore.getLastCustomMinutes()
                binding.sleepItemCustomEdit.setText(last.toString())
                binding.sleepItemCustomEdit.setSelection(
                    binding.sleepItemCustomEdit.text?.length ?: 0
                )
            }
        } else {
            binding.sleepItemCustomEdit.setSelection(
                binding.sleepItemCustomEdit.text?.length ?: 0
            )
        }
    }

    private fun showEndActionPicker() {
        val items = listOf(getString(R.string.sleep_stop_playing), getString(R.string.sleep_exit_player))
        val selectedIndex = if (endAction == SleepTimerState.ACTION_STOP_PLAYBACK) 0 else 1
        val config = OptionsListDialog.Config.create(this, items).apply {
            selectedItemIndex = selectedIndex
            onItemClickListener = AdapterView.OnItemClickListener { _, _, which, _ ->
                endAction = if (which == 0) {
                    SleepTimerState.ACTION_STOP_PLAYBACK
                } else {
                    SleepTimerState.ACTION_EXIT_PLAYER
                }
                updateOperationText()
                suppressNextManagerEvent = true
                DialogRegistry.dismissAll(this@SleepActivity)
            }
        }
        OptionsListDialog.show(this, config)
    }

    private fun updateOperationText() {
        binding.sleepItemOperationText.setText(
            if (endAction == SleepTimerState.ACTION_STOP_PLAYBACK) {
                R.string.sleep_stop_playing
            } else {
                R.string.sleep_exit_player
            }
        )
    }

    private fun clearChecks() {
        setChecked(binding.sleepItemCloseCheck, false)
        setChecked(binding.sleepItem10Check, false)
        setChecked(binding.sleepItem20Check, false)
        setChecked(binding.sleepItem30Check, false)
        setChecked(binding.sleepItem60Check, false)
        setChecked(binding.sleepItem90Check, false)
        setChecked(binding.sleepItemCustomCheck, false)
    }

    private fun renderSelection() {
        clearChecks()
        when (selectedMinutes) {
            0 -> setChecked(binding.sleepItemCloseCheck, true)
            10 -> setChecked(binding.sleepItem10Check, true)
            20 -> setChecked(binding.sleepItem20Check, true)
            30 -> setChecked(binding.sleepItem30Check, true)
            60 -> setChecked(binding.sleepItem60Check, true)
            90 -> setChecked(binding.sleepItem90Check, true)
            else -> {
                setChecked(binding.sleepItemCustomCheck, true)
                if (selectedMinutes > 0) {
                    binding.sleepItemCustomEdit.setText(selectedMinutes.toString())
                }
            }
        }

        if (selectedMinutes == 0) {
            lifecycleScope.launch {
                val last = sleepPreferenceStore.getLastCustomMinutes()
                binding.sleepItemCustomEdit.setText(last.toString())
            }
        }
    }

    private fun applyAndFinish() {
        lifecycleScope.launch {
            if (hasPendingChange) {
                if (binding.sleepItemCustomCheck.isSelected) {
                    val custom = binding.sleepItemCustomEdit.text
                        ?.toString()
                        ?.trim()
                        ?.toIntOrNull()

                    if (custom == null || custom <= 0) {
                        ToastUtil.show(
                            this@SleepActivity,
                            Toast.LENGTH_SHORT,
                            getString(R.string.input_error)
                        )
                        return@launch
                    }

                    selectedMinutes = custom
                    sleepPreferenceStore.setLastCustomMinutes(custom)
                }

                if (selectedMinutes <= 0) {
                    SleepTimerManager.cancel()
                } else {
                    SleepTimerManager.start(
                        context = this@SleepActivity,
                        durationMinutes = selectedMinutes,
                        action = endAction,
                        stopAfterCurrentTrack = binding.sleepItemOperationSelect.isSelected
                    )
                }
            }

            binding.sleepItemCustomEdit.hideKeyboard()
            finish()
        }
    }

    private fun setChecked(view: ImageView, checked: Boolean) {
        view.isSelected = checked
    }
}

private val PRESET_MINUTES = setOf(10, 20, 30, 60, 90)
