package gd.app.musicplayer.core.ui.drawable

import android.content.res.ColorStateList
import android.graphics.Bitmap
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
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable

object DrawableUtil {
    @JvmStatic
    fun ovalRipple(fillColor: Int, rippleColor: Int): Drawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
        }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            content,
            ShapeDrawable(OvalShape())
        )
    }

    @JvmStatic
    fun gradientDrawable(radius: Float, fillColor: Int) : Drawable {
        return GradientDrawable().apply {
            cornerRadius = radius
            setColor(fillColor)
        }
    }

    @JvmStatic
    fun roundedRipple(fillColor: Int, rippleColor: Int, radius: Float): Drawable {
        val gradientDrawable = GradientDrawable().apply {
            cornerRadius = radius
            setColor(fillColor)
        }
        val maskRadii = FloatArray(8) { radius }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            gradientDrawable,
            ShapeDrawable(RoundRectShape(maskRadii, null, null))
        )
    }

    @JvmStatic
    fun outlinedRoundedRipple(
        cornerRadius: Int,
        strokeWidth: Int,
        strokeColor: Int,
        rippleColor: Int
    ): Drawable {
        return outlinedRoundedRipple(
            cornerRadius = cornerRadius,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            fillColor = 0,
            rippleColor = rippleColor
        )
    }

    @JvmStatic
    fun outlinedRoundedRipple(
        cornerRadius: Int,
        strokeWidth: Int,
        strokeColor: Int,
        fillColor: Int,
        rippleColor: Int
    ): Drawable {
        val radius = cornerRadius.toFloat()
        val content = GradientDrawable().apply {
            setStroke(strokeWidth, strokeColor)
            this.cornerRadius = radius
            if (fillColor != 0) {
                setColor(fillColor)
            }
        }
        val maskRadii = FloatArray(8) { radius }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            content,
            ShapeDrawable(RoundRectShape(maskRadii, null, null))
        )
    }

    @JvmStatic
    fun roundedFill(cornerRadius: Float, fillColor: Int): Drawable {
        return GradientDrawable().apply {
            this.cornerRadius = cornerRadius
            setColor(fillColor)
        }
    }

    @JvmStatic
    fun roundedProgress(
        backgroundColor: Int,
        progressColor: Int,
        cornerRadius: Int
    ): Drawable {
        val orientation = GradientDrawable.Orientation.LEFT_RIGHT
        val background = GradientDrawable(orientation, intArrayOf(backgroundColor, backgroundColor)).apply {
            this.cornerRadius = cornerRadius.toFloat()
        }
        val progress = GradientDrawable(orientation, intArrayOf(progressColor, progressColor)).apply {
            this.cornerRadius = cornerRadius.toFloat()
        }
        return layeredProgress(
            background,
            ClipDrawable(progress, Gravity.START, ClipDrawable.HORIZONTAL)
        )
    }

    @JvmStatic
    fun layeredProgress(background: Drawable, progress: Drawable): Drawable {
        return LayerDrawable(arrayOf(background, progress)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    @JvmStatic
    fun rectRipple(fillColor: Int, rippleColor: Int): Drawable {
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            fillColor.toDrawable(),
            ShapeDrawable(RectShape())
        )
    }

    @JvmStatic
    fun tinted(drawable: Drawable?, tintColor: Int): Drawable? {
        return drawable?.let {
            DrawableCompat.wrap(it).mutate().also { wrapped ->
                DrawableCompat.setTint(wrapped, tintColor)
            }
        }
    }
}

