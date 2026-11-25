package gd.app.musicplayer.core.designsystem.theme

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import gd.app.lib.model.lrc.renderer.ScrollingLyricRenderer
import gd.app.lib.model.lrc.view.LyricView
import gd.app.musicplayer.feature.lyrics.setLyricText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class LyricGestureRegressionTest {
    private val lyrics = (0..20).joinToString("\n") { "[00:${(it * 2).toString().padStart(2, '0')}.00]Line $it" }
    private fun view(): LyricView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return LyricView(activity).apply {
            setDragEnable(true)
            setTextSize(16)
            setLyricText(lyrics)
            setCurrentTime(10_000)
            measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            layout(0, 0, 400, 600)
            draw(Canvas(Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)))
        }
    }
    private fun renderer(view: LyricView) = LyricView::class.java.getDeclaredField("renderer")
        .apply { isAccessible = true }.get(view) as ScrollingLyricRenderer
    private fun dragging(view: LyricView) = ScrollingLyricRenderer::class.java.getDeclaredField("isDragging")
        .apply { isAccessible = true }.getBoolean(renderer(view))
    private fun drag(view: LyricView, start: Float, end: Float) {
        view.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 200f, start, 0))
        view.onTouchEvent(MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 200f, end, 0))
    }

    @Test fun playbackTicksDoNotReplaceRendererOrInterruptAnActiveLyricScroll() {
        val view = view()
        drag(view, 400f, 250f)
        assertTrue(dragging(view))
        val original = renderer(view)
        repeat(20) {
            view.setLyricText(lyrics)
            view.setTextSize(16)
            view.setCurrentTime(10_000L + it * 100)
        }
        assertSame(original, renderer(view))
        assertTrue(dragging(view))
    }

    @Test fun downwardDragCanEnterTimelinePreviewAsWellAsUpwardDrag() {
        val view = view()
        drag(view, 150f, 300f)
        assertTrue(dragging(view))
    }

    @Test fun selectingATimestampAccountsForTheUserLyricOffset() {
        val view = view()
        var seek = -1L
        view.setOnLyricLineClickListener { seek = it }
        view.setTimeOffset(750)
        view.dispatchLyricLineClick(10_000)
        assertEquals(9_250L, seek)
        view.dispatchLyricLineClick(500)
        assertEquals(0L, seek)
    }
}
