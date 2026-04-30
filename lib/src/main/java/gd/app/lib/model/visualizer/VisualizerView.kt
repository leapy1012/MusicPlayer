package gd.app.lib.model.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), VisualizerDataListener, View.OnClickListener {

    private var renderer: VisualizerRenderer = GradientBarVisualizerRenderer(this)
    private val contentBounds = Rect()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AudioVisualizerManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AudioVisualizerManager.removeListener(this)
    }

    override fun onVisualizerEnabledChanged(enabled: Boolean) {
        renderer.onVisualizerEnabledChanged(enabled)
    }

    override fun onFftDataChanged(magnitudes: FloatArray, waveform: FloatArray?) {
        renderer.onFftDataChanged(magnitudes, waveform)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (renderer == null || contentBounds.isEmpty) return
        renderer.onDraw(canvas)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val bounds = Rect(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom
        )

        if (bounds.isEmpty) return

        contentBounds.set(bounds)
        renderer.onBoundsChanged(contentBounds)
    }

    override fun onClick(view: View?) {
        setRenderer(GradientBarVisualizerRenderer(this))
    }

    fun setRenderer(newRenderer: VisualizerRenderer) {
        renderer.release()
        renderer = newRenderer

        if (contentBounds.isEmpty) return

        renderer.onBoundsChanged(contentBounds)
        postInvalidate()
    }
}