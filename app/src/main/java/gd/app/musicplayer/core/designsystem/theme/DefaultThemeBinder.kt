package gd.app.musicplayer.core.designsystem.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.tabs.TabLayout
import gd.app.lib.model.lrc.view.LyricView
import gd.app.lib.model.scan.MusicScanProgressView
import gd.app.lib.view.MaskImageView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.designsystem.drawable.defaultWithDisabledDrawable
import gd.app.musicplayer.core.designsystem.drawable.disabledSelectedDefaultColors
import gd.app.musicplayer.core.designsystem.drawable.enabledDisabledColors
import gd.app.musicplayer.core.designsystem.drawable.focusedDefaultColors
import gd.app.musicplayer.core.designsystem.drawable.layeredProgressDrawable
import gd.app.musicplayer.core.designsystem.drawable.outlinedRoundedRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.ovalRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.pressedDefaultColorDrawable
import gd.app.musicplayer.core.designsystem.drawable.pressedDefaultColors
import gd.app.musicplayer.core.designsystem.drawable.rectRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.roundedDrawable
import gd.app.musicplayer.core.designsystem.drawable.roundedProgressDrawable
import gd.app.musicplayer.core.designsystem.drawable.roundedRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.selectedDefaultColors
import gd.app.musicplayer.core.designsystem.drawable.stateDrawable
import gd.app.musicplayer.core.designsystem.drawable.tinted
import gd.app.musicplayer.core.designsystem.view.PlayStateView
import gd.app.musicplayer.core.designsystem.view.RecyclerIndexBar
import gd.app.musicplayer.core.designsystem.view.RotateStepBar
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.ui.editor.waveform.SoundWaveView
import gd.app.musicplayer.ui.theme.ThemeTags

class DefaultThemeBinder : ThemeViewBinder {

    override fun bind(
        palette: ThemePalette,
        payload: Any?,
        view: View,
    ): Boolean {
        val tag = payload as? String ?: return false
        val theme = ThemeBindContext.from(palette)

        return bindSurface(tag, palette, theme, view) ||
                bindDialog(tag, palette, theme, view) ||
                bindNavigation(tag, palette, theme, view) ||
                bindTextAndIcons(tag, palette, theme, view) ||
                bindInputs(tag, palette, theme, view) ||
                bindButtons(tag, palette, theme, view) ||
                bindProgress(tag, palette, theme, view) ||
                bindEqualizer(tag, palette, theme, view) ||
                bindMediaAndSelection(tag, palette, theme, view) ||
                bindMisc(tag, palette, theme, view)
    }

    private fun bindSurface(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Core.ACTIVITY_BACKGROUND -> {
                view.background = palette.getActivityBackgroundDrawable(view.context)
                return true
            }

            ThemeTags.Core.BLUR_BACKGROUND -> {
                val drawable = palette.getBlurredBackgroundDrawable(view.context)
                view.background = drawable.constantState?.newDrawable()?.mutate()
                    ?: drawable.mutate()
                return true
            }

            ThemeTags.Core.CONTENT_BACKGROUND -> {
                applyContentBackground(view, theme.contentOverlay, theme.rippleColor)
                return true
            }

            ThemeTags.Core.TITLE_BACKGROUND_COLOR -> {
                view.setBackgroundColor(palette.headerOverlayColor)
                return true
            }

            ThemeTags.Core.SCROLL_CONTENT -> {
                view.setBackgroundColor(
                    if (palette.isNightTheme()) Color.TRANSPARENT else 0x1A000000,
                )
                return true
            }

            ThemeTags.Core.BOTTOM_CONTROL_BACKGROUND -> {
                view.setBackgroundColor(
                    if (theme.usesDarkForeground) 0x0D000000 else 0x1A000000,
                )
                return true
            }

            ThemeTags.Misc.SETTING_CONTENT -> {
                view.setBackgroundColor(
                    if (theme.usesDarkForeground) 0xFFF5F5F5.toInt() else Color.TRANSPARENT,
                )
                return true
            }

            ThemeTags.Misc.SETTING_GROUP -> {
                view.background = roundedDrawable(
                    cornerRadius = view.context.dpToPx(6f).toFloat(),
                    fillColor = if (theme.usesDarkForeground) Color.WHITE else 0x10FFFFFF,
                )
                return true
            }

            ThemeTags.Core.MAIN_BOTTOM_CONTROL -> {
                view.setBackgroundColor(theme.strongerOverlay)
                return true
            }

            ThemeTags.Misc.SLEEP_CONTENT -> {
                view.background = roundedDrawable(
                    cornerRadius = view.context.dpToPx(4f).toFloat(),
                    fillColor = if (theme.usesDarkForeground) 0x0D000000 else 0x0DFFFFFF,
                )
                return true
            }

            ThemeTags.Dialog.SURFACE -> {
                view.background = palette.getDialogSurfaceDrawable(view.context)
                return true
            }

            ThemeTags.Dialog.BOTTOM_SURFACE -> {
                view.background = if (palette is PictureThemePalette) {
                    palette.getBottomDialogSurfaceDrawable(view.context)
                } else {
                    palette.getDialogSurfaceDrawable(view.context)
                }
                return true
            }
        }

        return false
    }

    private fun bindDialog(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Dialog.TITLE,
            ThemeTags.Dialog.TITLE_COLOR -> {
                applyDialogTitleColor(view, palette.dialogTitleColor)
                return true
            }

            ThemeTags.Dialog.ITEM -> {
                applyTextOrIconColor(
                    view = view,
                    color = palette.dialogTitleColor,
                    rippleColor = palette.dialogPressedOverlayColor,
                )
                return true
            }

            ThemeTags.Dialog.ITEM_BACKGROUND -> {
                view.background = rectRippleDrawable(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = palette.dialogPressedOverlayColor,
                )
                return true
            }

            ThemeTags.Dialog.EDIT_TEXT_BACKGROUND -> {
                view.background = dialogEditTextBackground(
                    strokeColor = if (theme.usesDarkForeground) 0x1A000000 else 0x26FFFFFF,
                    density = view.context.resources.displayMetrics.density,
                )
                return true
            }

            ThemeTags.Dialog.TITLE_ICON,
            ThemeTags.Dialog.VOLUME_ICON,
            ThemeTags.Dialog.SIZE_BUTTON,
            ThemeTags.Dialog.ARROW -> {
                if (view is ImageView) {
                    view.imageTintList = if (tag == ThemeTags.Dialog.ARROW) {
                        disabledSelectedDefaultColors(
                            defaultColor = palette.dialogTitleColor,
                            selectedColor = palette.dialogTitleColor,
                            disabledColor = ColorUtils.setAlphaComponent(
                                palette.dialogTitleColor,
                                DISABLED_ALPHA,
                            ),
                        )
                    } else {
                        ColorStateList.valueOf(palette.dialogTitleColor)
                    }

                    view.background = ovalRippleDrawable(
                        fillColor = Color.TRANSPARENT,
                        rippleColor = palette.dialogPressedOverlayColor,
                    )
                }
                return true
            }

            ThemeTags.Dialog.IMAGE_THEME_BUTTON -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList.valueOf(theme.accentColor)
                    view.background = ovalRippleDrawable(
                        fillColor = Color.TRANSPARENT,
                        rippleColor = palette.dialogPressedOverlayColor,
                    )
                }
                return true
            }

            ThemeTags.Dialog.IMAGE -> {
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(theme.accentColor)

                    val radius = view.context.resources.displayMetrics.density * 12f
                    cornerRadii = floatArrayOf(
                        radius, radius,
                        radius, radius,
                        0f, 0f,
                        0f, 0f,
                    )
                }
                return true
            }

            ThemeTags.Dialog.FAVORITE -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_selected),
                            intArrayOf(),
                        ),
                        intArrayOf(
                            palette.accentColor,
                            palette.dialogTitleColor,
                        ),
                    )
                }
                return true
            }

            ThemeTags.Dialog.SEEK_BAR,
            ThemeTags.Lyric.SETTINGS_SEEK -> {
                if (view is SeekBar) {
                    val backgroundColor = ColorUtils.setAlphaComponent(
                        palette.dialogTitleColor,
                        DISABLED_ALPHA,
                    )
                    val radius = (view.context.resources.displayMetrics.density * 8f).toInt()

                    view.setProgressDrawable(
                        roundedProgressDrawable(
                            backgroundColor = backgroundColor,
                            progressColor = palette.accentColor,
                            cornerRadius = radius,
                        ),
                    )
                    view.setThumbColor(palette.accentColor)
                }
                return true
            }

            ThemeTags.Dialog.VOLUME_TEXT -> {
                if (view is TextView) {
                    view.setTextColor(palette.dialogSecondaryTextColor)
                }
                return true
            }

            ThemeTags.Dialog.CONFIRM -> {
                bindConfirmButton(view, palette, theme)
                return true
            }

            ThemeTags.Dialog.BUTTON -> {
                if (view is TextView) {
                    view.setTextColor(theme.accentColor)
                }
                view.background = rectRippleDrawable(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = palette.dialogPressedOverlayColor,
                )
                return true
            }

            ThemeTags.Dialog.CANCEL,
            ThemeTags.Dialog.DELETE_BUTTON -> {
                bindDialogNeutralButton(view, palette, tag)
                return true
            }

            ThemeTags.Dialog.RESTORE_BUTTON -> {
                if (view is TextView) {
                    view.setTextColor(Color.WHITE)
                }
                view.background = roundedRippleDrawable(
                    fillColor = theme.accentColor,
                    rippleColor = 0x26FFFFFF,
                    cornerRadius = FULL_ROUND_RADIUS,
                )
                return true
            }

            ThemeTags.Dialog.DIVIDER,
            ThemeTags.Dialog.DIVIDER_COLOR -> {
                view.setBackgroundColor(
                    if (palette.isNightTheme()) 0x0D000000 else 0x0EFFFFFF,
                )
                return true
            }

            ThemeTags.Dialog.MESSAGE,
            ThemeTags.Dialog.MESSAGE_COLOR -> {
                applyTextOrIconColor(
                    view = view,
                    color = palette.dialogSecondaryTextColor,
                    rippleColor = palette.dialogPressedOverlayColor,
                )
                return true
            }

            ThemeTags.Dialog.CONTENT_MESSAGE -> {
                if (view is TextView) {
                    view.setTextColor(
                        if (palette.isDialogSurfaceLight()) {
                            0xFF303030.toInt()
                        } else {
                            Color.WHITE
                        },
                    )
                }
                return true
            }

            ThemeTags.Dialog.SELECT_BOX -> {
                if (view is ImageView) {
                    val normalColor = if (palette.isDialogSurfaceLight()) {
                        -3355444
                    } else {
                        -2171170
                    }

                    view.imageTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(-android.R.attr.state_enabled),
                            intArrayOf(
                                android.R.attr.state_selected,
                                android.R.attr.state_enabled,
                            ),
                            intArrayOf(android.R.attr.state_enabled),
                        ),
                        intArrayOf(
                            normalColor,
                            theme.accentColor,
                            normalColor,
                        ),
                    )
                }
                return true
            }

            ThemeTags.Dialog.EDIT_TEXT -> {
                if (view is EditText) {
                    bindDialogEditText(view, palette, theme)
                }
                return true
            }

            ThemeTags.Dialog.LYRIC_BUTTON -> {
                if (view is TextView) {
                    view.setTextColor(palette.dialogTitleColor)
                    tintCompoundDrawables(view, palette.dialogTitleColor)
                }
                view.background = rectRippleDrawable(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = palette.dialogPressedOverlayColor,
                )
                return true
            }
        }

        return false
    }

    private fun bindNavigation(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when {
            tag == ThemeTags.Navigation.TOOLBAR && view is Toolbar -> {
                bindToolbar(view, theme.titleColor)
                return true
            }

            tag == ThemeTags.Navigation.TOOLBAR_WHITE && view is Toolbar -> {
                bindToolbar(view, Color.WHITE)
                return true
            }

            (tag == ThemeTags.Navigation.TAB_LAYOUT ||
                    tag == ThemeTags.Equalizer.TAB_LAYOUT) && view is TabLayout -> {
                view.setTabTextColors(theme.itemTextColor, theme.accentColor)
                view.setSelectedTabIndicatorColor(theme.accentColor)

                if (tag == ThemeTags.Equalizer.TAB_LAYOUT) {
                    applyEqualizerTabBackgrounds(view, palette)
                }
                return true
            }

            tag == ThemeTags.Navigation.RECYCLER_INDEX_BAR -> {
                if (view is RecyclerIndexBar) {
                    view.setTextColor(theme.accentColor)
                } else {
                    view.setBackgroundColor(
                        ColorUtils.setAlphaComponent(theme.itemTextColor, DIVIDER_ALPHA),
                    )
                }
                return true
            }
        }

        return false
    }

    private fun bindTextAndIcons(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Text.ITEM_TEXT_COLOR,
            ThemeTags.Text.BOTTOM_MENU_TEXT -> {
                applyTextOrIconColor(view, theme.itemTextColor, theme.rippleColor)
                return true
            }

            ThemeTags.Text.ITEM_TEXT_SECONDARY,
            ThemeTags.Text.FOLDER_FOOT_DES,
            ThemeTags.Text.SETTING_SUMMARY -> {
                applyTextOrIconColor(view, theme.secondaryTextColor, theme.rippleColor)
                return true
            }

            ThemeTags.Text.SPEED_ITEM_DESCRIPTION -> {
                if (view is TextView) {
                    view.setTextColor(ColorUtils.setAlphaComponent(palette.titleColor, 160))
                }
                return true
            }

            ThemeTags.Text.SPEED_ITEM_TEXT -> {
                if (view is TextView) {
                    bindSpeedItemText(view, palette)
                }
                return true
            }

            ThemeTags.Text.SOUND_INFO_VIEW -> {
                if (view is TextView) {
                    view.setTextColor(
                        ColorUtils.setAlphaComponent(theme.itemTextColor, TEXT_SECONDARY_ALPHA),
                    )
                }
                return true
            }

            ThemeTags.Folder.FOLDER_FOOT_SCAN_TEXT -> {
                if (view is TextView) {
                    view.setTextColor(theme.titleColor)
                }
                return true
            }

            ThemeTags.Color.THEME_COLOR -> {
                when (view) {
                    is PlayStateView -> view.setColor(theme.accentColor)
                    is TextView -> view.setTextColor(theme.accentColor)
                    is ImageView -> view.imageTintList = ColorStateList.valueOf(theme.accentColor)
                }
                return true
            }

            ThemeTags.Color.TITLE_COLOR -> {
                bindTitleColor(view, theme)
                return true
            }
        }

        return false
    }

    private fun bindInputs(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Input.EDIT_TEXT_BACKGROUND,
            ThemeTags.Input.SLEEP_EDIT_TEXT,
            ThemeTags.Input.EDIT_TEXT -> {
                val fillColor = if (theme.usesDarkForeground) 335544320 else 352321535

                if (view is TextView && tag == ThemeTags.Input.EDIT_TEXT) {
                    bindEditableTextBackground(
                        textView = view,
                        textColor = theme.titleColor,
                        accentColor = theme.accentColor,
                        fillColor = fillColor,
                    )
                } else {
                    view.background = roundedDrawable(
                        cornerRadius = view.context.resources.displayMetrics.density * 8f,
                        fillColor = fillColor,
                    )
                }

                if (tag == ThemeTags.Input.SLEEP_EDIT_TEXT && view is TextView) {
                    view.setTextColor(theme.itemTextColor)
                    view.setHintTextColor(
                        ColorUtils.setAlphaComponent(theme.itemTextColor, 160),
                    )
                }

                return true
            }
        }

        return false
    }

    private fun bindButtons(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Item.EMPTY_BUTTON,
            ThemeTags.Item.THEME_STROKE_BUTTON,
            ThemeTags.Misc.SCAN_BUTTON -> {
                if (view is TextView) {
                    view.setTextColor(theme.itemTextColor)
                    tintCompoundDrawables(view, theme.itemTextColor)
                    view.background = outlinedRoundedRippleDrawable(
                        cornerRadius = view.context.dpToPx(100f),
                        strokeWidth = view.context.dpToPx(1.5f),
                        strokeColor = theme.accentColor,
                        rippleColor = theme.contentOverlay,
                    )
                }
                return true
            }

            ThemeTags.Item.SAVE_BUTTON -> {
                view.background = roundedRippleDrawable(
                    fillColor = theme.accentColor,
                    rippleColor = 0x26FFFFFF,
                    cornerRadius = view.context.resources.displayMetrics.density * 100f,
                )
                return true
            }

            ThemeTags.Item.BACK_BUTTON,
            ThemeTags.Item.CONTROL_BUTTON -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList.valueOf(theme.itemTextColor)
                    view.background = ovalRippleDrawable(
                        fillColor = Color.TRANSPARENT,
                        rippleColor = theme.rippleColor,
                    )
                }
                return true
            }

            ThemeTags.Item.PLAY_PAUSE_BUTTON -> {
                if (view is ImageView) {
                    view.imageTintList = pressedDefaultColors(
                        defaultColor = if (theme.usesDarkForeground) {
                            theme.accentColor
                        } else {
                            theme.itemTextColor
                        },
                        pressedColor = ColorUtils.setAlphaComponent(
                            theme.itemTextColor,
                            204,
                        ),
                    )
                }
                return true
            }
        }

        return false
    }

    private fun bindProgress(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Progress.LOADING_PROGRESS_BAR,
            ThemeTags.Progress.PROGRESS_BAR -> {
                if (view is ProgressBar) {
                    view.indeterminateTintList = ColorStateList.valueOf(theme.accentColor)
                    view.progressTintList = ColorStateList.valueOf(theme.accentColor)
                }
                return true
            }

            ThemeTags.Progress.MUSIC_SCAN_PROGRESS_VIEW -> {
                if (view is MusicScanProgressView) {
                    view.setColor(theme.accentColor)
                }
                return true
            }

            ThemeTags.Progress.SEEK_BAR -> {
                if (view is SeekBar) {
                    view.setThumbColor(theme.accentColor)
                    view.setProgressDrawable(
                        roundedProgressDrawable(
                            backgroundColor = if (theme.usesDarkForeground) {
                                0x26000000
                            } else {
                                -2130706433
                            },
                            progressColor = theme.accentColor,
                            cornerRadius = (view.context.resources.displayMetrics.density * 20f).toInt(),
                        ),
                    )
                }
                return true
            }

            ThemeTags.Progress.GROUP_SEEK -> {
                if (view is SeekBar) {
                    view.setThumbColor(theme.accentColor)
                    view.setProgressDrawable(
                        roundedProgressDrawable(
                            backgroundColor = ColorUtils.setAlphaComponent(Color.WHITE, 128),
                            progressColor = theme.accentColor,
                            cornerRadius = (view.context.resources.displayMetrics.density * 10f).toInt(),
                        ),
                    )
                }
                return true
            }

            ThemeTags.Progress.RECYCLER_DIVIDER_COLOR,
            ThemeTags.Progress.BOTTOM_DIVIDER,
            ThemeTags.Progress.MUSIC_SELECT_TITLE_DIVIDER,
            ThemeTags.Core.LINE_BACKGROUND,
            ThemeTags.Progress.SLEEP_DIVIDER_COLOR,
            ThemeTags.Item.ITEM_DIVIDER,
            ThemeTags.Core.UNDER_LINE -> {
                view.setBackgroundColor(
                    ColorUtils.setAlphaComponent(theme.itemTextColor, DIVIDER_ALPHA),
                )
                return true
            }
        }

        return false
    }

    private fun bindEqualizer(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Equalizer.SEEK_BAR -> {
                if (view is SeekBar) {
                    bindEqualizerSeekBar(view, theme.accentColor)
                }
                return true
            }

            ThemeTags.Equalizer.SELECT_BOX -> {
                if (view is ImageView) {
                    bindEqualizerSelectBox(view, theme.accentColor)
                }
                return true
            }

            ThemeTags.Equalizer.ROTATE_STEP_BAR -> {
                if (view is RotateStepBar) {
                    bindEqualizerRotateStepBar(view, theme.accentColor)
                }
                return true
            }

            ThemeTags.Equalizer.ICON -> {
                if (view is ImageView) {
                    view.imageTintList = disabledSelectedDefaultColors(
                        defaultColor = Color.WHITE,
                        selectedColor = theme.accentColor,
                        disabledColor = view.context.getColor(R.color.equalizer_disable_color),
                    )
                }
                return true
            }

            ThemeTags.Equalizer.REVERB_ITEM -> {
                bindReverbItem(view, theme.accentColor)
                return true
            }

            ThemeTags.Equalizer.EFFECT_STROKE -> {
                view.background = GradientDrawable().apply {
                    setStroke(
                        (view.context.resources.displayMetrics.density * 1.5f).toInt(),
                        theme.accentColor,
                    )
                    cornerRadius = view.context.resources.displayMetrics.density * 10f
                }
                return true
            }

            ThemeTags.Equalizer.GROUP_EFFECT_TEXT -> {
                if (view is TextView) {
                    view.setTextColor(
                        selectedDefaultColors(
                            defaultColor = 0xB3FFFFFF.toInt(),
                            selectedColor = theme.accentColor,
                        ),
                    )
                }
                return true
            }

            ThemeTags.Equalizer.GROUP_BOOST -> {
                if (view is TextView) {
                    bindGroupBoost(view, theme.accentColor)
                }
                return true
            }
        }

        return false
    }

    private fun bindMediaAndSelection(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Item.ITEM_BACKGROUND,
            ThemeTags.Item.BOTTOM_MENU_ITEM -> {
                view.background = rectRippleDrawable(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = theme.rippleColor,
                )
                return true
            }

            ThemeTags.Item.ITEM_BACKGROUND_COLOR -> {
                val radius = view.context.resources.displayMetrics.density * 16f
                view.background = if (view.isClickable) {
                    roundedRippleDrawable(
                        fillColor = theme.contentOverlay,
                        rippleColor = theme.rippleColor,
                        cornerRadius = radius,
                    )
                } else {
                    roundedDrawable(
                        cornerRadius = radius,
                        fillColor = theme.contentOverlay,
                    )
                }
                return true
            }

            ThemeTags.Color.ACCENT_COLOR -> {
                if (view is ImageView) {
                    view.setImageDrawable(
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(theme.accentColor)
                        },
                    )
                }
                return true
            }

            ThemeTags.Color.ACCENT_COLOR_BG -> {
                if (view is ImageView) {
                    view.setImageDrawable(
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setStroke(
                                (view.context.resources.displayMetrics.density * 2f).toInt(),
                                Color.WHITE,
                            )
                        },
                    )
                }
                return true
            }

            ThemeTags.Item.BOTTOM_MENU_ICON,
            ThemeTags.Folder.FOLDER_FOOT_SCAN_ICON,
            ThemeTags.Item.ITEM_FAVORITE,
            ThemeTags.Selection.GROUP_SELECT_BOX -> {
                if (view is ImageView) {
                    view.imageTintList = when (tag) {
                        ThemeTags.Selection.GROUP_SELECT_BOX -> {
                            selectedDefaultColors(
                                defaultColor = 0xB3FFFFFF.toInt(),
                                selectedColor = theme.accentColor,
                            )
                        }

                        else -> ColorStateList.valueOf(theme.itemTextColor)
                    }
                }
                return true
            }

            ThemeTags.Banner.BANNER_IMAGE -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList.valueOf(
                        if (palette.isNightTheme()) 1711276032 else Color.WHITE,
                    )
                }
                return true
            }

            ThemeTags.Item.ITEM_ADD -> {
                bindItemAdd(view, palette, theme)
                return true
            }

            ThemeTags.Item.FAVORITE -> {
                if (view is ImageView) {
                    view.imageTintList = selectedDefaultColors(
                        defaultColor = theme.itemTextColor,
                        selectedColor = -42406,
                    )
                    view.background = ovalRippleDrawable(
                        fillColor = Color.TRANSPARENT,
                        rippleColor = theme.rippleColor,
                    )
                }
                return true
            }

            ThemeTags.Folder.FOLDER_FOOT_SCAN_BG -> {
                view.background = roundedRippleDrawable(
                    fillColor = if (theme.usesDarkForeground) 218103808 else 452984831,
                    rippleColor = if (theme.usesDarkForeground) 654311423 else 452984831,
                    cornerRadius = view.context.dpToPx(100f).toFloat(),
                )
                return true
            }

            ThemeTags.Selection.SELECT_ALL,
            ThemeTags.Selection.SELECT_BOX,
            ThemeTags.Selection.SELECT_BOX_2 -> {
                if (view is ImageView) {
                    bindSelectBox(view, palette, theme)
                }
                return true
            }

            ThemeTags.Banner.BANNER_IMAGE_BACKGROUND -> {
                if (view is MaskImageView) {
                    view.setMaskColor(
                        if (palette.isNightTheme()) 855638016 else 1291845632,
                    )
                    view.imageTintList = if (palette.isNightTheme()) {
                        ColorStateList.valueOf(436207616)
                    } else {
                        null
                    }
                }
                return true
            }
        }

        return false
    }

    private fun bindMisc(
        tag: String,
        palette: ThemePalette,
        theme: ThemeBindContext,
        view: View,
    ): Boolean {
        when (tag) {
            ThemeTags.Core.SHADOW_MARK,
            ThemeTags.Core.BOTTOM_SHADOW_MARK -> {
                view.setBackgroundColor(theme.contentOverlay)
                return true
            }

            ThemeTags.Misc.SOUND_WAVE_VIEW -> {
                if (view is SoundWaveView) {
                    view.setWaveColor(theme.itemTextColor)
                    view.setBaseLineColor(
                        ColorUtils.setAlphaComponent(theme.itemTextColor, TEXT_SECONDARY_ALPHA),
                    )
                    view.setClipColor(
                        ColorUtils.setAlphaComponent(theme.itemTextColor, 220),
                    )
                    view.setProgressLineColor(theme.accentColor)
                    view.setOverlayColor(ColorUtils.setAlphaComponent(Color.BLACK, 42))
                    view.setOverlaySelectColor(Color.TRANSPARENT)
                }
                return true
            }

            ThemeTags.Lyric.VIEW -> {
                if (view is LyricView) {
                    view.setCurrentTextColor(theme.titleColor)
                }
                return true
            }

            ThemeTags.Misc.PREVIOUS_VIEW,
            ThemeTags.Misc.NEXT_VIEW -> {
                view.background = ovalRippleDrawable(
                    fillColor = if (theme.usesDarkForeground) 436207616 else 452984831,
                    rippleColor = theme.rippleColor,
                )
                return true
            }

            ThemeTags.Misc.PASTE_BACKGROUND -> {
                view.background = roundedRippleDrawable(
                    fillColor = if (theme.usesDarkForeground) 436207616 else 452984831,
                    rippleColor = theme.accentColor,
                    cornerRadius = view.context.resources.displayMetrics.density * 50f,
                )
                return true
            }

            ThemeTags.Misc.PREFERENCE_FADE_SEEK_BAR -> {
                if (view is SeekBar) {
                    view.setThumbColor(theme.accentColor)
                    view.setProgressDrawable(
                        roundedProgressDrawable(
                            backgroundColor = if (theme.usesDarkForeground) {
                                436207616
                            } else {
                                654311423
                            },
                            progressColor = theme.accentColor,
                            cornerRadius = view.context.dpToPx(16f),
                        ),
                    )
                }
                return true
            }
        }

        return false
    }

    private fun applyContentBackground(
        view: View,
        fillColor: Int,
        rippleColor: Int,
    ) {
        if (view.isClickable) {
            view.background = rectRippleDrawable(fillColor, rippleColor)
        } else {
            view.setBackgroundColor(fillColor)
        }
    }

    private fun applyTextOrIconColor(
        view: View,
        color: Int,
        rippleColor: Int,
    ) {
        when (view) {
            is ImageView -> {
                view.imageTintList = ColorStateList.valueOf(color)
                if (view.isClickable) {
                    view.background = ovalRippleDrawable(
                        fillColor = Color.TRANSPARENT,
                        rippleColor = rippleColor,
                    )
                }
            }

            is TextView -> {
                view.setTextColor(color)
                view.setHintTextColor(ColorUtils.setAlphaComponent(color, HINT_ALPHA))
            }
        }
    }

    private fun applyDialogTitleColor(
        view: View,
        color: Int,
    ) {
        when (view) {
            is ImageView -> view.imageTintList = ColorStateList.valueOf(color)
            is TextView -> view.setTextColor(color)
        }
    }

    private fun tintCompoundDrawables(
        textView: TextView,
        color: Int,
    ) {
        textView.compoundDrawables.filterNotNull().forEach { drawable ->
            DrawableCompat.setTint(drawable.mutate(), color)
        }

        textView.compoundDrawablesRelative.filterNotNull().forEach { drawable ->
            DrawableCompat.setTint(drawable.mutate(), color)
        }
    }

    private fun dialogEditTextBackground(
        strokeColor: Int,
        density: Float,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke((density * 1.5f).toInt(), strokeColor)
            cornerRadius = density * 8f
        }
    }

    private fun bindConfirmButton(
        view: View,
        palette: ThemePalette,
        theme: ThemeBindContext,
    ) {
        if (view is TextView) {
            view.setTextColor(
                enabledDisabledColors(
                    enabledColor = Color.WHITE,
                    disabledColor = 0x80FFFFFF.toInt(),
                ),
            )
        }

        val disabledFillColor = if (theme.usesDarkForeground) {
            0x26000000
        } else {
            0x33FFFFFF
        }

        view.background = defaultWithDisabledDrawable(
            defaultDrawable = roundedRippleDrawable(
                fillColor = theme.accentColor,
                rippleColor = palette.confirmRippleColor,
                cornerRadius = FULL_ROUND_RADIUS,
            ),
            disabledDrawable = roundedDrawable(
                cornerRadius = FULL_ROUND_RADIUS,
                fillColor = disabledFillColor,
            ),
        )
    }

    private fun bindDialogNeutralButton(
        view: View,
        palette: ThemePalette,
        tag: String,
    ) {
        if (view is TextView) {
            view.setTextColor(
                if (palette.isDialogSurfaceLight()) {
                    0xDE000000.toInt()
                } else {
                    0xCCFFFFFF.toInt()
                },
            )
        }

        val fillColor = if (palette.isDialogSurfaceLight()) {
            0x0D000000
        } else {
            0x0DFFFFFF
        }

        val rippleColor = if (tag == ThemeTags.Dialog.DELETE_BUTTON) {
            if (palette.isDialogSurfaceLight()) 0x1A000000 else 0x26FFFFFF
        } else {
            palette.dialogPressedOverlayColor
        }

        view.background = roundedRippleDrawable(
            fillColor = fillColor,
            rippleColor = rippleColor,
            cornerRadius = FULL_ROUND_RADIUS,
        )
    }

    private fun bindDialogEditText(
        editText: EditText,
        palette: ThemePalette,
        theme: ThemeBindContext,
    ) {
        editText.setTextColor(palette.dialogTitleColor)
        editText.setHintTextColor(
            ColorUtils.setAlphaComponent(palette.dialogTitleColor, HINT_ALPHA),
        )
        editText.highlightColor = ColorUtils.setAlphaComponent(
            theme.accentColor,
            DISABLED_ALPHA,
        )

        val fillColor = if (theme.usesDarkForeground) 335544320 else 352321535
        val background = DrawableCompat.wrap(
            editText.background?.mutate()
                ?: roundedDrawable(
                    cornerRadius = editText.context.resources.displayMetrics.density * 8f,
                    fillColor = fillColor,
                ).mutate(),
        )

        DrawableCompat.setTintList(
            background,
            focusedDefaultColors(
                defaultColor = ColorUtils.setAlphaComponent(
                    palette.dialogTitleColor,
                    DISABLED_ALPHA,
                ),
                focusedColor = theme.accentColor,
            ),
        )

        editText.background = background
    }

    private fun bindToolbar(
        toolbar: Toolbar,
        color: Int,
    ) {
        toolbar.setTitleTextColor(color)
        toolbar.setSubtitleTextColor(
            ColorUtils.setAlphaComponent(color, TEXT_SECONDARY_ALPHA),
        )
        toolbar.navigationIcon?.setTint(color)
        toolbar.overflowIcon?.setTint(color)

        for (index in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(index).icon?.setTint(color)
        }
    }

    private fun applyEqualizerTabBackgrounds(
        tabLayout: TabLayout,
        palette: ThemePalette,
    ) {
        val tabItemBackground = pressedDefaultColorDrawable(
            defaultColor = Color.TRANSPARENT,
            pressedColor = palette.headerPressedOverlayColor,
        )

        (tabLayout.getChildAt(0) as? ViewGroup)?.let { tabStrip ->
            for (index in 0 until tabStrip.childCount) {
                tabStrip.getChildAt(index).background = tabItemBackground
            }
        }
    }

    private fun bindTitleColor(
        view: View,
        theme: ThemeBindContext,
    ) {
        when (view) {
            is ImageView -> {
                view.imageTintList = ColorStateList.valueOf(theme.titleColor)
                if (view.isClickable) {
                    view.background = ovalRippleDrawable(
                        fillColor = Color.TRANSPARENT,
                        rippleColor = theme.rippleColor,
                    )
                }
            }

            is TextView -> {
                view.setTextColor(theme.titleColor)
                view.setHintTextColor(
                    ColorUtils.setAlphaComponent(theme.titleColor, HINT_ALPHA),
                )
            }

            else -> view.setBackgroundColor(theme.titleColor)
        }
    }

    private fun bindEditableTextBackground(
        textView: TextView,
        textColor: Int,
        accentColor: Int,
        fillColor: Int,
    ) {
        textView.setTextColor(textColor)
        textView.setHintTextColor(
            ColorUtils.setAlphaComponent(textColor, HINT_ALPHA),
        )

        val background = DrawableCompat.wrap(
            textView.background?.mutate()
                ?: roundedDrawable(
                    cornerRadius = textView.context.resources.displayMetrics.density * 8f,
                    fillColor = fillColor,
                ).mutate(),
        )

        DrawableCompat.setTintList(
            background,
            focusedDefaultColors(
                defaultColor = ColorUtils.setAlphaComponent(textColor, DISABLED_ALPHA),
                focusedColor = accentColor,
            ),
        )

        textView.background = background
    }

    private fun bindSpeedItemText(
        textView: TextView,
        palette: ThemePalette,
    ) {
        textView.setTextColor(
            selectedDefaultColors(
                defaultColor = ColorUtils.setAlphaComponent(palette.titleColor, TEXT_SECONDARY_ALPHA),
                selectedColor = Color.WHITE,
            ),
        )

        val radius = textView.context.resources.displayMetrics.density * 6f
        textView.background = stateDrawable(
            defaultDrawable = roundedRippleDrawable(
                fillColor = ColorUtils.setAlphaComponent(palette.titleColor, 28),
                rippleColor = palette.rippleColor,
                cornerRadius = radius,
            ),
            selectedDrawable = roundedRippleDrawable(
                fillColor = palette.accentColor,
                rippleColor = palette.confirmRippleColor,
                cornerRadius = radius,
            ),
            disabledDrawable = null,
        )
    }

    private fun bindEqualizerSeekBar(
        seekBar: SeekBar,
        accentColor: Int,
    ) {
        val disabledColor = seekBar.context.getColor(R.color.equalizer_disable_color)
        val backgroundColor = seekBar.context.getColor(R.color.equalizer_background_color)
        val cornerRadius = seekBar.context.resources.displayMetrics.density * 2f

        seekBar.setThumbOverlayColor(
            enabledDisabledColors(
                enabledColor = accentColor,
                disabledColor = disabledColor,
            ),
        )

        seekBar.setProgressDrawable(
            layeredProgressDrawable(
                background = roundedDrawable(
                    cornerRadius = cornerRadius,
                    fillColor = backgroundColor,
                ),
                progress = defaultWithDisabledDrawable(
                    defaultDrawable = roundedDrawable(
                        cornerRadius = cornerRadius,
                        fillColor = accentColor,
                    ),
                    disabledDrawable = roundedDrawable(
                        cornerRadius = cornerRadius,
                        fillColor = disabledColor,
                    ),
                ),
            ),
        )
    }

    private fun bindEqualizerSelectBox(
        imageView: ImageView,
        accentColor: Int,
    ) {
        val toggleOffDrawable =
            AppCompatResources.getDrawable(imageView.context, R.drawable.equalizer_toggle_off)
        val toggleOnDrawable =
            AppCompatResources.getDrawable(imageView.context, R.drawable.equalizer_toggle_on)
        val toggleOverlayDrawable =
            AppCompatResources.getDrawable(
                imageView.context,
                R.drawable.equalizer_toggle_on_overlay,
            )?.tinted(accentColor)

        imageView.setImageDrawable(
            stateDrawable(
                defaultDrawable = toggleOffDrawable,
                selectedDrawable = LayerDrawable(
                    listOfNotNull(
                        toggleOnDrawable,
                        toggleOverlayDrawable,
                    ).toTypedArray(),
                ),
                disabledDrawable = null,
            ),
        )
    }

    private fun bindEqualizerRotateStepBar(
        rotateStepBar: RotateStepBar,
        accentColor: Int,
    ) {
        val disabledColor = rotateStepBar.context.getColor(R.color.equalizer_disable_color)
        val backgroundColor = rotateStepBar.context.getColor(R.color.equalizer_background_color)

        val tintList = disabledSelectedDefaultColors(
            defaultColor = backgroundColor,
            selectedColor = accentColor,
            disabledColor = disabledColor,
        )

        rotateStepBar.setIndicatorOverlayTintList(
            enabledDisabledColors(
                enabledColor = accentColor,
                disabledColor = disabledColor,
            ),
        )
        rotateStepBar.setPrimaryGraduationTintList(tintList)
        rotateStepBar.setSecondaryGraduationTintList(tintList)
    }

    private fun bindReverbItem(
        view: View,
        accentColor: Int,
    ) {
        val baseDrawable =
            AppCompatResources.getDrawable(view.context, R.drawable.equalizer_button)
        val selectedOverlay =
            AppCompatResources.getDrawable(view.context, R.drawable.equalizer_button_select)
                ?.tinted(ColorUtils.setAlphaComponent(accentColor, 204))

        view.background = stateDrawable(
            defaultDrawable = baseDrawable,
            selectedDrawable = LayerDrawable(
                listOfNotNull(
                    baseDrawable?.constantState?.newDrawable()?.mutate() ?: baseDrawable,
                    selectedOverlay,
                ).toTypedArray(),
            ),
            disabledDrawable = null,
        )
    }

    private fun bindGroupBoost(
        textView: TextView,
        accentColor: Int,
    ) {
        textView.setTextColor(
            selectedDefaultColors(
                defaultColor = 0xB3FFFFFF.toInt(),
                selectedColor = accentColor,
            ),
        )

        val radius = textView.context.resources.displayMetrics.density * 50f
        val strokeWidth = (textView.context.resources.displayMetrics.density * 1.5f).toInt()
        val fillColor = 0x1AFFFFFF

        textView.background = stateDrawable(
            defaultDrawable = outlinedRoundedRippleDrawable(
                cornerRadius = radius.toInt(),
                strokeWidth = strokeWidth,
                strokeColor = 0xB3FFFFFF.toInt(),
                fillColor = fillColor,
                rippleColor = fillColor,
            ),
            selectedDrawable = outlinedRoundedRippleDrawable(
                cornerRadius = radius.toInt(),
                strokeWidth = strokeWidth,
                strokeColor = accentColor,
                fillColor = fillColor,
                rippleColor = fillColor,
            ),
            disabledDrawable = null,
        )
    }

    private fun bindItemAdd(
        view: View,
        palette: ThemePalette,
        theme: ThemeBindContext,
    ) {
        when (view) {
            is TextView -> {
                view.setTextColor(theme.accentColor)
                view.background = outlinedRoundedRippleDrawable(
                    cornerRadius = view.context.dpToPx(100f),
                    strokeWidth = view.context.dpToPx(1.5f),
                    strokeColor = theme.accentColor,
                    fillColor = ColorUtils.setAlphaComponent(theme.accentColor, 20),
                    rippleColor = theme.rippleColor,
                )
            }

            is ImageView -> {
                view.imageTintList = ColorStateList.valueOf(theme.itemTextColor)
            }
        }
    }

    private fun bindSelectBox(
        imageView: ImageView,
        palette: ThemePalette,
        theme: ThemeBindContext,
    ) {
        if (imageView.isClickable) {
            imageView.background = ovalRippleDrawable(
                fillColor = Color.TRANSPARENT,
                rippleColor = theme.rippleColor,
            )
        }

        val normalColor = if (palette.isContentSurfaceLight()) {
            -3355444
        } else {
            -2171170
        }

        val disabledColor = if (palette.isContentSurfaceLight()) {
            0x66000000
        } else {
            0x4DFFFFFF
        }

        imageView.imageTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(
                    android.R.attr.state_selected,
                    android.R.attr.state_enabled,
                ),
                intArrayOf(
                    android.R.attr.state_checked,
                    android.R.attr.state_enabled,
                ),
                intArrayOf(android.R.attr.state_enabled),
            ),
            intArrayOf(
                disabledColor,
                theme.accentColor,
                theme.accentColor,
                normalColor,
            ),
        )
    }

    private data class ThemeBindContext(
        val accentColor: Int,
        val titleColor: Int,
        val itemTextColor: Int,
        val secondaryTextColor: Int,
        val rippleColor: Int,
        val contentOverlay: Int,
        val strongerOverlay: Int,
        val usesDarkForeground: Boolean,
    ) {
        companion object {
            fun from(palette: ThemePalette): ThemeBindContext {
                val usesDarkForeground = palette.headerTitleColor != Color.WHITE
                val itemTextColor = palette.itemPrimaryTextColor

                return ThemeBindContext(
                    accentColor = palette.accentColor,
                    titleColor = palette.headerTitleColor,
                    itemTextColor = itemTextColor,
                    secondaryTextColor = ColorUtils.setAlphaComponent(
                        itemTextColor,
                        TEXT_SECONDARY_ALPHA,
                    ),
                    rippleColor = if (usesDarkForeground) 0x1A000000 else 0x26FFFFFF,
                    contentOverlay = if (usesDarkForeground) 0 else 0x10000000,
                    strongerOverlay = if (usesDarkForeground) 0x0D000000 else 0x1A000000,
                    usesDarkForeground = usesDarkForeground,
                )
            }
        }
    }

    private companion object {
        const val FULL_ROUND_RADIUS = 1000f
        const val HINT_ALPHA = 128
        const val TEXT_SECONDARY_ALPHA = 180
        const val DISABLED_ALPHA = 77
        const val DIVIDER_ALPHA = 36
    }
}