package gd.app.musicplayer.ui.setting

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import gd.app.musicplayer.databinding.DialogSimultaneousTipBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.lib.view.RoundedOutlineProvider
import gd.app.lib.view.square.HeightFromWidthMeasurePolicy
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.designsystem.drawable.DrawableUtil
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.messageColor

class SimultaneousTipDialog : BaseDialogFragment() {

    private var _binding: DialogSimultaneousTipBinding? = null
    private val binding: DialogSimultaneousTipBinding
        get() = requireNotNull(_binding)

    companion object {
        fun newInstance(): SimultaneousTipDialog = SimultaneousTipDialog()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSimultaneousTipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cornerRadiusPx = requireContext().dpToPx(12f)
        binding.root.clipToOutline = true
        binding.root.outlineProvider = RoundedOutlineProvider(cornerRadiusPx.toFloat())

        binding.dialogButtonOk.setOnClickListener { dismiss() }
        binding.dialogMessage.text = buildDialogMessage()

        applyThemeToViews(cornerRadiusPx.toFloat())
        updateTitleImageShape(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTitleImageShape(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun applyThemeToViews(topCornerRadius: Float) {
        val themePalette = currentTheme()

        binding.root.background = themePalette.getDialogBackground(requireContext())
        binding.dialogMessage.setTextColor(themePalette.messageColor)
        binding.dialogButtonOk.background = DrawableUtil.roundedRipple(
            themePalette.accentColor,
            452984831,
            requireContext().dpToPx(1000f).toFloat()
        )

        binding.dialogTitle.background = GradientDrawable().apply {
            setColor(themePalette.accentColor)
            cornerRadii = floatArrayOf(
                topCornerRadius, topCornerRadius,
                topCornerRadius, topCornerRadius,
                0f, 0f,
                0f, 0f
            )
        }
    }

    private fun buildDialogMessage(): String {
        val activity = requireActivity() as BaseActivity

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buildString {
                append("1、")
                append(activity.getString(R.string.simultaneous_play_dialog_msg_1))
                append('\n')
                append("2、")
                append(activity.getString(R.string.simultaneous_play_dialog_msg_2))
            }
        } else {
            activity.getString(R.string.simultaneous_play_dialog_msg_2)
        }
    }

    private fun updateTitleImageShape(isLandscape: Boolean) {
        binding.dialogTitle.setSquare(
            HeightFromWidthMeasurePolicy(
                ratio = if (isLandscape) 0.24f else 0.3f
            )
        )

    }
}
