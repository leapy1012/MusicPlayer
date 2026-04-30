package gd.app.lib.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.withStyledAttributes

import gd.app.lib.R
import androidx.core.graphics.drawable.toDrawable

class MaskImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var maskDrawable: Drawable? = null

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.MaskImageView) {
                val color = getColor(
                    R.styleable.MaskImageView_maskColor,
                    0
                )
                if (color != 0) {
                    maskDrawable = color.toDrawable()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        try {
            super.onDraw(canvas)
        } catch (e: Exception) {
            e.printStackTrace() // Replace with Timber or your logger if needed
        }

        maskDrawable?.let { drawable ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }
    }

    fun setMaskColor(color: Int) {
        maskDrawable = color.toDrawable()
        invalidate()
    }

    fun setMaskDrawable(drawable: Drawable?) {
        maskDrawable = drawable
        invalidate()
    }
}