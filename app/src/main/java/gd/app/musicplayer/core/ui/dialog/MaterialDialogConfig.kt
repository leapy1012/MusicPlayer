package gd.app.musicplayer.core.ui.dialog

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.theme.accentColor
import gd.app.musicplayer.core.theme.itemTextColor
import gd.app.musicplayer.core.theme.messageColor
import gd.app.musicplayer.core.theme.titleColor
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables

object MaterialDialogConfig {

    fun createMaterialListDialogConfig(
        context: Context,
        items: List<String>
    ): OptionsListDialog.Config {
        val palette = context.appDependencies.themeRepo.getCorePalette(context)
        val colors = DialogThemeColors.from(
            isDarkMode = palette.isDarkMode(),
            accentColor = palette.accentColor
        )

        return OptionsListDialog.Config.create(context, items).apply {
            backgroundDrawable = palette.getDialogBackground(context)
            dialogLayoutRes = R.layout.music_material_list_dialog_layout

            titleTextColor = palette.titleColor
            itemTextColor = palette.itemTextColor
            selectedItemTextColor = palette.accentColor

            dimAmount = DEFAULT_DIM_AMOUNT
            cancelable = true

            itemIconTintList = ViewStateDrawables.disabledSelectedDefaultColors(
                colors.disabledItemColor,
                palette.accentColor,
                colors.disabledItemColor
            )

            applyMaterialActionButtons(colors)
        }
    }

    fun createMaterialMessageDialogConfig(
        context: Context
    ): MessageDialog.Config {
        val palette = context.appDependencies.themeRepo.getCorePalette(context)
        val colors = DialogThemeColors.from(
            isDarkMode = palette.isDarkMode(),
            accentColor = palette.accentColor
        )

        return MessageDialog.Config.create(context).apply {
            backgroundDrawable = palette.getDialogBackground(context)
            dialogLayoutRes = R.layout.music_material_dialog_layout

            titleTextColor = palette.titleColor
            messageTextColor = palette.messageColor

            cancelable = true
            dimAmount = DEFAULT_DIM_AMOUNT

            applyMaterialActionButtons(colors)
        }
    }

    private fun OptionsListDialog.Config.applyMaterialActionButtons(
        colors: DialogThemeColors
    ) {
        positiveButtonTextColor = Color.WHITE
        negativeButtonTextColor = colors.secondaryButtonTextColor

        positiveButtonBackground = DrawableUtil.roundedRipple(
            colors.accentColor,
            colors.primaryRippleColor,
            DEFAULT_CORNER_RADIUS
        )

        negativeButtonBackground = DrawableUtil.roundedRipple(
            colors.secondaryRippleColor,
            colors.secondaryPressedColor,
            DEFAULT_CORNER_RADIUS
        )
    }

    private fun MessageDialog.Config.applyMaterialActionButtons(
        colors: DialogThemeColors
    ) {
        positiveButtonTextColor = Color.WHITE
        negativeButtonTextColor = colors.secondaryButtonTextColor
        neutralButtonTextColor = colors.secondaryButtonTextColor

        positiveButtonBackground = DrawableUtil.roundedRipple(
            colors.accentColor,
            colors.primaryRippleColor,
            DEFAULT_CORNER_RADIUS
        )

        val secondaryBackground = DrawableUtil.roundedRipple(
            colors.secondaryRippleColor,
            colors.secondaryPressedColor,
            DEFAULT_CORNER_RADIUS
        )

        negativeButtonBackground = secondaryBackground
        neutralButtonBackground = secondaryBackground
    }

    private data class DialogThemeColors(
        @param:ColorInt val accentColor: Int,
        @param:ColorInt val disabledItemColor: Int,
        @param:ColorInt val secondaryButtonTextColor: Int,
        @param:ColorInt val primaryRippleColor: Int,
        @param:ColorInt val secondaryRippleColor: Int,
        @param:ColorInt val secondaryPressedColor: Int
    ) {
        companion object {
            fun from(
                isDarkMode: Boolean,
                @ColorInt accentColor: Int
            ): DialogThemeColors {
                return DialogThemeColors(
                    accentColor = accentColor,
                    disabledItemColor = if (isDarkMode) {
                        -3355444
                    } else {
                        -2171170
                    },
                    secondaryButtonTextColor = if (isDarkMode) {
                        0xDE000000.toInt()
                    } else {
                        -855638017
                    },
                    primaryRippleColor = 872415231,
                    secondaryRippleColor = if (isDarkMode) {
                        218103808
                    } else {
                        234881023
                    },
                    secondaryPressedColor = if (isDarkMode) {
                        637534208
                    } else {
                        872415231
                    }
                )
            }
        }
    }

    private const val DEFAULT_DIM_AMOUNT = 0.5f
    private const val DEFAULT_CORNER_RADIUS = 1000f
}