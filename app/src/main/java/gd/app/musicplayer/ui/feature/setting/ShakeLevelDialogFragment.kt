package gd.app.musicplayer.ui.feature.setting

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.ui.dialog.BaseDialogFragment
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.databinding.DialogShakeLevelBinding
import gd.app.musicplayer.util.ShakeDetector
import kotlin.math.max
import kotlin.math.min

class ShakeLevelDialogFragment : BaseDialogFragment() {

    private var _binding: DialogShakeLevelBinding? = null
    private val binding: DialogShakeLevelBinding
        get() = requireNotNull(_binding)

    private val onShakeLevelChange = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            android.util.Log.e("Leapy", "LeapyLeapy")
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

        binding.shakeLevelPlus.setOnClickListener { onShakeLevelPlus() }
        binding.shakeLevelMinus.setOnClickListener { onShakeLevelMinus() }
        binding.dialogButtonReset.setOnClickListener { onShakeLevelReset() }
        binding.dialogButtonOk.setOnClickListener { onShakeLevelSave() }

        binding.shakeLevelSeek.setOnSeekBarChangeListener (onShakeLevelChange)
        val currentShakeLevel = requireContext().appDependencies.preferenceUtil.getShakeLevel()
        binding.shakeLevelSeek.setProgress((currentShakeLevel * binding.shakeLevelSeek.getMax()).toInt())
    }

    override fun provideBackgroundDrawable(): Drawable {
        val themePalette =
            requireContext().appDependencies.themeRegistry.getCurrentTheme(requireContext())
        return themePalette.getDialogBackground(requireContext())
    }

    private fun onShakeLevelSave() {
        val shakeLevel: Float =
            binding.shakeLevelSeek.getProgress().toFloat() / binding.shakeLevelSeek.getMax()
        requireContext().appDependencies.preferenceUtil.setShakeLevel(shakeLevel)
        ShakeDetector.getInstance(requireContext()).updateSensitivity(shakeLevel)
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
        fun newInstance(): ShakeLevelDialogFragment = ShakeLevelDialogFragment()
    }
}
