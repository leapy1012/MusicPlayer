package gd.app.lib.model.lrc.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import gd.app.lib.model.lrc.resource.LyricText

interface LyricIndicatorRenderer {

    fun setParagraphSpacing(spacing: Int)

    fun setTextAlign(align: Int)

    fun drawBackground(
        canvas: Canvas,
        fullBounds: Rect,
        scrollOffset: Float,
        lyricData: LyricText
    )

    fun setColor(color: Int)

    fun adjustContentBounds(bounds: Rect)

    fun setTextSize(size: Float)

    fun drawForeground(
        canvas: Canvas,
        fullBounds: Rect,
        scrollOffset: Float,
        lyricData: LyricText
    )

    fun initialize(context: Context)
}