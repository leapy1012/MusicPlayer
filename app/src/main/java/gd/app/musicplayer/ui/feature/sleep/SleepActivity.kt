package gd.app.musicplayer.ui.feature.sleep

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.ActivitySleepBinding
import gd.app.musicplayer.playback.SleepTimerManager
import gd.app.musicplayer.playback.SleepTimerState
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.core.extension.hideKeyboard
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.core.util.ToastUtil
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SleepActivity : BaseActivity() {

    private lateinit var binding: ActivitySleepBinding
    private var selectedMinutes: Int? = null
    private var endAction: Int = SleepTimerState.ACTION_EXIT_PLAYER

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, SleepActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySleepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupOptions()
        observeSleepTimer()
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

    private fun setupOptions() {
        binding.sleepItemClose.setOnClickListener {
            SleepTimerManager.cancel()
            binding.sleepItemCustomEdit.hideKeyboard()
            ToastUtil.show(this, Toast.LENGTH_SHORT, getString(R.string.sleep_cancel))
        }

        binding.sleepItem10.setOnClickListener { startTimer(10) }
        binding.sleepItem20.setOnClickListener { startTimer(20) }
        binding.sleepItem30.setOnClickListener { startTimer(30) }
        binding.sleepItem60.setOnClickListener { startTimer(60) }
        binding.sleepItem90.setOnClickListener { startTimer(90) }
        binding.sleepItemCustom.setOnClickListener {
            val customMinutes = binding.sleepItemCustomEdit.text?.toString()?.trim()
                ?.toIntOrNull()
                ?.coerceIn(1, 9999)
            if (customMinutes == null) {
                ToastUtil.show(this, Toast.LENGTH_SHORT, "Set a valid minute value (1-9999).")
                return@setOnClickListener
            }
            startTimer(customMinutes)
        }

        binding.sleepItemOperation1.setOnClickListener {
            endAction = if (endAction == SleepTimerState.ACTION_EXIT_PLAYER) {
                SleepTimerState.ACTION_STOP_PLAYBACK
            } else {
                SleepTimerState.ACTION_EXIT_PLAYER
            }
            updateOperationSummary()
        }

        binding.sleepItemOperation2.setOnClickListener {
            binding.sleepItemOperationSelect.isSelected = !binding.sleepItemOperationSelect.isSelected
        }
    }

    private fun startTimer(minutes: Int) {
        selectedMinutes = minutes
        val stopAfterCurrentTrack = binding.sleepItemOperationSelect.isSelected
        SleepTimerManager.start(
            context = this,
            durationMinutes = minutes,
            action = endAction,
            stopAfterCurrentTrack = stopAfterCurrentTrack
        )
        renderSelectedPreset(minutes)
    }

    private fun observeSleepTimer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SleepTimerManager.state.collect { state ->
                    if (!state.isActive) {
                        renderSelectedPreset(null)
                        binding.sleepItemCloseCheck.isSelected = true
                        binding.sleepItemOperationSelect.isSelected = false
                        binding.sleepItemOperationText.setText(
                            if (endAction == SleepTimerState.ACTION_EXIT_PLAYER) {
                                R.string.sleep_exit_player
                            } else {
                                R.string.sleep_stop_playing
                            }
                        )
                        return@collect
                    }

                    val remainingMinutes = (state.remainingMs / 60_000f).roundToInt().coerceAtLeast(1)
                    binding.sleepItemCloseCheck.isSelected = false
                    binding.sleepItemOperationSelect.isSelected = state.stopAfterCurrentTrack
                    binding.sleepItemOperationText.text = getString(
                        R.string.sleep_mode_tips,
                        remainingMinutes.toString()
                    )
                    renderSelectedPreset(state.durationMinutes)
                }
            }
        }
    }

    private fun updateOperationSummary() {
        binding.sleepItemOperationText.setText(
            if (endAction == SleepTimerState.ACTION_EXIT_PLAYER) {
                R.string.sleep_exit_player
            } else {
                R.string.sleep_stop_playing
            }
        )
    }

    private fun renderSelectedPreset(minutes: Int?) {
        val selected = minutes ?: selectedMinutes
        setSelected(binding.sleepItem10Check, selected == 10)
        setSelected(binding.sleepItem20Check, selected == 20)
        setSelected(binding.sleepItem30Check, selected == 30)
        setSelected(binding.sleepItem60Check, selected == 60)
        setSelected(binding.sleepItem90Check, selected == 90)
        val custom = selected != null && selected !in setOf(10, 20, 30, 60, 90)
        setSelected(binding.sleepItemCustomCheck, custom)
        if (custom) {
            binding.sleepItemCustomEdit.setText(selected.toString())
        }
    }

    private fun setSelected(view: ImageView, selected: Boolean) {
        view.isSelected = selected
    }
}
