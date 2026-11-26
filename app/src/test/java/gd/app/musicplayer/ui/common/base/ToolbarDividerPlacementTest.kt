package gd.app.musicplayer.ui.common.base

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import gd.app.musicplayer.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
class ToolbarDividerPlacementTest {
    @Test fun dividerInFrameSitsBelowToolbarRatherThanUnderStatusBar() {
        val c=Robolectric.buildActivity(Activity::class.java)
        c.get().setTheme(R.style.AppTheme)
        val activity=c.setup().get()
        val frame=FrameLayout(activity)
        val toolbar=Toolbar(activity)
        frame.addView(toolbar,FrameLayout.LayoutParams(-1,-1))
        activity.setContentView(frame)
        toolbar.ensureAppBarDivider()
        frame.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY))
        frame.layout(0,0,400,80)
        val divider=frame.findViewById<View>(R.id.app_bar_divider)
        assertNotNull(divider)
        assertEquals(80,divider.bottom)
        assertTrue(divider.top>0)
        c.pause().stop().destroy()
    }
}
