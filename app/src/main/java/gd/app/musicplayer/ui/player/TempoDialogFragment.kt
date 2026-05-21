package gd.app.musicplayer.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.data.local.preference.PlaybackStatePreferenceStore
import gd.app.musicplayer.databinding.DialogTempoBinding
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@AndroidEntryPoint
class TempoDialogFragment : BaseBottomSheetDialogFragment(), SeekBar.OnSeekBarChangeListener {

    @Inject
    lateinit var playbackStatePreferenceStore: PlaybackStatePreferenceStore

    private var _binding: DialogTempoBinding? = null
    private val binding: DialogTempoBinding
        get() = checkNotNull(_binding)

    private val playbackViewModel: PlayerViewModel by viewModels()
    private lateinit var speedButtons: List<TextView>

    override fun onCreateBottomSheetView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTempoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speedButtons = listOf(
            binding.popupTextSpeed1,
            binding.popupTextSpeed2,
            binding.popupTextSpeed3,
            binding.popupTextSpeed4
        )

        binding.popupSeekPitch.setOnSeekBarChangeListener(this)
        binding.popupSeekTempo.setMax(SPEED_MAX)
        binding.popupSeekTempo.setOnSeekBarChangeListener(this)
        binding.popupRefreshPitch.setOnClickListener { setPitchFactor(1f, fromUser = true) }
        binding.popupRefreshTempo.setOnClickListener { setSpeedFactor(1f, fromUser = true) }

        val presetSpeeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        speedButtons.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                setSpeedFactor(presetSpeeds[index], fromUser = true)
            }
        }

        renderFromPreferences()
    }

    private fun renderFromPreferences() {
        viewLifecycleOwner.lifecycleScope.launch {
            setPitchFactor(playbackStatePreferenceStore.getPlayPitch(), fromUser = false)
            setSpeedFactor(playbackStatePreferenceStore.getPlaySpeed(), fromUser = false)
        }
    }

    private fun setPitchFactor(factor: Float, fromUser: Boolean) {
        val progress = factorToPitchProgress(factor)
        if (binding.popupSeekPitch.getProgress() != progress) {
            binding.popupSeekPitch.setProgress(progress)
        }
        renderPitch(progress)
        if (fromUser) persistPitch(progressToPitchFactor(progress))
    }

    private fun setSpeedFactor(factor: Float, fromUser: Boolean) {
        val progress = speedToProgress(factor)
        if (binding.popupSeekTempo.getProgress() != progress) {
            binding.popupSeekTempo.setProgress(progress)
        }
        renderSpeed(progress)
        if (fromUser) persistSpeed(progressToSpeed(progress))
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        when (seekBar.id) {
            binding.popupSeekPitch.id -> {
                val factor = progressToPitchFactor(progress)
                renderPitch(progress)
                if (fromUser) persistPitch(factor)
            }

            binding.popupSeekTempo.id -> {
                val factor = progressToSpeed(progress)
                renderSpeed(progress)
                if (fromUser) persistSpeed(factor)
            }
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit

    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

    private fun renderPitch(progress: Int) {
        val semitoneOffset = progress - PITCH_CENTER
        val label = if (semitoneOffset > 0) "+$semitoneOffset" else semitoneOffset.toString()
        binding.popupTextPitch.text = getString(R.string.equalizer_pitch) + ": " + label
    }

    private fun renderSpeed(progress: Int) {
        val speed = progressToSpeed(progress)
        binding.popupTextTempo.text =
            getString(R.string.equalizer_speed) + ": " + formatSpeed(speed) + " x"
        val selectedIndex = presetSpeedIndex(speed)
        speedButtons.forEachIndexed { index, textView ->
            textView.isSelected = index == selectedIndex
        }
    }

    private fun persistPitch(factor: Float) {
        viewLifecycleOwner.lifecycleScope.launch {
            playbackStatePreferenceStore.setPlayPitch(factor)
            playbackViewModel.applyPlaybackTuning(requireContext())
        }
    }

    private fun persistSpeed(factor: Float) {
        viewLifecycleOwner.lifecycleScope.launch {
            playbackStatePreferenceStore.setPlaySpeed(factor)
            playbackViewModel.applyPlaybackTuning(requireContext())
        }
    }

    private fun factorToPitchProgress(factor: Float): Int {
        val semitones = (12f * (ln(factor.coerceIn(0.5f, 2.0f)) / ln(2f))).roundToInt()
        return (PITCH_CENTER + semitones).coerceIn(0, PITCH_MAX)
    }

    private fun progressToPitchFactor(progress: Int): Float {
        val semitones = progress - PITCH_CENTER
        return 2.0.pow(semitones / 12.0).toFloat().coerceIn(0.5f, 2.0f)
    }

    private fun speedToProgress(speed: Float): Int {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        val normalized = if (clamped > 1.0f) {
            ((clamped - 1.0f) / 2.0f) + 0.5f
        } else {
            (clamped - 0.5f)
        }
        return (normalized * SPEED_MAX).roundToInt().coerceIn(0, SPEED_MAX)
    }

    private fun progressToSpeed(progress: Int): Float {
        val normalized = progress.coerceIn(0, SPEED_MAX) / SPEED_MAX.toFloat()
        return if (normalized < 0.5f) {
            0.5f + normalized
        } else {
            normalized * 2.0f
        }.coerceIn(0.5f, 2.0f)
    }

    private fun presetSpeedIndex(speed: Float): Int {
        val presets = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        return presets.indices.minByOrNull { index -> abs(presets[index] - speed) } ?: 1
    }

    private fun formatSpeed(speed: Float): String {
        return if (abs(speed - speed.roundToInt()) < 0.05f) {
            speed.roundToInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", speed).trimEnd('0').trimEnd('.')
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val PITCH_MAX = 24
        private const val PITCH_CENTER = 12
        private const val SPEED_MAX = 20

        fun show(fragmentManager: FragmentManager) {
            TempoDialogFragment().show(fragmentManager, TempoDialogFragment::class.java.simpleName)
        }
    }
}
