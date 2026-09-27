package gd.app.musicplayer.feature.setting

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import com.coui.appcompat.seekbar.COUISeekBar
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogReplayGainPreampBinding
import kotlin.math.round

class ReplayGainPreampDialogFragment : DialogFragment() {

    private var binding: DialogReplayGainPreampBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentBinding = DialogReplayGainPreampBinding.inflate(LayoutInflater.from(requireContext()))
        binding = contentBinding
        disableClipAlongParents(contentBinding.root)

        val listener = object : COUISeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: COUISeekBar, progress: Int, fromUser: Boolean) {
                val value = replayGainDbFromProgress(progress)
                when (seekBar.id) {
                    R.id.with_tag_seek -> contentBinding.withTagText.text = formatReplayGainDb(value)
                    R.id.without_tag_seek ->
                        contentBinding.withoutTagText.text = formatReplayGainDb(value)
                }
            }

            override fun onStartTrackingTouch(seekBar: COUISeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: COUISeekBar) = Unit
        }

        contentBinding.withTagSeek.setOnSeekBarChangeListener(listener)
        contentBinding.withoutTagSeek.setOnSeekBarChangeListener(listener)

        contentBinding.withTagSeek.progress = progressFromReplayGainDb(
            requireArguments().getFloat(ARG_WITH_TAG, DEFAULT_PREAMP_DB)
        )
        contentBinding.withoutTagSeek.progress = progressFromReplayGainDb(
            requireArguments().getFloat(ARG_WITHOUT_TAG, DEFAULT_PREAMP_DB)
        )
        contentBinding.withTagText.text =
            formatReplayGainDb(replayGainDbFromProgress(contentBinding.withTagSeek.progress))
        contentBinding.withoutTagText.text =
            formatReplayGainDb(replayGainDbFromProgress(contentBinding.withoutTagSeek.progress))

        contentBinding.replayGainReset.setOnClickListener {
            contentBinding.withTagSeek.progress = REPLAY_GAIN_SEEK_CENTER
            contentBinding.withoutTagSeek.progress = REPLAY_GAIN_SEEK_CENTER
        }

        // Cancel + OK only → COUI keeps a horizontal button row (3 buttons stack vertically).
        val dialog = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_Center
        )
            .setTitle(R.string.replay_gain_preamp)
            .setView(contentBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            disableClipAlongParents(contentBinding.root)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    bundleOf(
                        KEY_WITH_TAG to replayGainDbFromProgress(contentBinding.withTagSeek.progress),
                        KEY_WITHOUT_TAG to
                            replayGainDbFromProgress(contentBinding.withoutTagSeek.progress)
                    )
                )
                dismiss()
            }
        }
        return dialog
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun disableClipAlongParents(view: android.view.View) {
        var current: android.view.View? = view
        while (current != null) {
            if (current is ViewGroup) {
                current.clipChildren = false
                current.clipToPadding = false
            }
            current = current.parent as? android.view.View
        }
    }

    private fun progressFromReplayGainDb(value: Float): Int {
        val clamped = value.coerceIn(MIN_REPLAY_GAIN_PREAMP_DB, MAX_REPLAY_GAIN_PREAMP_DB)
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

        fun newInstance(withTag: Float, withoutTag: Float): ReplayGainPreampDialogFragment {
            return ReplayGainPreampDialogFragment().apply {
                arguments = bundleOf(
                    ARG_WITH_TAG to withTag,
                    ARG_WITHOUT_TAG to withoutTag
                )
            }
        }
    }
}
