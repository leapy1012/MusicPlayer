package gd.app.musicplayer.core.designsystem.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import androidx.core.graphics.withSave
import androidx.core.graphics.withClip

class RoundedMaskDrawable(
    private val drawable: Drawable?,
    private val cornerRadius: Float
) : DrawableWrapper(drawable) {

    private val clipPath = Path()

    override fun draw(canvas: Canvas) {
        canvas.withClip(clipPath) {

            super.draw(canvas)

        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)

        clipPath.reset()

        clipPath.addRoundRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            cornerRadius * 2,
            cornerRadius * 2,
            Path.Direction.CW
        )
    }
}
