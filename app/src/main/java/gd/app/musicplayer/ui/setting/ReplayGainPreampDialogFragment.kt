package gd.app.musicplayer.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.databinding.DialogReplayGainPreampBinding
import kotlin.math.round

class ReplayGainPreampDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogReplayGainPreampBinding? = null
    private val binding: DialogReplayGainPreampBinding
        get() = requireNotNull(_binding)

    private val seekChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: SeekBar,
            progress: Int,
            fromUser: Boolean
        ) {
            val value = replayGainDbFromProgress(progress)

            when (seekBar) {
                binding.withTagSeek -> binding.withTagText.text = formatReplayGainDb(value)
                binding.withoutTagSeek -> binding.withoutTagText.text = formatReplayGainDb(value)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogReplayGainPreampBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogBackground(view)

        binding.withTagSeek.setOnSeekBarChangeListener(seekChangeListener)
        binding.withoutTagSeek.setOnSeekBarChangeListener(seekChangeListener)

        binding.dialogButtonReset.setOnClickListener(this)
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)

        bindInitialValues()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            binding.dialogButtonOk.id -> saveAndDismiss()
            binding.dialogButtonCancel.id -> dismiss()
            binding.dialogButtonReset.id -> resetValues()
        }
    }

    private fun bindInitialValues() {
        binding.withTagSeek.setProgress(
            progressFromReplayGainDb(
                requireArguments().getFloat(ARG_WITH_TAG, DEFAULT_PREAMP_DB)
            )
        )
        binding.withoutTagSeek.setProgress(
            progressFromReplayGainDb(
                requireArguments().getFloat(ARG_WITHOUT_TAG, DEFAULT_PREAMP_DB)
            )
        )
    }

    private fun saveAndDismiss() {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply {
                putFloat(KEY_WITH_TAG, replayGainDbFromProgress(binding.withTagSeek.getProgress()))
                putFloat(
                    KEY_WITHOUT_TAG,
                    replayGainDbFromProgress(binding.withoutTagSeek.getProgress())
                )
            }
        )
        dismiss()
    }

    private fun resetValues() {
        binding.withTagSeek.setProgress(REPLAY_GAIN_SEEK_CENTER)
        binding.withoutTagSeek.setProgress(REPLAY_GAIN_SEEK_CENTER)
    }

    private fun progressFromReplayGainDb(value: Float): Int {
        val clamped = value.coerceIn(
            MIN_REPLAY_GAIN_PREAMP_DB,
            MAX_REPLAY_GAIN_PREAMP_DB
        )

        return (((clamped - MIN_REPLAY_GAIN_PREAMP_DB) / REPLAY_GAIN_RANGE_DB) *
            REPLAY_GAIN_SEEK_MAX.toFloat()).toInt()
    }

    private fun replayGainDbFromProgress(progress: Int): Float {
        val normalized = progress.coerceIn(0, REPLAY_GAIN_SEEK_MAX) /
            REPLAY_GAIN_SEEK_MAX.toFloat()
        val db = MIN_REPLAY_GAIN_PREAMP_DB + (normalized * REPLAY_GAIN_RANGE_DB)
        return round(db * REPLAY_GAIN_ROUNDING_SCALE) / REPLAY_GAIN_ROUNDING_SCALE
    }

    private fun formatReplayGainDb(value: Float): String {
        val rounded = round(value * REPLAY_GAIN_ROUNDING_SCALE) / REPLAY_GAIN_ROUNDING_SCALE
        val prefix = if (rounded > 0f) "+" else ""
        return "$prefix${rounded}dB"
    }

    companion object {
        const val RESULT_KEY = "replay_gain_preamp_result"
        const val KEY_WITH_TAG = "with_tag"
        const val KEY_WITHOUT_TAG = "without_tag"

        private const val ARG_WITH_TAG = "arg_with_tag"
        private const val ARG_WITHOUT_TAG = "arg_without_tag"

        private const val DEFAULT_PREAMP_DB = 0f
        private const val MIN_REPLAY_GAIN_PREAMP_DB = -15f
        private const val MAX_REPLAY_GAIN_PREAMP_DB = 15f
        private const val REPLAY_GAIN_RANGE_DB = 30f
        private const val REPLAY_GAIN_SEEK_MAX = 300
        private const val REPLAY_GAIN_SEEK_CENTER = REPLAY_GAIN_SEEK_MAX / 2
        private const val REPLAY_GAIN_ROUNDING_SCALE = 10f

        fun newInstance(
            withTag: Float,
            withoutTag: Float
        ): ReplayGainPreampDialogFragment {
            return ReplayGainPreampDialogFragment().apply {
                arguments = Bundle().apply {
                    putFloat(ARG_WITH_TAG, withTag)
                    putFloat(ARG_WITHOUT_TAG, withoutTag)
                }
            }
        }
    }
}
