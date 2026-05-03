package gd.app.lib.model.lrc.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import gd.app.lib.getCenteredTextBaselineY
import gd.app.lib.wrapText

class MultilineTextLayout(
    private val textPaint: TextPaint,
    private val maxWidth: Int,
    private val lineSpacing: Int,
    private val lineHeight: Int,
    private val alignment: Int
) {

    private val lines = mutableListOf<String>()
    private var totalHeight: Int = 0

    fun setText(text: String) {
        lines.clear()
        textPaint.wrapText(text, maxWidth.toFloat(), lines, true)

        val count = lines.size
        totalHeight = (lineHeight * count) + ((count - 1) * lineSpacing)
    }

    fun draw(canvas: Canvas, offsetX: Float, offsetY: Float, paint: Paint?) {
        val drawPaint = paint ?: textPaint
        var y = drawPaint.getCenteredTextBaselineY(offsetY + lineHeight / 2f)

        for (line in lines) {
            val x = when (alignment) {
                1 -> (maxWidth - drawPaint.measureText(line)) / 2f
                2 -> maxWidth - drawPaint.measureText(line)
                else -> 0f
            }

            canvas.drawText(line, offsetX + x, y, drawPaint)
            y += lineHeight + lineSpacing
        }
    }

    fun getHeight(): Int = totalHeight

    fun getLineCount(): Int = lines.size
}