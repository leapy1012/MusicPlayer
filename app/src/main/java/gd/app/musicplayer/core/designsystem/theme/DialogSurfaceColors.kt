package gd.app.musicplayer.core.designsystem.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import gd.app.musicplayer.core.designsystem.drawable.roundedProgressDrawable
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.designsystem.view.SelectBox

/**
 * Content colors for dialog / bottom-sheet plates.
 *
 * Activity may overlay [Theme_COUI_Main_Dark], so theme attrs like LabelPrimary can
 * stay white even when [ThemePalette.getDialogSurfaceDrawable] is a light plate.
 * Resolve from [ThemePalette.isDialogSurfaceLight] instead.
 */
object DialogSurfaceColors {

    fun contentColor(palette: ThemePalette): Int {
        val lightPlate = usesLightPlate(palette)
        return if (lightPlate) {
            val candidate = palette.dialogTitleColor
            if (isVisuallyWhite(candidate)) 0xDE000000.toInt() else candidate
        } else {
            Color.WHITE
        }
    }

    fun usesLightPlate(palette: ThemePalette): Boolean {
        return palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT ||
            palette.isDialogSurfaceLight()
    }

    fun isVisuallyWhite(color: Int): Boolean {
        return Color.red(color) >= 0xF0 &&
            Color.green(color) >= 0xF0 &&
            Color.blue(color) >= 0xF0
    }

    /**
     * Tempo / lyric sheets omit `seekProgressDrawable` in XML, so tracks never draw.
     * Paint a plate-aware track + accent fill (same recipe as [DefaultThemeBinder]).
     */
    fun paintSeekBar(seekBar: SeekBar, palette: ThemePalette) {
        val lightPlate = usesLightPlate(palette)
        val accent = palette.accentColor
        val track = if (lightPlate) 0x26000000 else 0x40FFFFFF
        val radius = (seekBar.resources.displayMetrics.density * 20f).toInt()
        seekBar.setProgressDrawable(
            roundedProgressDrawable(
                backgroundColor = track,
                progressColor = accent,
                cornerRadius = radius,
            ),
        )
        seekBar.setThumbColor(if (lightPlate) accent else Color.WHITE)
    }

    /**
     * Paints labels/icons under [root] for the current dialog plate.
     * [SelectBox] toggles are white vectors — tint only on light plates.
     * Skip any subtree matching [skip].
     */
    fun paintContent(
        root: View?,
        palette: ThemePalette,
        skip: ((View) -> Boolean)? = null,
    ) {
        if (root == null) return
        val colors = ColorStateList.valueOf(contentColor(palette))
        val lightPlate = usesLightPlate(palette)
        paintTree(root, colors, lightPlate, skip)
    }

    private fun paintTree(
        view: View,
        colors: ColorStateList,
        lightPlate: Boolean,
        skip: ((View) -> Boolean)?,
    ) {
        if (skip?.invoke(view) == true) return
        when (view) {
            is SelectBox -> {
                ImageViewCompat.setImageTintList(view, if (lightPlate) colors else null)
            }
            is SeekBar -> {
                // Progress/thumb handled by [paintSeekBar]; don't treat as ImageView.
            }
            is TextView -> {
                view.setTextColor(colors)
                TextViewCompat.setCompoundDrawableTintList(view, colors)
            }
            is ImageView -> ImageViewCompat.setImageTintList(view, colors)
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    paintTree(view.getChildAt(index), colors, lightPlate, skip)
                }
            }
        }
    }
}
