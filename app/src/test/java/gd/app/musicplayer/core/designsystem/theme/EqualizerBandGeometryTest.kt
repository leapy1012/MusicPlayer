package gd.app.musicplayer.core.designsystem.theme

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.view.VerticalEqualizerSlider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class, manifest=Config.NONE)
class EqualizerBandGeometryTest {
    @Test fun horizontalCouiControlIsRotatedAndHigherTouchesIncreaseTheBand() {
        val c=Robolectric.buildActivity(Activity::class.java)
        c.get().setTheme(R.style.AppTheme)
        val activity=c.setup().get()
        val root=LayoutInflater.from(activity).inflate(R.layout.item_equalizer_seekbar,null)
        val slider=root.findViewById<VerticalEqualizerSlider>(R.id.equalizer_item_seek)
        slider.measure(View.MeasureSpec.makeMeasureSpec(96,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY))
        slider.layout(0,0,96,800)
        assertEquals(800,slider.seekBar.width)
        assertEquals(96,slider.seekBar.height)
        assertEquals(-90f,slider.seekBar.rotation,0f)
        fun touch(y:Float):Int {
            slider.dispatchTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,48f,y,0))
            slider.dispatchTouchEvent(MotionEvent.obtain(0,20,MotionEvent.ACTION_UP,48f,y,0))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            return slider.seekBar.progress
        }
        assertTrue("An upward vertical touch must increase progress",touch(150f)>touch(650f))
        slider.isEnabled=false
        assertFalse(slider.seekBar.isEnabled)
        c.pause().stop().destroy()
    }
}
