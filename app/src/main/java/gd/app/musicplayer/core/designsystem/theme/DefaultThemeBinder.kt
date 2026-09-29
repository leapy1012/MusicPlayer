package gd.app.musicplayer.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import gd.app.musicplayer.core.designsystem.drawable.ovalRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.rectRippleDrawable
import gd.app.musicplayer.core.designsystem.drawable.roundedProgressDrawable
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.ui.theme.ThemeTags

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
                        view.setBackgroundColor(
                            resolveCouiColor(
                                view,
                                com.coui.appcompat.R.attr.couiColorCardBackground,
                                0xFFFFFFFF.toInt(),
                            )
                        )
                        // Soft COUI card lift over page background.
                        panel?.elevation = view.resources.displayMetrics.density * 6f
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
                val accent = if (palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT) {
                    resolveCouiColor(
                        view,
                        com.coui.appcompat.R.attr.couiColorLabelTheme,
                        theme.accentColor,
                    )
                } else {
                    theme.accentColor
                }
                view.setThumbColor(accent)
                val track = when (palette.getThemeType()) {
                    ThemeManager.THEME_TYPE_PICTURE -> 0x40FFFFFF
                    ThemeManager.THEME_TYPE_LIGHT -> 0x26000000
                    else -> 0x33FFFFFF
                }
                view.setProgressDrawable(
                    roundedProgressDrawable(
                        backgroundColor = track,
                        progressColor = accent,
                        cornerRadius = (view.context.resources.displayMetrics.density * 20f).toInt(),
                    ),
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
        toolbar.navigationIcon?.setTint(color)
        toolbar.overflowIcon?.setTint(color)
        for (index in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(index).icon?.setTint(color)
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
