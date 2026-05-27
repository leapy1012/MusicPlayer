package gd.app.musicplayer.ui.sleep

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.DialogRegistry
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.core.datastore.SleepPreferenceStore
import gd.app.musicplayer.databinding.ActivitySleepBinding
import gd.app.musicplayer.playback.timer.SleepTimerManager
import gd.app.musicplayer.playback.timer.SleepTimerState
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SleepActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivitySleepBinding

    private val playerViewModel: PlayerViewModel by viewModels()

    @Inject lateinit var sleepPreferenceStore: SleepPreferenceStore
    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    private var selectedMinutes: Int = 0
    private var hasPendingChange: Boolean = false
    private var suppressCustomWatcher: Boolean = false
    private var endAction: Int = SleepTimerState.ACTION_STOP_PLAYBACK

    private val presetItems: Map<Int, ImageView>
        get() = mapOf(
            10 to binding.sleepItem10Check,
            20 to binding.sleepItem20Check,
            30 to binding.sleepItem30Check,
            60 to binding.sleepItem60Check,
            90 to binding.sleepItem90Check
        )

    private val allCheckViews: List<ImageView>
        get() = listOf(
            binding.sleepItemCloseCheck,
            binding.sleepItem10Check,
            binding.sleepItem20Check,
            binding.sleepItem30Check,
            binding.sleepItem60Check,
            binding.sleepItem90Check,
            binding.sleepItemCustomCheck
        )

    companion object {
        private val PRESET_MINUTES = setOf(10, 20, 30, 60, 90)

        fun start(context: Context) {
            context.startActivityCompat(Intent(context, SleepActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySleepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackPressedHandler()
        setupToolbar()
        setupClickListeners()
        setupCustomInput()
        loadSavedBehavior()
        observeSleepTimer()
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    applyAndFinish()
                }
            }
        )
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.sleep_item_close -> selectDisabled()

            R.id.sleep_item_10 -> selectPreset(10)
            R.id.sleep_item_20 -> selectPreset(20)
            R.id.sleep_item_30 -> selectPreset(30)
            R.id.sleep_item_60 -> selectPreset(60)
            R.id.sleep_item_90 -> selectPreset(90)

            R.id.sleep_item_custom -> selectCustom(markPending = true)

            R.id.sleep_item_operation_1 -> showEndActionPicker()

            R.id.sleep_item_operation_2,
            R.id.sleep_item_operation_select -> toggleStopAfterCurrentTrack()
        }
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

    private fun setupClickListeners() = with(binding) {
        listOf(
            sleepItemClose,
            sleepItem10,
            sleepItem20,
            sleepItem30,
            sleepItem60,
            sleepItem90,
            sleepItemCustom,
            sleepItemOperation1,
            sleepItemOperation2,
            sleepItemOperationSelect
        ).forEach { it.setOnClickListener(this@SleepActivity) }
    }

    private fun setupCustomInput() = with(binding.sleepItemCustomEdit) {
        setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    event?.isEnterKeyDown() == true

            if (isDoneAction) {
                saveCustomMinutesIfValid()
                hideKeyboard()
                clearFocus()
            }

            false
        }

        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                selectCustom(markPending = true)
            }
        }

        addTextChangedListener(customTextWatcher)
    }

    private fun loadSavedBehavior() {
        lifecycleScope.launch {
            endAction = sleepPreferenceStore.getEndAction()

            binding.sleepItemOperationSelect.isSelected =
                sleepPreferenceStore.getStopAfterCurrentTrackEnabled()

            updateOperationText()

            if (binding.sleepItemCustomEdit.text.isNullOrBlank()) {
                setCustomEditText(sleepPreferenceStore.getLastCustomMinutes())
            }
        }
    }

    private fun observeSleepTimer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SleepTimerManager.state.collect(::renderSleepTimerState)
            }
        }
    }

    private suspend fun renderSleepTimerState(state: SleepTimerState) {
        // Important:
        // SleepTimerManager emits every second.
        // Do not let timer ticks overwrite the user's new selection/custom input.
        if (hasPendingChange) return

        if (state.isActive) {
            selectedMinutes = state.durationMinutes
            endAction = state.action
            binding.sleepItemOperationSelect.isSelected = state.stopAfterCurrentTrack
        } else {
            selectedMinutes = 0
            endAction = sleepPreferenceStore.getEndAction()
            binding.sleepItemOperationSelect.isSelected =
                sleepPreferenceStore.getStopAfterCurrentTrackEnabled()
        }

        updateOperationText()
        renderSelection(syncCustomEditText = true)
    }

    private fun selectDisabled() {
        selectedMinutes = 0
        hasPendingChange = true
        renderSelection(syncCustomEditText = false)
    }

    private fun selectPreset(minutes: Int) {
        selectedMinutes = minutes
        hasPendingChange = true
        renderSelection(syncCustomEditText = false)
    }

    private fun selectCustom(
        markPending: Boolean,
        syncEditText: Boolean = true
    ) {
        if (markPending) {
            hasPendingChange = true
        }

        clearChecks()
        binding.sleepItemCustomCheck.isSelected = true

        if (!syncEditText) {
            moveCustomCursorToEnd()
            return
        }

        when {
            selectedMinutes > 0 && selectedMinutes !in PRESET_MINUTES -> {
                setCustomEditText(selectedMinutes)
                moveCustomCursorToEnd()
            }

            binding.sleepItemCustomEdit.text.isNullOrBlank() -> {
                lifecycleScope.launch {
                    setCustomEditText(sleepPreferenceStore.getLastCustomMinutes())
                    moveCustomCursorToEnd()
                }
            }

            else -> moveCustomCursorToEnd()
        }
    }

    private fun toggleStopAfterCurrentTrack() {
        val wasSelected = binding.sleepItemOperationSelect.isSelected
        val isSelected = !wasSelected

        binding.sleepItemOperationSelect.isSelected = isSelected

        lifecycleScope.launch {
            sleepPreferenceStore.setStopAfterCurrentTrackEnabled(isSelected)
        }

        SleepTimerManager.updateBehavior(
            action = endAction,
            stopAfterCurrentTrack = isSelected
        )

        if (wasSelected) {
            playerViewModel.setStopAfterCurrentTrack(this, false)
        }
    }

    private fun showEndActionPicker() {
        val items = listOf(
            getString(R.string.sleep_stop_playing),
            getString(R.string.sleep_exit_player)
        )

        val selectedIndex = if (endAction == SleepTimerState.ACTION_STOP_PLAYBACK) 0 else 1

        val config = materialDialogConfigFactory
            .createMaterialListDialogConfig(this, items)
            .apply {
                selectedItemIndex = selectedIndex
                onItemClickListener = AdapterView.OnItemClickListener { _, _, index, _ ->
                    updateEndAction(index)
                    DialogRegistry.dismissAll(this@SleepActivity)
                }
            }

        OptionsListDialog.show(this, config)
    }

    private fun updateEndAction(selectedIndex: Int) {
        endAction = when (selectedIndex) {
            0 -> SleepTimerState.ACTION_STOP_PLAYBACK
            else -> SleepTimerState.ACTION_EXIT_PLAYER
        }

        lifecycleScope.launch {
            sleepPreferenceStore.setEndAction(endAction)
        }

        updateOperationText()

        SleepTimerManager.updateBehavior(
            action = endAction,
            stopAfterCurrentTrack = binding.sleepItemOperationSelect.isSelected
        )
    }

    private fun updateOperationText() {
        val textRes = when (endAction) {
            SleepTimerState.ACTION_STOP_PLAYBACK -> R.string.sleep_stop_playing
            else -> R.string.sleep_exit_player
        }

        binding.sleepItemOperationText.setText(textRes)
    }

    private fun renderSelection(syncCustomEditText: Boolean) {
        clearChecks()

        when (selectedMinutes) {
            0 -> binding.sleepItemCloseCheck.isSelected = true

            in PRESET_MINUTES -> {
                presetItems[selectedMinutes]?.isSelected = true
            }

            else -> {
                binding.sleepItemCustomCheck.isSelected = true

                if (syncCustomEditText && selectedMinutes > 0) {
                    setCustomEditText(selectedMinutes)
                    moveCustomCursorToEnd()
                }
            }
        }
    }

    private fun clearChecks() {
        allCheckViews.forEach { it.isSelected = false }
    }

    private fun applyAndFinish() {
        lifecycleScope.launch {
            if (hasPendingChange) {
                val shouldContinue = applyPendingSleepTimerChange()
                if (!shouldContinue) return@launch
            }

            binding.sleepItemCustomEdit.hideKeyboard()
            finish()
        }
    }

    private suspend fun applyPendingSleepTimerChange(): Boolean {
        if (binding.sleepItemCustomCheck.isSelected) {
            val customMinutes = readCustomMinutes()

            if (customMinutes == null || customMinutes <= 0) {
                showInputError()
                return false
            }

            selectedMinutes = customMinutes
            sleepPreferenceStore.setLastCustomMinutes(customMinutes)
        }

        if (selectedMinutes <= 0) {
            SleepTimerManager.cancel()
        } else {
            SleepTimerManager.start(
                context = this,
                durationMinutes = selectedMinutes,
                action = endAction,
                stopAfterCurrentTrack = binding.sleepItemOperationSelect.isSelected
            )
        }

        hasPendingChange = false
        return true
    }

    private fun readCustomMinutes(): Int? {
        return binding.sleepItemCustomEdit.text
            ?.toString()
            ?.trim()
            ?.toIntOrNull()
    }

    private fun saveCustomMinutesIfValid() {
        val minutes = readCustomMinutes()

        if (minutes != null && minutes > 0) {
            lifecycleScope.launch {
                sleepPreferenceStore.setLastCustomMinutes(minutes)
            }
        }
    }

    private fun showInputError() {
        ToastUtil.show(
            this,
            Toast.LENGTH_SHORT,
            getString(R.string.input_error)
        )
    }

    private fun setCustomEditText(minutes: Int) {
        suppressCustomWatcher = true

        try {
            binding.sleepItemCustomEdit.setText(minutes.toString())
        } finally {
            suppressCustomWatcher = false
        }
    }

    private fun moveCustomCursorToEnd() {
        binding.sleepItemCustomEdit.setSelection(
            binding.sleepItemCustomEdit.text?.length ?: 0
        )
    }

    private val customTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) = Unit

        override fun onTextChanged(
            s: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) {
            if (suppressCustomWatcher) return

            selectCustom(
                markPending = false,
                syncEditText = false
            )

            hasPendingChange = true
        }

        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun KeyEvent.isEnterKeyDown(): Boolean {
        return keyCode == KeyEvent.KEYCODE_ENTER && action == KeyEvent.ACTION_DOWN
    }
}