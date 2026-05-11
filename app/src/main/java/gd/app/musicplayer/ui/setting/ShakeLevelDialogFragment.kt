package gd.app.musicplayer.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.databinding.DialogShakeLevelBinding
import kotlin.math.max
import kotlin.math.min

class ShakeLevelDialogFragment : BaseDialogFragment() {

    private var _binding: DialogShakeLevelBinding? = null
    private val binding: DialogShakeLevelBinding
        get() = requireNotNull(_binding)

    private val onShakeLevelChange = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            binding.shakeLevelNumber.text = (progress + 1).toString()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogShakeLevelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogBackground(view)

        binding.shakeLevelPlus.setOnClickListener { onShakeLevelPlus() }
        binding.shakeLevelMinus.setOnClickListener { onShakeLevelMinus() }
        binding.dialogButtonReset.setOnClickListener { onShakeLevelReset() }
        binding.dialogButtonOk.setOnClickListener { onShakeLevelSave() }

        binding.shakeLevelSeek.setOnSeekBarChangeListener (onShakeLevelChange)
        val currentShakeLevel = requireArguments().getFloat(ARG_SHAKE_LEVEL, DEFAULT_SHAKE_LEVEL)
        binding.shakeLevelSeek.setProgress((currentShakeLevel * binding.shakeLevelSeek.getMax()).toInt())
    }

    private fun onShakeLevelSave() {
        val shakeLevel: Float =
            binding.shakeLevelSeek.getProgress().toFloat() / binding.shakeLevelSeek.getMax()
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply { putFloat(KEY_SHAKE_LEVEL, shakeLevel) }
        )
        dismiss()
    }

    private fun onShakeLevelReset() {
        val defaultProgress = (binding.shakeLevelSeek.getMax() * 0.5f).toInt()
        binding.shakeLevelSeek.setProgress(defaultProgress)
    }

    private fun onShakeLevelPlus() {
        val newProgress = min(
            binding.shakeLevelSeek.getMax(),
            binding.shakeLevelSeek.getProgress() + 1
        )
        binding.shakeLevelSeek.setProgress(newProgress)
    }

    private fun onShakeLevelMinus() {
        val newProgress = max(
            0,
            binding.shakeLevelSeek.getProgress() - 1
        )
        binding.shakeLevelSeek.setProgress(newProgress)
    }

    companion object {
        const val RESULT_KEY = "shake_level_result"
        const val KEY_SHAKE_LEVEL = "shake_level"
        private const val ARG_SHAKE_LEVEL = "arg_shake_level"
        private const val DEFAULT_SHAKE_LEVEL = 0.5f

        fun newInstance(currentShakeLevel: Float): ShakeLevelDialogFragment {
            return ShakeLevelDialogFragment().apply {
                arguments = Bundle().apply {
                    putFloat(ARG_SHAKE_LEVEL, currentShakeLevel)
                }
            }
        }
    }
}
