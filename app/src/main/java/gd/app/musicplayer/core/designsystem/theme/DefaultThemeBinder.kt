package gd.app.musicplayer.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.drawable.defaultWithDisabledDrawable
import gd.app.musicplayer.core.designsystem.drawable.disabledSelectedDefaultColors
import gd.app.musicplayer.core.designsystem.drawable.enabledDisabledColors
import gd.app.musicplayer.core.designsystem.drawable.layeredProgressDrawable
import gd.app.musicplayer.core.designsystem.drawable.ovalRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.rectRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.roundedDrawable
import gd.app.musicplayer.core.designsystem.drawable.roundedProgressDrawable
import gd.app.musicplayer.core.designsystem.drawable.stateDrawable
import gd.app.musicplayer.core.designsystem.drawable.tinted
import gd.app.musicplayer.core.designsystem.view.RotateStepBar
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.ui.theme.ThemeTags
import androidx.appcompat.content.res.AppCompatResources
import android.graphics.drawable.LayerDrawable

/**
 * Home-screen-focused binder: wallpaper / blur plus the tags needed for Picture chrome
 * on main + mini player (toolbar, content wash, text/icons, bottom bar, seek).
 */
class DefaultThemeBinder : ThemeViewBinder {

    override fun bind(
        palette: ThemePalette,
        payload: Any?,
        view: View,
    ): Boolean {
        val tag = payload as? String ?: return false
        val theme = ThemeBindContext.from(palette)
        return when (tag) {
            ThemeTags.Core.ACTIVITY_BACKGROUND -> {
                view.background = palette.getActivityBackgroundDrawable(view.context)
                syncWindowBackground(view, palette)
                true
            }

            ThemeTags.Core.BLUR_BACKGROUND -> {
                if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
                    view.setBackgroundColor(
                        resolveCouiColor(
                            view,
                            com.coui.appcompat.R.attr.couiColorCardBackground,
                            Color.WHITE,
                        )
                    )
                } else {
                    val drawable = palette.getBlurredBackgroundDrawable(view.context)
                    view.background = drawable.constantState?.newDrawable()?.mutate()
                        ?: drawable.mutate()
                }
                true
            }

            ThemeTags.Core.TITLE_BACKGROUND_COLOR -> {
                view.setBackgroundColor(palette.headerOverlayColor)
                true
            }

            ThemeTags.Core.CONTENT_BACKGROUND -> {
                applyContentBackground(view, theme.contentOverlay, theme.rippleColor)
                true
            }

            ThemeTags.Core.BOTTOM_CONTROL_BACKGROUND -> {
                val panel = view.parent as? View
                when (palette.getThemeType()) {
                    ThemeManager.THEME_TYPE_LIGHT -> {
                        // Match the page wash so the mini bar doesn't read as a gray card.
                        view.setBackgroundColor(
                            resolveCouiColor(
                                view,
                                com.coui.appcompat.R.attr.couiColorBackgroundWithCard,
                                0xFFF0F1F2.toInt(),
                            )
                        )
                        panel?.elevation = 0f
                    }
                    ThemeManager.THEME_TYPE_PICTURE -> {
                        // Match header wash strength so sky/teal wallpaper reads through
                        // (opaque black slab was the "strange" mini-player look).
                        view.setBackgroundColor(0x4D000000.toInt())
                        panel?.elevation = 0f
                    }
                    else -> {
                        view.setBackgroundColor(0x99000000.toInt())
                        panel?.elevation = 0f
                    }
                }
                true
            }

            ThemeTags.Core.BOTTOM_CONTROL_DIVIDER -> {
                if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
                    view.setBackgroundColor(
                        resolveCouiColor(
                            view,
                            com.coui.appcompat.R.attr.couiColorDivider,
                            0x1F000000,
                        )
                    )
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
                true
            }

            // Original decoded: bottomDivider above edit/selection action bar.
            ThemeTags.Core.BOTTOM_DIVIDER -> {
                when (palette.getThemeType()) {
                    ThemeManager.THEME_TYPE_LIGHT -> view.setBackgroundColor(
                        resolveCouiColor(
                            view,
                            com.coui.appcompat.R.attr.couiColorDivider,
                            0x1F000000,
                        )
                    )
                    else -> view.setBackgroundColor(0x33FFFFFF)
                }
                true
            }

            ThemeTags.Navigation.TOOLBAR -> {
                if (view !is Toolbar) return false
                val iconColor = if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
                    resolveCouiColor(
                        view,
                        com.coui.appcompat.R.attr.couiColorPrimaryNeutral,
                        theme.titleColor,
                    )
                } else {
                    theme.titleColor
                }
                bindToolbar(view, iconColor)
                if (view is com.coui.appcompat.toolbar.COUIToolbar) {
                    view.setIsTitleCenterStyle(false)
                    view.couiTitleTextView?.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }
                true
            }

            // Original decoded: bottomMenuItem → press wash on each edit action.
            ThemeTags.Navigation.BOTTOM_MENU_ITEM -> {
                val ripple = when (palette.getThemeType()) {
                    ThemeManager.THEME_TYPE_LIGHT -> 0x1A000000
                    else -> 0x26FFFFFF
                }
                view.isClickable = true
                view.background = rectRippleDrawable(Color.TRANSPARENT, ripple)
                true
            }

            ThemeTags.Text.ITEM_TEXT_COLOR -> {
                val color = if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
                    resolveCouiColor(
                        view,
                        com.coui.appcompat.R.attr.couiColorPrimaryNeutral,
                        theme.itemTextColor,
                    )
                } else {
                    theme.itemTextColor
                }
                applyTextOrIconColor(view, color, theme.rippleColor)
                true
            }

            ThemeTags.Text.ITEM_TEXT_SECONDARY -> {
                val color = if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
                    resolveCouiColor(
                        view,
                        com.coui.appcompat.R.attr.couiColorSecondNeutral,
                        theme.secondaryTextColor,
                    )
                } else {
                    theme.secondaryTextColor
                }
                applyTextOrIconColor(view, color, theme.rippleColor)
                true
            }

            // Original decoded: bottomMenuText / bottomMenuIcon on edit-bar icons+labels.
            // Light → COUI neutral; pictured/dark → white over the translucent action wash.
            ThemeTags.Text.BOTTOM_MENU_TEXT,
            ThemeTags.Text.BOTTOM_MENU_ICON -> {
                val color = if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
                    resolveCouiColor(
                        view,
                        com.coui.appcompat.R.attr.couiColorPrimaryNeutral,
                        theme.itemTextColor,
                    )
                } else {
                    Color.WHITE
                }
                applyTextOrIconColor(view, color, theme.rippleColor)
                true
            }

            ThemeTags.Progress.SEEK_BAR -> {
                if (view !is SeekBar) return false
                val themeType = palette.getThemeType()
                val isLight = themeType == ThemeManager.THEME_TYPE_LIGHT
                // Mini hairline: near-white track + accent fill (not white-on-white).
                val isMiniHairline = view.id == gd.app.musicplayer.R.id.main_music_progress
                val progressColor = when {
                    isMiniHairline -> theme.accentColor
                    isLight -> resolveCouiColor(
                        view,
                        com.coui.appcompat.R.attr.couiColorLabelTheme,
                        theme.accentColor,
                    )
                    else -> theme.accentColor
                }
                val thumbColor = when {
                    isMiniHairline -> theme.accentColor
                    isLight -> progressColor
                    else -> theme.accentColor
                }
                view.setThumbColor(thumbColor)
                val track = when {
                    isMiniHairline && isLight -> Color.WHITE
                    isMiniHairline -> 0x99FFFFFF.toInt() // soft white remaining on pictured/dark
                    themeType == ThemeManager.THEME_TYPE_PICTURE -> 0x40FFFFFF
                    isLight -> 0x26000000
                    else -> 0x33FFFFFF
                }
                view.setProgressDrawable(
                    roundedProgressDrawable(
                        backgroundColor = track,
                        progressColor = progressColor,
                        cornerRadius = (view.context.resources.displayMetrics.density * 20f).toInt(),
                    ),
                )
                true
            }

            // Skeuomorphic EQ seeks (bands vertical, VOL horizontal): accent track.
            ThemeTags.Progress.EQUALIZER_SEEK_BAR -> {
                if (view !is SeekBar) return false
                val accent = theme.accentColor
                val disabled = view.context.getColor(R.color.equalizer_disable_color)
                val trackBg = view.context.getColor(R.color.equalizer_background_color)
                val radius = view.context.resources.displayMetrics.density * 2f
                val vertical = view.isVertical()
                view.setThumbOverlayColor(enabledDisabledColors(accent, disabled))
                view.setProgressDrawable(
                    layeredProgressDrawable(
                        background = roundedDrawable(radius, trackBg),
                        progress = ClipDrawable(
                            defaultWithDisabledDrawable(
                                roundedDrawable(radius, accent),
                                roundedDrawable(radius, disabled),
                            ),
                            if (vertical) Gravity.BOTTOM else Gravity.START,
                            if (vertical) ClipDrawable.VERTICAL else ClipDrawable.HORIZONTAL,
                        ),
                    ),
                )
                true
            }

            // Rotary Bass Boost / Virtualizer / balance: tint graduations + overlay.
            ThemeTags.Progress.EQUALIZER_ROTATE_STEP_BAR -> {
                if (view !is RotateStepBar) return false
                val accent = theme.accentColor
                val disabled = view.context.getColor(R.color.equalizer_disable_color)
                val trackBg = view.context.getColor(R.color.equalizer_background_color)
                val graduationTint = disabledSelectedDefaultColors(trackBg, accent, disabled)
                view.setIndicatorOverlayTintList(enabledDisabledColors(accent, disabled))
                view.setPrimaryGraduationTintList(graduationTint)
                view.setSecondaryGraduationTintList(graduationTint)
                true
            }

            ThemeTags.Progress.REVERB_ITEM -> {
                val base = AppCompatResources.getDrawable(
                    view.context,
                    R.drawable.equalizer_button,
                )
                val selectedOverlay = AppCompatResources
                    .getDrawable(view.context, R.drawable.equalizer_button_select)
                    ?.mutate()
                    ?.tinted(ColorUtils.setAlphaComponent(theme.accentColor, 204))
                view.background = stateDrawable(
                    defaultDrawable = base,
                    selectedDrawable = if (base != null && selectedOverlay != null) {
                        LayerDrawable(arrayOf(base, selectedOverlay))
                    } else {
                        base
                    },
                )
                true
            }

            else -> false
        }
    }

    private fun resolveCouiColor(view: View, attr: Int, fallback: Int): Int {
        val typed = view.context.obtainStyledAttributes(intArrayOf(attr))
        val color = typed.getColor(0, fallback)
        typed.recycle()
        return color
    }

    private fun applyContentBackground(view: View, fillColor: Int, rippleColor: Int) {
        if (view.isClickable) {
            view.background = rectRippleDrawable(fillColor, rippleColor)
        } else {
            view.setBackgroundColor(fillColor)
        }
    }

    private fun applyTextOrIconColor(view: View, color: Int, rippleColor: Int) {
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
                view.setHintTextColor(
                    ColorUtils.setAlphaComponent(color, ThemeBindDefaults.HINT_ALPHA),
                )
            }
        }
    }

    private fun bindToolbar(toolbar: Toolbar, color: Int) {
        toolbar.setBackgroundColor(Color.TRANSPARENT)
        toolbar.setTitleTextColor(color)
        toolbar.setSubtitleTextColor(
            ColorUtils.setAlphaComponent(color, ThemeBindDefaults.TEXT_SECONDARY_ALPHA),
        )
        toolbar.navigationIcon?.mutate()?.setTint(color)
        toolbar.overflowIcon?.mutate()?.setTint(color)
        for (index in 0 until toolbar.menu.size()) {
            val icon = toolbar.menu.getItem(index).icon ?: continue
            icon.mutate().setTint(color)
            toolbar.menu.getItem(index).icon = icon
        }
    }

    private fun syncWindowBackground(view: View, palette: ThemePalette) {
        val activity = view.context.findActivity() ?: return
        activity.window?.setBackgroundDrawable(
            palette.getActivityBackgroundDrawable(view.context)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
