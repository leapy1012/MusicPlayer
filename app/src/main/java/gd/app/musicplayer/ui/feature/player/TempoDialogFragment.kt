package gd.app.musicplayer.ui.feature.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.core.ui.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.data.local.preference.PlaybackStatePreferenceStore
import gd.app.musicplayer.databinding.DialogTempoBinding
import gd.app.musicplayer.data.repository.ThemeRepo
import gd.app.musicplayer.ui.player.PlayerViewModel
import gd.app.musicplayer.core.ui.view.SeekBar

import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@AndroidEntryPoint
class TempoDialogFragment : BaseBottomSheetDialogFragment(), SeekBar.OnSeekBarChangeListener {

    @Inject lateinit var themeRepo: ThemeRepo
    @Inject lateinit var playbackStatePreferenceStore: PlaybackStatePreferenceStore

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
        applyDialogBackground(binding.root)
//        applyTagStyles(binding.root)

        speedButtons = listOf(
            binding.popupTextSpeed1,
            binding.popupTextSpeed2,
            binding.popupTextSpeed3,
            binding.popupTextSpeed4
        )

        binding.popupSeekPitch.setOnSeekBarChangeListener(this)
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
        } else {
            renderPitch(progress)
            if (fromUser) persistPitch(progressToPitchFactor(progress))
        }
    }

    private fun setSpeedFactor(factor: Float, fromUser: Boolean) {
        val progress = speedToProgress(factor)
        if (binding.popupSeekTempo.getProgress() != progress) {
            binding.popupSeekTempo.setProgress(progress)
        } else {
            renderSpeed(progress)
            if (fromUser) persistSpeed(progressToSpeed(progress))
        }
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

    private fun applyDialogBackground(rootView: View) {
        rootView.background = themeRepo
            .getCorePalette()
            .getDialogSurfaceDrawable(rootView.context)
    }

//    private fun applyTagStyles(rootView: View) {
//        val accentColor = themeRepo.getAccentColor()
//        val palette = themeRepo.getCorePalette()
//
//        val titleColor = palette.titleColor
//        val messageColor = palette.messageColor
//        val rippleColor = palette.rippleColor
//        val dividerColor = palette.dividerColor
//        val cancelTextColor = palette.cancelTextColor
//        val cancelBaseColor = palette.cancelBaseColor
//        val confirmRippleColor = palette.confirmRippleColor
//        val selectBoxNormalColor =
//            if (titleColor == Color.WHITE) -2171170 else -3355444
//
//        fun apply(view: View) {
//            when (view.tag as? String) {
//                "dialogTitle", "dialogTitleColor", "dialogTitleIcon", "dialogItem" -> {
//                    when (view) {
//                        is TextView -> view.setTextColor(titleColor)
//                        is ImageView -> view.imageTintList = ColorStateList.valueOf(titleColor)
//                    }
//                }
//
//                "dialogMessage", "dialogMessageColor" -> {
//                    when (view) {
//                        is TextView -> view.setTextColor(messageColor)
//                        is ImageView -> view.imageTintList = ColorStateList.valueOf(messageColor)
//                    }
//                }
//
//                "dialogButton" -> {
//                    if (view is TextView) {
//                        view.setTextColor(accentColor)
//                    }
//                    view.background = DrawableUtil.rectRipple(
//                        fillColor = Color.TRANSPARENT,
//                        rippleColor = rippleColor
//                    )
//                }
//
//                "dialogConfirm" -> {
//                    if (view is TextView) {
//                        view.setTextColor(Color.WHITE)
//                    }
//                    view.background = DrawableUtil.roundedRipple(
//                        fillColor = accentColor,
//                        rippleColor = confirmRippleColor,
//                        radius = 1000f
//                    )
//                }
//
//                "dialogCancel" -> {
//                    if (view is TextView) {
//                        view.setTextColor(cancelTextColor)
//                    }
//                    view.background = DrawableUtil.roundedRipple(
//                        fillColor = cancelBaseColor,
//                        rippleColor = rippleColor,
//                        radius = 1000f
//                    )
//                }
//
//                "dialogItemBackground" -> {
//                    view.background = DrawableUtil.rectRipple(
//                        fillColor = Color.TRANSPARENT,
//                        rippleColor = rippleColor
//                    )
//                }
//
//                "dialogSelectBox" -> {
//                    if (view is ImageView) {
//                        view.imageTintList = ColorStateList(
//                            arrayOf(
//                                intArrayOf(android.R.attr.state_selected),
//                                intArrayOf(android.R.attr.state_checked),
//                                intArrayOf(android.R.attr.state_activated),
//                                intArrayOf()
//                            ),
//                            intArrayOf(
//                                accentColor,
//                                accentColor,
//                                accentColor,
//                                selectBoxNormalColor
//                            )
//                        )
//                    }
//                }
//
//                "dialogDivider", "dialogDividerColor" -> {
//                    view.setBackgroundColor(dividerColor)
//                }
//
//                "dialogSeekBar" -> {
//                    if (view is SeekBar) {
//                        view.setThumbColor(accentColor)
//                        view.setProgressDrawable(
//                            DrawableUtil.roundedProgress(
//                                Color.argb(77, Color.red(titleColor), Color.green(titleColor), Color.blue(titleColor)),
//                                accentColor,
//                                (view.context.resources.displayMetrics.density * 4f).toInt()
//                            )
//                        )
//                    }
//                }
//
//                "speedItemDes" -> {
//                    if (view is TextView) {
//                        view.setTextColor(Color.argb(160, Color.red(titleColor), Color.green(titleColor), Color.blue(titleColor)))
//                    }
//                }
//
//                "speedItemText" -> {
//                    if (view is TextView) {
//                        view.setTextColor(
//                            android.content.res.ColorStateList(
//                                arrayOf(
//                                    intArrayOf(android.R.attr.state_selected),
//                                    intArrayOf()
//                                ),
//                                intArrayOf(Color.WHITE, Color.argb(180, Color.red(titleColor), Color.green(titleColor), Color.blue(titleColor)))
//                            )
//                        )
//                        val radius = view.context.resources.displayMetrics.density * 6f
//                        view.background = gd.app.musicplayer.core.ui.drawable.ViewStateDrawables.buildStateDrawable(
//                            DrawableUtil.roundedRipple(
//                                fillColor = Color.argb(28, Color.red(titleColor), Color.green(titleColor), Color.blue(titleColor)),
//                                rippleColor = rippleColor,
//                                radius = radius
//                            ),
//                            DrawableUtil.roundedRipple(
//                                fillColor = accentColor,
//                                rippleColor = confirmRippleColor,
//                                radius = radius
//                            ),
//                            null
//                        )
//                    }
//                }
//            }
//
//            if (view is ViewGroup) {
//                for (index in 0 until view.childCount) {
//                    apply(view.getChildAt(index))
//                }
//            }
//        }
//
//        apply(rootView)
//    }

    private fun factorToPitchProgress(factor: Float): Int {
        val semitones = (12f * (ln(factor.coerceIn(0.5f, 2.0f)) / ln(2f))).roundToInt()
        return (PITCH_CENTER + semitones).coerceIn(0, PITCH_MAX)
    }

    private fun progressToPitchFactor(progress: Int): Float {
        val semitones = progress - PITCH_CENTER
        return 2.0.pow(semitones / 12.0).toFloat().coerceIn(0.5f, 2.0f)
    }

    private fun speedToProgress(speed: Float): Int {
        return (((speed.coerceIn(0.5f, 2.0f) - 0.5f) / 1.5f) * SPEED_MAX).roundToInt().coerceIn(0, SPEED_MAX)
    }

    private fun progressToSpeed(progress: Int): Float {
        return 0.5f + (progress.coerceIn(0, SPEED_MAX) / SPEED_MAX.toFloat()) * 1.5f
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
        private const val SPEED_MAX = 100

        fun show(fragmentManager: FragmentManager) {
            TempoDialogFragment().show(fragmentManager, TempoDialogFragment::class.java.simpleName)
        }
    }
}
