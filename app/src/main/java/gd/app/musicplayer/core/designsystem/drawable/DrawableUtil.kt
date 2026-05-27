package gd.app.musicplayer.core.designsystem.drawable

import android.content.res.ColorStateList
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape
import android.view.Gravity
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable

fun ovalRippleDrawable(
    @ColorInt fillColor: Int,
    @ColorInt rippleColor: Int,
): Drawable {
    return RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        ovalDrawable(fillColor),
        ShapeDrawable(OvalShape()),
    )
}

fun roundedDrawable(
    cornerRadius: Float,
    @ColorInt fillColor: Int,
): Drawable {
    return GradientDrawable().apply {
        this.cornerRadius = cornerRadius
        setColor(fillColor)
    }
}

fun roundedRippleDrawable(
    @ColorInt fillColor: Int,
    @ColorInt rippleColor: Int,
    cornerRadius: Float,
): Drawable {
    return RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        roundedDrawable(
            cornerRadius = cornerRadius,
            fillColor = fillColor,
        ),
        roundRectMask(cornerRadius),
    )
}

fun outlinedRoundedRippleDrawable(
    cornerRadius: Int,
    strokeWidth: Int,
    @ColorInt strokeColor: Int,
    @ColorInt rippleColor: Int,
): Drawable {
    return createOutlinedRoundedRippleDrawable(
        cornerRadius = cornerRadius,
        strokeWidth = strokeWidth,
        strokeColor = strokeColor,
        fillColor = null,
        rippleColor = rippleColor,
    )
}

fun outlinedRoundedRippleDrawable(
    cornerRadius: Int,
    strokeWidth: Int,
    @ColorInt strokeColor: Int,
    @ColorInt fillColor: Int,
    @ColorInt rippleColor: Int,
): Drawable {
    return createOutlinedRoundedRippleDrawable(
        cornerRadius = cornerRadius,
        strokeWidth = strokeWidth,
        strokeColor = strokeColor,
        fillColor = fillColor,
        rippleColor = rippleColor,
    )
}

fun roundedProgressDrawable(
    @ColorInt backgroundColor: Int,
    @ColorInt progressColor: Int,
    cornerRadius: Int,
): Drawable {
    val radius = cornerRadius.toFloat()

    val background = horizontalGradientDrawable(
        startColor = backgroundColor,
        endColor = backgroundColor,
        cornerRadius = radius,
    )

    val progress = horizontalGradientDrawable(
        startColor = progressColor,
        endColor = progressColor,
        cornerRadius = radius,
    )

    return layeredProgressDrawable(
        background = background,
        progress = ClipDrawable(
            progress,
            Gravity.START,
            ClipDrawable.HORIZONTAL,
        ),
    )
}

fun layeredProgressDrawable(
    background: Drawable,
    progress: Drawable,
): Drawable {
    return LayerDrawable(arrayOf(background, progress)).apply {
        setId(BACKGROUND_LAYER_INDEX, android.R.id.background)
        setId(PROGRESS_LAYER_INDEX, android.R.id.progress)
    }
}

fun rectRippleDrawable(
    @ColorInt fillColor: Int,
    @ColorInt rippleColor: Int,
): Drawable {
    return RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        fillColor.toDrawable(),
        ShapeDrawable(RectShape()),
    )
}

fun Drawable.tinted(
    @ColorInt tintColor: Int,
): Drawable {
    return DrawableCompat.wrap(this)
        .mutate()
        .also { wrapped ->
            DrawableCompat.setTint(wrapped, tintColor)
        }
}

fun createOutlinedRoundedRippleDrawable(
    cornerRadius: Int,
    strokeWidth: Int,
    @ColorInt strokeColor: Int,
    @ColorInt fillColor: Int?,
    @ColorInt rippleColor: Int,
): Drawable {
    val radius = cornerRadius.toFloat()

    val content = GradientDrawable().apply {
        this.cornerRadius = radius
        setStroke(strokeWidth, strokeColor)

        if (fillColor != null) {
            setColor(fillColor)
        }
    }

    return RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        content,
        roundRectMask(radius),
    )
}

private fun ovalDrawable(
    @ColorInt fillColor: Int,
): Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
    }
}

private fun roundRectMask(
    radius: Float,
): Drawable {
    return ShapeDrawable(
        RoundRectShape(
            FloatArray(ROUND_RECT_CORNER_COUNT) { radius },
            null,
            null,
        ),
    )
}

private fun horizontalGradientDrawable(
    @ColorInt startColor: Int,
    @ColorInt endColor: Int,
    cornerRadius: Float,
): Drawable {
    return GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(startColor, endColor),
    ).apply {
        this.cornerRadius = cornerRadius
    }
}

private const val ROUND_RECT_CORNER_COUNT = 8
private const val BACKGROUND_LAYER_INDEX = 0
private const val PROGRESS_LAYER_INDEX = 1