package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.widget.ImageViewCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.designsystem.drawable.DrawableUtil
import gd.app.musicplayer.core.designsystem.drawable.ViewStateDrawables
import gd.app.musicplayer.core.common.util.ColorBrightnessUtils

class ColorSelectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs), View.OnClickListener {

    fun interface OnColorChangedListener {
        fun onColorChanged(color: Int)
    }

    private val colorViews = mutableListOf<ImageView>()
    private var selectedColor: Int = AVAILABLE_COLORS.first()
    private var onColorChangedListener: OnColorChangedListener? = null

    init {
        orientation = HORIZONTAL
        buildColorOptions(context)
    }

    override fun onClick(view: View) {
        if (view.isSelected) return

        colorViews.forEachIndexed { index, colorView ->
            val isSelected = colorView == view
            colorView.isSelected = isSelected
            if (isSelected) {
                selectedColor = AVAILABLE_COLORS[index]
            }
        }

        onColorChangedListener?.onColorChanged(selectedColor)
    }

    fun setColor(color: Int) {
        selectedColor = color
        colorViews.forEachIndexed { index, colorView ->
            colorView.isSelected = AVAILABLE_COLORS[index] == color
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        colorViews.forEach { it.isEnabled = enabled }
    }

    fun setOnColorChangedListener(listener: OnColorChangedListener?) {
        onColorChangedListener = listener
    }

    fun setShowWhiteColor(show: Boolean) {
        if (childCount > 2) {
            for (index in childCount - 2 until childCount) {
                getChildAt(index).visibility = if (show) VISIBLE else GONE
            }
        }
    }

    private fun buildColorOptions(context: Context) {
        val cornerRadius = context.dpToPx(4f)
        val borderWidth = context.dpToPx(1f)

        AVAILABLE_COLORS.forEachIndexed { index, color ->
            val colorView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(
                    _root_ide_package_.gd.app.musicplayer.core.designsystem.drawable.ViewStateDrawables.selectedDefaultDrawableFromRes(context, intArrayOf(
                        R.drawable.vector_color_unselect,
                        R.drawable.vector_color_select
                    ))
                )

                background = createColorBackground(
                    color = color,
                    cornerRadius = cornerRadius,
                    borderWidth = borderWidth
                )

                if (ColorBrightnessUtils.isLightColor(color)) {
                    ImageViewCompat.setImageTintList(
                        this,
                        ColorStateList.valueOf(WHITE_CHECKMARK_TINT)
                    )
                }

                setOnClickListener(this@ColorSelectView)
            }

            addView(
                colorView,
                LayoutParams(0, LayoutParams.MATCH_PARENT).apply {
                    weight = COLOR_ITEM_WEIGHT
                }
            )
            colorViews += colorView

            if (index != AVAILABLE_COLORS.lastIndex) {
                addView(
                    Space(context),
                    LayoutParams(0, LayoutParams.MATCH_PARENT).apply {
                        weight = SPACER_WEIGHT
                    }
                )
            }
        }
    }

    private fun createColorBackground(
        color: Int,
        cornerRadius: Int,
        borderWidth: Int
    ) = if (color == WHITE_COLOR) {
        _root_ide_package_.gd.app.musicplayer.core.designsystem.drawable.DrawableUtil.outlinedRoundedRipple(
            cornerRadius,
            borderWidth,
            WHITE_BORDER_COLOR,
            color,
            WHITE_BORDER_COLOR
        )
    } else {
        _root_ide_package_.gd.app.musicplayer.core.designsystem.drawable.DrawableUtil.roundedRipple(color, PRESSED_OVERLAY_COLOR, cornerRadius.toFloat())
    }

    companion object {
        private val AVAILABLE_COLORS = intArrayOf(
            -45747,
            -16714366,
            -12669697,
            -16715530,
            -9371,
            -40448,
            -1
        )

        private const val COLOR_ITEM_WEIGHT = 5f
        private const val SPACER_WEIGHT = 1f

        private const val WHITE_COLOR = -1
        private const val WHITE_CHECKMARK_TINT = -6710887
        private const val WHITE_BORDER_COLOR = 436207616
        private const val PRESSED_OVERLAY_COLOR = 452984831
    }
}
