package gd.app.musicplayer.core.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.tabs.TabLayout
import gd.app.lib.model.scan.MusicScanProgressView
import gd.app.lib.view.MaskImageView
import gd.app.lib.model.lrc.view.LyricView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables
import gd.app.musicplayer.core.ui.view.PlayStateView
import gd.app.musicplayer.core.ui.view.RecyclerIndexBar
import gd.app.musicplayer.core.ui.view.RotateStepBar
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.ui.feature.editor.waveform.SoundWaveView
import gd.app.musicplayer.ui.theme.ThemeTags

class DefaultThemeBinder : ThemeViewBinder {
    override fun bind(palette: ThemePalette, payload: Any?, view: View): Boolean {
        val tag = payload as? String ?: return false
        val accentColor = palette.accentColor
        val titleColor = palette.headerTitleColor
        val itemTextColor = palette.itemPrimaryTextColor
        val secondaryTextColor = ColorUtils.setAlphaComponent(itemTextColor, 180)
        val rippleColor = if (usesDarkForegroundPalette(palette)) 0x1A000000 else 0x26FFFFFF
        val contentOverlay = if (usesDarkForegroundPalette(palette)) 0 else 0x10000000
        val strongerOverlay = if (usesDarkForegroundPalette(palette)) 0x0D000000 else 0x1A000000

        when (tag) {
            ThemeTags.ACTIVITY_BACKGROUND -> {
                view.background = palette.getActivityBackgroundDrawable(view.context)
                return true
            }

            ThemeTags.BLUR_BACKGROUND -> {
                val drawable = palette.getBlurredBackgroundDrawable(view.context)
                view.background =
                    drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
                return true
            }

            ThemeTags.CONTENT_BACKGROUND, ThemeTags.BOTTOM_CONTENT -> {
                applyContentBackground(view, contentOverlay, rippleColor)
                return true
            }

            ThemeTags.TITLE_BACKGROUND_COLOR -> {
                view.setBackgroundColor(palette.headerOverlayColor)
                return true
            }

            ThemeTags.SCROLL_CONTENT -> {
                view.setBackgroundColor(if (usesDarkForegroundPalette(palette)) Color.TRANSPARENT else 0x1A000000)
                return true
            }

            ThemeTags.BOTTOM_CONTROL_BACKGROUND -> {
                view.setBackgroundColor(if (usesDarkForegroundPalette(palette)) 0x0D000000 else 0x1A000000)
                return true
            }

            ThemeTags.BOTTOM_ROOT_LAYOUT -> {
                view.setBackgroundColor(strongerOverlay)
                return true
            }

            ThemeTags.MAIN_BOTTOM_CONTROL -> {
                view.setBackgroundColor(strongerOverlay)
                return true
            }

            ThemeTags.SLEEP_CONTENT -> {
                view.background = DrawableUtil.roundedFill(
                    view.context.resources.displayMetrics.density * 4f,
                    if (usesDarkForegroundPalette(palette)) 0x0D000000 else 0x0DFFFFFF
                )
                return true
            }

            ThemeTags.DIALOG_SURFACE -> {
                view.background = palette.getDialogSurfaceDrawable(view.context)
                return true
            }

            ThemeTags.DIALOG_ITEM -> {
                applyTextOrIconColor(
                    view,
                    palette.dialogTitleColor,
                    palette.dialogPressedOverlayColor
                )
                return true
            }

            ThemeTags.DIALOG_ITEM_BACKGROUND -> {
                view.background =
                    DrawableUtil.rectRipple(Color.TRANSPARENT, palette.dialogPressedOverlayColor)
                return true
            }

            ThemeTags.DIALOG_TITLE_ICON, ThemeTags.DIALOG_VOLUME_ICON -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList.valueOf(palette.dialogTitleColor)
                    view.background = DrawableUtil.ovalRipple(
                        Color.TRANSPARENT,
                        palette.dialogPressedOverlayColor
                    )
                }
                return true
            }

            ThemeTags.DIALOG_FAVORITE -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                        intArrayOf(palette.accentColor, palette.dialogTitleColor)
                    )
                }
                return true
            }

            ThemeTags.DIALOG_SEEK_BAR -> {
                if (view is SeekBar) {
                    val backgroundColor = if (palette.isNightTheme()) 0x29000000 else 0x33FFFFFF
                    val radius = (view.context.resources.displayMetrics.density * 8f).toInt()
                    view.setProgressDrawable(
                        DrawableUtil.roundedProgress(backgroundColor, palette.accentColor, radius)
                    )
                    view.setThumbColor(palette.accentColor)
                }
                return true
            }

            ThemeTags.DIALOG_VOLUME_TEXT -> {
                if (view is TextView) {
                    view.setTextColor(palette.dialogSecondaryTextColor)
                }
                return true
            }

            ThemeTags.BUTTON_CONFIRM -> {
                view.background = DrawableUtil.roundedRipple(
                    accentColor,
                    0x33FFFFFF,
                    1000.0f
                )
            }

            ThemeTags.DIALOG_DIVIDER -> {
                view.setBackgroundColor(if (palette.isNightTheme()) 0x0D000000 else 0x0EFFFFFF)
                return true
            }

            ThemeTags.ITEM_BACKGROUND, ThemeTags.BOTTOM_MENU_ITEM -> {
                view.background = DrawableUtil.rectRipple(0, rippleColor)
                return true
            }

            ThemeTags.ITEM_BACKGROUND_COLOR -> {
                val radius = view.context.resources.displayMetrics.density * 16f
                val fillColor = contentOverlay
                view.background = if (view.isClickable) {
                    DrawableUtil.roundedRipple(fillColor, rippleColor, radius)
                } else {
                    DrawableUtil.roundedFill(radius, fillColor)
                }
                return true
            }

            ThemeTags.ACCENT_COLOR -> {
                if (view is ImageView) {
                    view.setImageDrawable(
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(accentColor)
                        }
                    )
                }
                return true
            }

            ThemeTags.THEME_COLOR -> {
                when (view) {
                    is PlayStateView -> view.setColor(accentColor)
                    is TextView -> view.setTextColor(accentColor)
                    is ImageView -> view.imageTintList = ColorStateList.valueOf(accentColor)
                }
                return true
            }

            ThemeTags.ACCENT_COLOR_BG -> {
                if (view is ImageView) {
                    view.setImageDrawable(
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setStroke(
                                (view.context.resources.displayMetrics.density * 2f).toInt(),
                                Color.WHITE
                            )
                        }
                    )
                }
                return true
            }

            ThemeTags.TITLE_COLOR -> {
                when (view) {
                    is ImageView -> {
                        view.imageTintList = ColorStateList.valueOf(titleColor)
                        if (view.isClickable) {
                            view.background = DrawableUtil.ovalRipple(0, rippleColor)
                        }
                    }

                    is TextView -> {
                        view.setTextColor(titleColor)
                        view.setHintTextColor(ColorUtils.setAlphaComponent(titleColor, 128))
                    }

                    else -> view.setBackgroundColor(titleColor)
                }
                return true
            }

            ThemeTags.EDIT_TEXT_BACKGROUND, ThemeTags.SLEEP_EDIT_TEXT, ThemeTags.EDIT_TEXT -> {
                val fillColor = if (usesDarkForegroundPalette(palette)) 335544320 else 352321535
                if (view is TextView && tag == ThemeTags.EDIT_TEXT) {
                    view.setTextColor(titleColor)
                    view.setHintTextColor(ColorUtils.setAlphaComponent(titleColor, 128))
                    val background = DrawableCompat.wrap(
                        view.background?.mutate()
                            ?: DrawableUtil.gradientDrawable(
                                view.context.resources.displayMetrics.density * 8f,
                                fillColor
                            ).mutate()
                    )
                    DrawableCompat.setTintList(
                        background,
                        ViewStateDrawables.focusedDefaultColors(
                            ColorUtils.setAlphaComponent(titleColor, 77),
                            accentColor
                        )
                    )
                    view.background = background
                } else {
                    view.background = DrawableUtil.gradientDrawable(
                        view.context.resources.displayMetrics.density * 8f,
                        fillColor
                    )
                }
                if (tag == ThemeTags.SLEEP_EDIT_TEXT && view is TextView) {
                    view.setTextColor(itemTextColor)
                    view.setHintTextColor(ColorUtils.setAlphaComponent(itemTextColor, 160))
                }
                return true
            }
        }

        if (tag == ThemeTags.TOOLBAR && view is Toolbar) {
            view.setTitleTextColor(titleColor)
            view.setSubtitleTextColor(ColorUtils.setAlphaComponent(titleColor, 180))
            view.navigationIcon?.setTint(titleColor)
            view.overflowIcon?.setTint(titleColor)
            for (index in 0 until view.menu.size()) {
                view.menu.getItem(index).icon?.setTint(titleColor)
            }
            return true
        }

        if (tag == ThemeTags.TOOLBAR_WHITE && view is Toolbar) {
            view.setTitleTextColor(Color.WHITE)
            view.setSubtitleTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 180))
            view.navigationIcon?.setTint(Color.WHITE)
            view.overflowIcon?.setTint(Color.WHITE)
            for (index in 0 until view.menu.size()) {
                view.menu.getItem(index).icon?.setTint(Color.WHITE)
            }
            return true
        }

        if ((tag == ThemeTags.TAB_LAYOUT || tag == ThemeTags.EQUALIZER_TAB_LAYOUT) && view is TabLayout) {
            view.setTabTextColors(itemTextColor, accentColor)
            view.setSelectedTabIndicatorColor(accentColor)
            if (tag == ThemeTags.EQUALIZER_TAB_LAYOUT) {
                val tabItemBackground = ViewStateDrawables.pressedDefaultColorDrawable(
                    Color.TRANSPARENT,
                    palette.headerPressedOverlayColor
                )
                (view.getChildAt(0) as? ViewGroup)?.let { tabStrip ->
                    for (index in 0 until tabStrip.childCount) {
                        tabStrip.getChildAt(index).background = tabItemBackground
                    }
                }
            }
            return true
        }

        if (tag == ThemeTags.ITEM_TEXT_COLOR || tag == ThemeTags.BOTTOM_MENU_TEXT) {
            applyTextOrIconColor(view, itemTextColor, rippleColor)
            return true
        }

        if (tag == ThemeTags.ITEM_TEXT_SECONDARY || tag == ThemeTags.FOLDER_FOOT_DES || tag == ThemeTags.SETTING_SUMMARY) {
            applyTextOrIconColor(view, secondaryTextColor, rippleColor)
            return true
        }

        if (
            tag == ThemeTags.RECYCLER_DIVIDER_COLOR ||
            tag == ThemeTags.BOTTOM_DIVIDER ||
            tag == ThemeTags.MUSIC_SELECT_TITLE_DIVIDER ||
            tag == ThemeTags.LINE_BACKGROUND ||
            tag == ThemeTags.SLEEP_DIVIDER_COLOR ||
            tag == ThemeTags.ITEM_DIVIDER ||
            tag == ThemeTags.UNDER_LINE
        ) {
            view.setBackgroundColor(ColorUtils.setAlphaComponent(itemTextColor, 36))
            return true
        }

        if (tag == ThemeTags.SHADOW_MARK || tag == ThemeTags.BOTTOM_SHADOW_MARK) {
            view.setBackgroundColor(contentOverlay)
            return true
        }

        if (tag == ThemeTags.EMPTY_BUTTON || tag == ThemeTags.THEME_STROKE_BUTTON) {
            if (view is TextView) {
                view.setTextColor(accentColor)
                view.background = DrawableUtil.outlinedRoundedRipple(
                    (view.context.resources.displayMetrics.density * 100f).toInt(),
                    (view.context.resources.displayMetrics.density * 1f).toInt(),
                    ColorUtils.setAlphaComponent(itemTextColor, 51),
                    0,
                    rippleColor
                )
            }
            return true
        }

        if (tag == ThemeTags.SAVE_BUTTON) {
            view.background = DrawableUtil.roundedRipple(
                accentColor,
                0x26FFFFFF,
                view.context.resources.displayMetrics.density * 100f
            )
            return true
        }

        if (tag == ThemeTags.LOADING_PROGRESS_BAR || tag == ThemeTags.PROGRESS_BAR) {
            if (view is ProgressBar) {
                view.indeterminateTintList = ColorStateList.valueOf(accentColor)
                view.progressTintList = ColorStateList.valueOf(accentColor)
            }
            return true
        }

        if (tag == ThemeTags.MUSIC_SCAN_PROGRESS_VIEW && view is MusicScanProgressView) {
            view.setColor(accentColor)
            return true
        }

        if (tag == ThemeTags.SOUND_WAVE_VIEW && view is SoundWaveView) {
            view.setWaveColor(itemTextColor)
            view.setBaseLineColor(ColorUtils.setAlphaComponent(itemTextColor, 180))
            view.setClipColor(ColorUtils.setAlphaComponent(itemTextColor, 220))
            view.setProgressLineColor(accentColor)
            view.setOverlayColor(ColorUtils.setAlphaComponent(Color.BLACK, 42))
            view.setOverlaySelectColor(Color.TRANSPARENT)
            return true
        }

        if (tag == ThemeTags.SOUND_INFO_VIEW && view is TextView) {
            view.setTextColor(ColorUtils.setAlphaComponent(itemTextColor, 180))
            return true
        }

        if (tag == ThemeTags.LYRIC_VIEW && view is LyricView) {
            view.setTextColor(titleColor)
            return true
        }

        if ((tag == ThemeTags.SEEK_BAR || tag == ThemeTags.EQUALIZER_SEEK_BAR) && view is SeekBar) {
            if (tag == ThemeTags.EQUALIZER_SEEK_BAR) {
                val disabledColor = view.context.getColor(R.color.equalizer_disable_color)
                val backgroundColor = view.context.getColor(R.color.equalizer_background_color)
                val cornerRadius = view.context.resources.displayMetrics.density * 2f
                view.setThumbOverlayColor(
                    ViewStateDrawables.enabledDisabledColors(accentColor, disabledColor)
                )
                view.setProgressDrawable(
                    DrawableUtil.layeredProgress(
                        DrawableUtil.gradientDrawable(cornerRadius, backgroundColor),
                        ViewStateDrawables.defaultWithDisabledDrawable(
                            DrawableUtil.gradientDrawable(cornerRadius, accentColor),
                            DrawableUtil.gradientDrawable(cornerRadius, disabledColor)
                        )
                    )
                )
            } else {
                view.setThumbColor(accentColor)
                view.setProgressDrawable(
                    DrawableUtil.roundedProgress(
                        if (usesDarkForegroundPalette(palette)) 0x26000000 else -2130706433,
                        accentColor,
                        (view.context.resources.displayMetrics.density * 20f).toInt()
                    )
                )
            }
            return true
        }

        if (tag == ThemeTags.GROUP_SEEK && view is SeekBar) {
            view.setThumbColor(accentColor)
            view.setProgressDrawable(
                DrawableUtil.roundedProgress(
                    ColorUtils.setAlphaComponent(Color.WHITE, 128),
                    accentColor,
                    (view.context.resources.displayMetrics.density * 10f).toInt()
                )
            )
            return true
        }

        if (tag == ThemeTags.EQUALIZER_SELECT_BOX && view is ImageView) {
            val toggleOffDrawable =
                AppCompatResources.getDrawable(view.context, R.drawable.equalizer_toggle_off)
            val toggleOnDrawable =
                AppCompatResources.getDrawable(view.context, R.drawable.equalizer_toggle_on)
            val toggleOverlayDrawable = DrawableUtil.tinted(
                AppCompatResources.getDrawable(
                    view.context,
                    R.drawable.equalizer_toggle_on_overlay
                ),
                accentColor
            )

            view.setImageDrawable(
                ViewStateDrawables.buildStateDrawable(
                    toggleOffDrawable,
                    LayerDrawable(
                        listOfNotNull(
                            toggleOnDrawable,
                            toggleOverlayDrawable
                        ).toTypedArray()
                    ),
                    null
                )
            )
            return true
        }

        if (tag == ThemeTags.EQUALIZER_ROTATE_STEP_BAR && view is RotateStepBar) {
            val disabledColor = view.context.getColor(R.color.equalizer_disable_color)
            val backgroundColor = view.context.getColor(R.color.equalizer_background_color)
            val tintList = ViewStateDrawables.disabledSelectedDefaultColors(
                backgroundColor,
                accentColor,
                disabledColor
            )
            view.setIndicatorOverlayTintList(
                ViewStateDrawables.enabledDisabledColors(accentColor, disabledColor)
            )
            view.setPrimaryGraduationTintList(tintList)
            view.setSecondaryGraduationTintList(tintList)
            return true
        }

        if ((tag == ThemeTags.BACK_BUTTON || tag == ThemeTags.CONTROL_BUTTON) && view is ImageView) {
            view.imageTintList = ColorStateList.valueOf(itemTextColor)
            view.background = DrawableUtil.ovalRipple(0, rippleColor)
            return true
        }

        if (tag == ThemeTags.PLAY_PAUSE_BUTTON && view is ImageView) {
            view.imageTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(accentColor, ColorUtils.setAlphaComponent(itemTextColor, 204))
            )
            return true
        }

        if (
            (tag == ThemeTags.BOTTOM_MENU_ICON ||
                    tag == ThemeTags.FOLDER_FOOT_SCAN_ICON ||
                    tag == ThemeTags.ITEM_FAVORITE ||
                    tag == ThemeTags.EQUALIZER_ICON ||
                    tag == ThemeTags.GROUP_SELECT_BOX) &&
            view is ImageView
        ) {
            view.imageTintList = if (tag == ThemeTags.EQUALIZER_ICON) {
                ViewStateDrawables.disabledSelectedDefaultColors(
                    Color.WHITE,
                    accentColor,
                    view.context.getColor(R.color.equalizer_disable_color)
                )
            } else if (tag == ThemeTags.GROUP_SELECT_BOX) {
                ViewStateDrawables.selectedDefaultColors(
                    0xB3FFFFFF.toInt(),
                    accentColor
                )
            } else {
                ColorStateList.valueOf(itemTextColor)
            }
            return true
        }

        if (tag == ThemeTags.BANNER_IMAGE && view is ImageView) {
            view.imageTintList =
                ColorStateList.valueOf(if (usesDarkForegroundPalette(palette)) 0x66000000 else Color.WHITE)
            return true
        }

        if (tag == ThemeTags.ITEM_ADD) {
            when (view) {
                is TextView -> {
                    view.setTextColor(accentColor)
                    view.background = DrawableUtil.outlinedRoundedRipple(
                        (view.context.resources.displayMetrics.density * 100f).toInt(),
                        (view.context.resources.displayMetrics.density * 1.5f).toInt(),
                        accentColor,
                        ColorUtils.setAlphaComponent(accentColor, 20),
                        rippleColor
                    )
                }

                is ImageView -> view.imageTintList = ColorStateList.valueOf(itemTextColor)
            }
            return true
        }

        if (tag == ThemeTags.FAVORITE && view is ImageView) {
            view.imageTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(accentColor, itemTextColor)
            )
            view.background = DrawableUtil.ovalRipple(0, rippleColor)
            return true
        }

        if (tag == ThemeTags.FOLDER_FOOT_SCAN_BG) {
            view.background = DrawableUtil.roundedRipple(
                accentColor,
                rippleColor,
                view.context.resources.displayMetrics.density * 100f
            )
            return true
        }

        if (tag == ThemeTags.FOLDER_FOOT_SCAN_TEXT && view is TextView) {
            view.setTextColor(titleColor)
            return true
        }

        if (tag == ThemeTags.SELECT_ALL || tag == ThemeTags.SELECT_BOX || tag == ThemeTags.SELECT_BOX_2) {
            if (view is ImageView) {
                if (view.isClickable) {
                    view.background = DrawableUtil.ovalRipple(0, rippleColor)
                }
                view.imageTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_selected),
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(android.R.attr.state_activated),
                        intArrayOf()
                    ),
                    intArrayOf(
                        accentColor,
                        accentColor,
                        accentColor,
                        ColorUtils.setAlphaComponent(itemTextColor, 180)
                    )
                )
            }
            return true
        }

        if (tag == ThemeTags.BANNER_IMAGE_BACKGROUND) {
            if (view is MaskImageView) {
                view.setMaskColor(if (usesDarkForegroundPalette(palette)) 0x33000000 else 0x4D000000)
            }
            if (view is ImageView) {
                view.imageTintList =
                    if (usesDarkForegroundPalette(palette)) ColorStateList.valueOf(0x1A000000) else null
            }
            return true
        }

        if (tag == ThemeTags.RECYCLER_INDEX_BAR) {
            if (view is RecyclerIndexBar) {
                view.setTextColor(accentColor)
            } else {
                view.setBackgroundColor(ColorUtils.setAlphaComponent(itemTextColor, 36))
            }
            return true
        }

        if (tag == ThemeTags.REVERB_ITEM) {
            val baseDrawable =
                AppCompatResources.getDrawable(view.context, R.drawable.equalizer_button)
            val selectedOverlay = DrawableUtil.tinted(
                AppCompatResources.getDrawable(view.context, R.drawable.equalizer_button_select),
                ColorUtils.setAlphaComponent(accentColor, 204)
            )
            view.background = ViewStateDrawables.buildStateDrawable(
                baseDrawable,
                LayerDrawable(listOfNotNull(baseDrawable, selectedOverlay).toTypedArray()),
                null
            )
            return true
        }

        if (tag == ThemeTags.EFFECT_STROKE) {
            view.background = GradientDrawable().apply {
                setStroke(
                    (view.context.resources.displayMetrics.density * 1.5f).toInt(),
                    accentColor
                )
                cornerRadius = view.context.resources.displayMetrics.density * 10f
            }
            return true
        }

        if (tag == ThemeTags.GROUP_EFFECT_TEXT && view is TextView) {
            view.setTextColor(
                ViewStateDrawables.selectedDefaultColors(
                    0xB3FFFFFF.toInt(),
                    accentColor
                )
            )
            return true
        }

        if (tag == ThemeTags.GROUP_BOOST && view is TextView) {
            view.setTextColor(
                ViewStateDrawables.selectedDefaultColors(
                    0xB3FFFFFF.toInt(),
                    accentColor
                )
            )
            val radius = view.context.resources.displayMetrics.density * 50f
            val strokeWidth = (view.context.resources.displayMetrics.density * 1.5f).toInt()
            val fillColor = 0x1AFFFFFF
            view.background = ViewStateDrawables.buildStateDrawable(
                DrawableUtil.outlinedRoundedRipple(
                    cornerRadius = radius.toInt(),
                    strokeWidth = strokeWidth,
                    strokeColor = 0xB3FFFFFF.toInt(),
                    fillColor = fillColor,
                    rippleColor = fillColor
                ),
                DrawableUtil.outlinedRoundedRipple(
                    cornerRadius = radius.toInt(),
                    strokeWidth = strokeWidth,
                    strokeColor = accentColor,
                    fillColor = fillColor,
                    rippleColor = fillColor
                ),
                null
            )
            return true
        }

        return false
    }

    private fun applyContentBackground(view: View, fillColor: Int, rippleColor: Int) {
        if (view.isClickable) {
            view.background = DrawableUtil.rectRipple(fillColor, rippleColor)
        } else {
            view.setBackgroundColor(fillColor)
        }
    }

    private fun applyTextOrIconColor(view: View, color: Int, rippleColor: Int) {
        when (view) {
            is ImageView -> {
                view.imageTintList = ColorStateList.valueOf(color)
                if (view.isClickable) {
                    view.background = DrawableUtil.ovalRipple(0, rippleColor)
                }
            }

            is TextView -> {
                view.setTextColor(color)
                view.setHintTextColor(ColorUtils.setAlphaComponent(color, 128))
            }
        }
    }

    private fun usesDarkForegroundPalette(palette: ThemePalette): Boolean =
        palette.headerTitleColor != Color.WHITE
}
