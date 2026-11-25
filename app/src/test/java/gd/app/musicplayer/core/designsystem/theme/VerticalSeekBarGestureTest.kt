package gd.app.musicplayer.core.designsystem.theme

import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.coui.appcompat.seekbar.COUIVerticalSeekBar
import gd.app.musicplayer.R
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class VerticalSeekBarGestureTest {
    private fun slider(): COUIVerticalSeekBar {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        return COUIVerticalSeekBar(activity).apply {
            max = 1000
            measure(View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
            layout(0, 0, 64, 320)
        }
    }

    @Test fun animationAfterLayoutKeepsDisplayedThumbConsistentWithProgress() {
        val seek = slider()
        seek.setProgress(750, true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(750, seek.progress)
        seek.setProgressColor(ColorStateList.valueOf(Color.GREEN))
        seek.setProgressRoundCornerWeight(0f)
        seek.setBackgroundRoundCornerWeight(0f)
        fun paintedTop(): Float {
            var top = Float.NaN
            val canvas = object : Canvas() {
                override fun drawRoundRect(left: Float, y: Float, right: Float, bottom: Float,
                                           rx: Float, ry: Float, paint: Paint) {
                    if (paint.color == Color.GREEN) top = y
                }
                override fun drawRect(rect: Rect, paint: Paint) {
                    if (paint.color == Color.GREEN) top = rect.top.toFloat()
                }
            }
            seek.draw(canvas)
            return top
        }
        val animatedTop = paintedTop()
        assertFalse(animatedTop.isNaN())
        seek.progress = 250
        assertTrue(animatedTop < paintedTop())
        seek.progress = 750
        assertEquals(paintedTop(), animatedTop, 1f)
    }

    @Test fun tapAfterProgrammaticAnimationStillReportsUserChangeAndReleasesTracking() {
        val seek = slider()
        var userProgress = -1
        var starts = 0
        var stops = 0
        seek.setOnSeekBarChangeListener(object : COUIVerticalSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: COUIVerticalSeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) userProgress = progress
            }
            override fun onStartTrackingTouch(view: COUIVerticalSeekBar) { starts++ }
            override fun onStopTrackingTouch(view: COUIVerticalSeekBar) { stops++ }
        })
        seek.setProgress(500, true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        seek.onTouchEvent(MotionEvent.obtain(1000, 1000, MotionEvent.ACTION_DOWN, 32f, 80f, 0))
        seek.onTouchEvent(MotionEvent.obtain(1000, 1050, MotionEvent.ACTION_UP, 32f, 80f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(userProgress > 500)
        assertEquals(seek.progress, userProgress)
        assertEquals(1, starts)
        assertEquals(1, stops)
        assertFalse(seek.isPressed)
    }

    @Test fun canceledTapDoesNotChangeAudioGain() {
        val seek = slider()
        seek.progress = 500
        seek.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 32f, 40f, 0))
        seek.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_CANCEL, 32f, 40f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(500, seek.progress)
        assertFalse(seek.isPressed)
    }
}
