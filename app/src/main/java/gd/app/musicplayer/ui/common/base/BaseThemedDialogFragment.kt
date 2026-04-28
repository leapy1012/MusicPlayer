package gd.app.musicplayer.ui.common.base

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.drawable.DrawableCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.screenHeight
import gd.app.musicplayer.core.extension.screenWidth
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables
import gd.app.musicplayer.core.ui.view.SeekBar

abstract class BaseThemedDialogFragment : DialogFragment() {

    protected open val dialogOverlayColor: Int = 0xBF2A2A39.toInt()
    protected open val dialogCornerRadiusDp: Float = 12f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppDialogTheme)
        isCancelable = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyTagStyles(view)
    }

    protected fun currentAccentColor(): Int =
        requireContext().appDependencies.themeRepo.getAccentColor(requireContext())

    protected fun applyDialogWidth(widthRatio: Float) {
        dialog?.window?.let { window ->
            val context = window.context
            window.attributes = window.attributes.apply {
                gravity = Gravity.CENTER
                width = (context.screenWidth * widthRatio).toInt()
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                dimAmount = 0.5f
            }
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    protected fun applyDialogBackground(
        rootView: View,
        reqWidth: Int = maxOf(320, requireContext().screenWidth / 2),
        reqHeight: Int = maxOf(220, requireContext().screenHeight / 3)
    ) {
        rootView.background = rootView.context.appDependencies.themeRepo
            .getCorePalette(rootView.context)
            .getDialogSurfaceDrawable(rootView.context)
    }

    protected fun applyTagStyles(rootView: View, accentColor: Int = currentAccentColor()) {
        val palette = DialogTagPalette(
            theme = rootView.context.appDependencies.themeRepo.getCorePalette(rootView.context),
            accentColorOverride = accentColor,
            editTextBackground = rootView.context.appDependencies.themeRepo
                .getCorePalette(rootView.context)
                .getEditTextBackground(rootView.context)
        )
        applyTaggedStylesRecursive(rootView, palette)
    }

    private fun applyTaggedStylesRecursive(view: View, palette: DialogTagPalette) {
        val tag = view.tag as? String
        applyTaggedStyle(tag = tag, view = view, palette = palette)

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyTaggedStylesRecursive(view.getChildAt(index), palette)
            }
        }
    }

    protected open fun applyTaggedStyle(
        tag: String?,
        view: View,
        palette: DialogTagPalette
    ): Boolean {
        return when (tag) {
            "dialogTitle", "dialogTitleColor", "dialogTitleIcon", "dialogItem" -> {
                when (view) {
                    is TextView -> view.setTextColor(palette.titleColor)
                    is ImageView -> view.imageTintList = ColorStateList.valueOf(palette.titleColor)
                }
                true
            }

            "dialogMessage", "dialogMessageColor" -> {
                when (view) {
                    is TextView -> view.setTextColor(palette.messageColor)
                    is ImageView -> view.imageTintList =
                        ColorStateList.valueOf(palette.messageColor)
                }
                true
            }

            "dialogFavorite" -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_selected),
                            intArrayOf()
                        ),
                        intArrayOf(
                            palette.accentColor,
                            palette.titleColor
                        )
                    )
                }
                true
            }

            "dialogButton" -> {
                if (view is TextView) {
                    view.setTextColor(palette.accentColor)
                }
                view.background = DrawableUtil.rectRipple(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = palette.rippleColor
                )
                true
            }

            "dialogConfirm" -> {
                if (view is TextView) {
                    view.setTextColor(Color.WHITE)
                }
                view.background = DrawableUtil.roundedRipple(
                    fillColor = palette.accentColor,
                    rippleColor = palette.confirmRippleColor,
                    radius = 1000f
                )
                true
            }

            "dialogCancel" -> {
                if (view is TextView) {
                    view.setTextColor(palette.cancelTextColor)
                }
                view.background = DrawableUtil.roundedRipple(
                    fillColor = palette.cancelBaseColor,
                    rippleColor = palette.rippleColor,
                    radius = 1000f
                )
                true
            }

            "dialogItemBackground" -> {
                view.background = DrawableUtil.rectRipple(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = palette.rippleColor
                )
                true
            }

            "dialogSelectBox" -> {
                if (view is ImageView) {
                    view.imageTintList = createSelectBoxTintList(
                        normalColor = palette.selectBoxNormalColor,
                        accentColor = palette.accentColor
                    )
                }
                true
            }

            "dialogDivider", "dialogDividerColor" -> {
                if (view is ListView) {
                    view.divider = palette.dividerColor.toDrawable()
                    if (view.dividerHeight <= 0) {
                        view.dividerHeight = 1
                    }
                } else {
                    view.setBackgroundColor(palette.dividerColor)
                }
                true
            }

            "dialogEditText" -> {
                if (view is EditText) {
                    view.setTextColor(palette.titleColor)
                    view.setHintTextColor(ColorUtils.setAlphaComponent(palette.titleColor, 128))
                    view.highlightColor = ColorUtils.setAlphaComponent(palette.accentColor, 77)

                    val background = DrawableCompat.wrap(
                        view.background?.mutate() ?: palette.editTextBackground.mutate()
                    )
                    DrawableCompat.setTintList(
                        background,
                        ViewStateDrawables.focusedDefaultColors(
                            ColorUtils.setAlphaComponent(palette.titleColor, 77),
                            palette.accentColor
                        )
                    )
                    view.background = background
                }
                true
            }

            "dialogSeekBar" -> {
                if (view is SeekBar) {
                    view.setThumbColor(palette.accentColor)
                    view.setProgressDrawable(
                        DrawableUtil.roundedProgress(
                            ColorUtils.setAlphaComponent(palette.titleColor, 77),
                            palette.accentColor,
                            (view.context.resources.displayMetrics.density * 4f).toInt()
                        )
                    )
                }
                true
            }

            "speedItemDes" -> {
                if (view is TextView) {
                    view.setTextColor(ColorUtils.setAlphaComponent(palette.titleColor, 160))
                }
                true
            }

            "speedItemText" -> {
                if (view is TextView) {
                    view.setTextColor(
                        ViewStateDrawables.selectedDefaultColors(
                            ColorUtils.setAlphaComponent(palette.titleColor, 180),
                            Color.WHITE
                        )
                    )
                    val radius = view.context.resources.displayMetrics.density * 6f
                    view.background = ViewStateDrawables.buildStateDrawable(
                        DrawableUtil.roundedRipple(
                            fillColor = ColorUtils.setAlphaComponent(palette.titleColor, 28),
                            rippleColor = palette.rippleColor,
                            radius = radius
                        ),
                        DrawableUtil.roundedRipple(
                            fillColor = palette.accentColor,
                            rippleColor = palette.confirmRippleColor,
                            radius = radius
                        ),
                        null
                    )
                }
                true
            }

            else -> false
        }
    }

    private fun createSelectBoxTintList(normalColor: Int, accentColor: Int): ColorStateList {
        return ColorStateList(
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
                normalColor
            )
        )
    }

    protected data class DialogTagPalette(
        val theme: ThemePalette,
        val accentColorOverride: Int,
        val editTextBackground: android.graphics.drawable.Drawable
    ) {
        val accentColor: Int get() = accentColorOverride
        val titleColor: Int get() = theme.titleColor
        val messageColor: Int get() = theme.messageColor
        val rippleColor: Int get() = theme.rippleColor
        val dividerColor: Int get() = theme.dividerColor
        val cancelTextColor: Int get() = theme.cancelTextColor
        val cancelBaseColor: Int get() = theme.cancelBaseColor
        val confirmRippleColor: Int get() = theme.confirmRippleColor
        val selectBoxNormalColor: Int =
            if (titleColor == Color.WHITE) -2171170 else -3355444
    }
}
