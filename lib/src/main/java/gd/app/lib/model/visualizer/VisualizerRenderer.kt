package gd.app.lib.model.visualizer

import android.graphics.Canvas
import android.graphics.Rect

interface VisualizerRenderer : VisualizerDataListener {
    val type: Int

    fun onBoundsChanged(bounds: Rect)
    fun onDraw(canvas: Canvas)
    fun release()
}
