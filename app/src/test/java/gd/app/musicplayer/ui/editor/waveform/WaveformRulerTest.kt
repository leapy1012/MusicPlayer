package gd.app.musicplayer.ui.editor.waveform

import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import gd.app.musicplayer.ui.editor.model.WaveformData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class, manifest=Config.NONE)
class WaveformRulerTest {
    private fun data(duration: Int = 85000) = WaveformData("test.mp3", duration, "audio/mpeg",
        32000, 96000, frameTimesMs=IntArray(1200) { it * duration / 1200 },
        frameGains=IntArray(1200) { 1 + it % 40 })

    @Test fun measuredLabelSpacingSurvivesZoomAndNarrowViewports() {
        val model = SoundWaveViewportModel()
        model.setData(data())
        model.setViewport(360, 500, 40f, 16f, 16f)
        repeat(4) {
            val points = model.rulerPoints(110f, 0f, 360f)
            points.zipWithNext().forEach { (a, b) -> assertTrue(b.second - a.second >= 110f) }
            assertTrue(points.size <= 6)
            model.zoomOut()
        }
    }

    @Test fun longTrackOnlyBuildsVisibleRulerLabels() {
        val model = SoundWaveViewportModel()
        model.setData(data(24 * 60 * 60 * 1000))
        model.setViewport(360, 500, 40f, 16f, 16f)
        assertTrue(model.rulerPoints(90f, 400f, 760f).size <= 7)
    }

    @Test fun rulerUsesItsOwnOpaqueSurfaceAndThemeTextOutsideSelectionMasks() {
        for ((textColor, surface) in listOf(Color.BLACK to Color.WHITE, Color.WHITE to Color.BLACK)) {
            val view = SoundWaveView(RuntimeEnvironment.getApplication())
            view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 600, 400)
            view.setSoundFile(data())
            view.setClipIcon(null)
            view.setClipLeft(2000, false)
            view.setClipRight(6000)
            view.setOverlayColor(Color.RED)
            view.setRulerColors(textColor, surface)
            var labels = 0
            var surfaceBottom = 0f
            var lowestMaskTop = Float.MAX_VALUE
            view.draw(object : Canvas() {
                override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                    labels++
                    assertEquals(textColor, paint.color)
                    assertTrue(paint.textSize >= 12f)
                }
                override fun drawRect(rect: Rect, paint: Paint) {
                    if (paint.color == Color.RED) lowestMaskTop = minOf(lowestMaskTop, rect.top.toFloat())
                }
                override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
                    if (top == 0f && paint.color == surface) surfaceBottom = bottom
                }
            })
            assertTrue(labels > 0)
            assertTrue(surfaceBottom > 0f)
            assertTrue(lowestMaskTop >= surfaceBottom)
        }
    }
}
