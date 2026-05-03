package gd.app.lib.model.lrc.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import gd.app.lib.R
import gd.app.lib.dpToPx
import gd.app.lib.getCenteredTextBaselineY
import gd.app.lib.model.lrc.resource.LyricText
import gd.app.lib.toPlaybackTimeText

class DefaultLyricIndicatorRenderer : LyricIndicatorRenderer {

    private var playIcon: Drawable? = null
    private val paint: Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
    }

    private var textAlign: Int = 1
    private var cachedReservedWidth: Int = -1
    private var horizontalPadding: Int = 0
    private var iconTextSpacing: Float = 0f

    private fun reservedWidth(): Int {
        if (cachedReservedWidth == -1) {
//            if (o8.c.f().h() == null) return 0



            var width = paint.measureText((0L).toPlaybackTimeText()).toInt() + horizontalPadding

            if (textAlign != ALIGN_CENTER) {
                playIcon?.let {
                    width += it.bounds.width() + iconTextSpacing.toInt()
                }
            }

            cachedReservedWidth = width
        }

        return cachedReservedWidth
    }

    override fun setTextAlign(align: Int) {
        textAlign = align
        cachedReservedWidth = -1
    }

    override fun setColor(color: Int) {
        paint.color = color
        playIcon?.let { DrawableCompat.setTint(it, color) }
    }

    override fun adjustContentBounds(bounds: Rect) {
        when (textAlign) {
            ALIGN_LEFT -> bounds.right -= reservedWidth()
            ALIGN_RIGHT -> bounds.left += reservedWidth()
            else -> bounds.inset(reservedWidth() / 2, 0)
        }
    }

    override fun setTextSize(size: Float) {
        paint.textSize = size
        cachedReservedWidth = -1
    }

    override fun drawForeground(
        canvas: Canvas,
        fullBounds: Rect,
        scrollOffset: Float,
        lyricData: LyricText
    ) {
        val currentLine = lyricData.findLineByVerticalPosition (fullBounds.centerY() - scrollOffset, false)
        val icon = playIcon ?: return
        if (currentLine == null) return

        val timeText = if (currentLine.startTime >= 0) (currentLine.startTime.toPlaybackTimeText()) else ""
        val textWidth = paint.measureText(timeText)
        val iconBounds = Rect(icon.bounds)

        val textX: Float
        val lineStartX: Float
        val lineEndX: Float

        when (textAlign) {
            ALIGN_LEFT -> {
                iconBounds.offsetTo(
                    fullBounds.right - iconBounds.width() - horizontalPadding,
                    fullBounds.centerY() - iconBounds.height() / 2
                )

                textX = iconBounds.left - textWidth - iconTextSpacing
                lineStartX = fullBounds.left + horizontalPadding.toFloat()
                lineEndX = textX - iconTextSpacing
            }

            ALIGN_RIGHT -> {
                iconBounds.offsetTo(
                    fullBounds.left + horizontalPadding,
                    fullBounds.centerY() - iconBounds.height() / 2
                )

                textX = iconBounds.right + iconTextSpacing
                lineStartX = textX + textWidth + iconTextSpacing
                lineEndX = fullBounds.right - horizontalPadding.toFloat()
            }

            else -> {
                iconBounds.offsetTo(
                    fullBounds.right - iconBounds.width() - horizontalPadding,
                    fullBounds.centerY() - iconBounds.height() / 2
                )

                textX = fullBounds.left + horizontalPadding.toFloat()
                lineStartX = textX + textWidth + iconTextSpacing
                lineEndX = iconBounds.left - iconTextSpacing
            }
        }

        icon.bounds = iconBounds
        icon.alpha = 204
        icon.draw(canvas)

        val centerY = fullBounds.centerY().toFloat()

        paint.alpha = 204
        canvas.drawText(timeText, textX, paint.getCenteredTextBaselineY(centerY), paint)

        paint.alpha = 51
        canvas.drawLine(lineStartX, centerY, lineEndX, centerY, paint)

        paint.alpha = 255
    }

    override fun initialize(context: Context) {
        if (playIcon == null) {
            playIcon = AppCompatResources.getDrawable(context, R.drawable.vector_lyric_play)

            val size = context.dpToPx(18f)
            playIcon?.setBounds(0, 0, size, size)
            playIcon?.let { DrawableCompat.setTint(it, paint.color) }
        }

        if (iconTextSpacing == 0f) {
            horizontalPadding = context.dpToPx(16f)
            iconTextSpacing = context.dpToPx(8f).toFloat()
        }
    }

    override fun setParagraphSpacing(spacing: Int) = Unit

    override fun drawBackground(
        canvas: Canvas,
        fullBounds: Rect,
        scrollOffset: Float,
        lyricData: LyricText
    ) = Unit

    companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
    }
}