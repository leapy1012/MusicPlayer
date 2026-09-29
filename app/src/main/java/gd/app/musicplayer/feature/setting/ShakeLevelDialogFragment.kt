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
import gd.app.musicplayer.core.designsystem.dialog.CouiAlertDialogSurface
import gd.app.musicplayer.databinding.DialogShakeLevelBinding
import kotlin.math.max
import kotlin.math.min

class ShakeLevelDialogFragment : DialogFragment() {

    private var binding: DialogShakeLevelBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentBinding = DialogShakeLevelBinding.inflate(LayoutInflater.from(requireContext()))
        binding = contentBinding
        disableClipAlongParents(contentBinding.root)

        val seek = contentBinding.shakeLevelSeek
        val currentShakeLevel = requireArguments().getFloat(ARG_SHAKE_LEVEL, DEFAULT_SHAKE_LEVEL)
        seek.progress = (currentShakeLevel * seek.max).toInt()
        updateShakeLevelNumber(seek.progress)

        seek.setOnSeekBarChangeListener(object : COUISeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: COUISeekBar, progress: Int, fromUser: Boolean) {
                updateShakeLevelNumber(progress)
            }

            override fun onStartTrackingTouch(seekBar: COUISeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: COUISeekBar) = Unit
        })

        contentBinding.shakeLevelPlus.setOnClickListener {
            seek.progress = min(seek.max, seek.progress + 1)
        }
        contentBinding.shakeLevelMinus.setOnClickListener {
            seek.progress = max(0, seek.progress - 1)
        }
        contentBinding.shakeLevelReset.setOnClickListener {
            seek.progress = (seek.max * 0.5f).toInt()
        }

        val dialog = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_Center
        )
            .setTitle(R.string.shake_level)
            .setView(contentBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            disableClipAlongParents(contentBinding.root)
            CouiAlertDialogSurface.apply(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val shakeLevel = seek.progress.toFloat() / seek.max.toFloat()
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    bundleOf(KEY_SHAKE_LEVEL to shakeLevel)
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

    private fun updateShakeLevelNumber(progress: Int) {
        binding?.shakeLevelNumber?.text = (progress + 1).toString()
    }

    companion object {
        const val RESULT_KEY = "shake_level_result"
        const val KEY_SHAKE_LEVEL = "shake_level"
        private const val ARG_SHAKE_LEVEL = "arg_shake_level"
        private const val DEFAULT_SHAKE_LEVEL = 0.5f

        fun newInstance(currentShakeLevel: Float): ShakeLevelDialogFragment {
            return ShakeLevelDialogFragment().apply {
                arguments = bundleOf(ARG_SHAKE_LEVEL to currentShakeLevel)
            }
        }
    }
}
