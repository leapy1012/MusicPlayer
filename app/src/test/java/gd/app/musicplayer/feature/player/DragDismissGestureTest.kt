package gd.app.musicplayer.feature.player

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import gd.app.lib.view.DragDismissLayout
import gd.app.musicplayer.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
class DragDismissGestureTest {
    @Test fun downSwipeWorksAcrossPagerPagesAndAfterAnAbandonedBlockedGesture() {
        val controller=Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        val activity=controller.setup().get()
        val root=LayoutInflater.from(activity).inflate(R.layout.activity_musicplay,null) as DragDismissLayout
        activity.setContentView(root)
        root.setAllowedDragDirections(DragDismissLayout.DIRECTION_DOWN)
        val content=root.findViewById<FrameLayout>(R.id.music_play_content)
        val pager=ViewPager(activity)
        pager.adapter=object:PagerAdapter() {
            override fun getCount()=3
            override fun isViewFromObject(view:View,obj:Any)=view===obj
            override fun instantiateItem(container:ViewGroup,position:Int):Any =
                FrameLayout(activity).also { container.addView(it) }
            override fun destroyItem(container:ViewGroup,position:Int,obj:Any) {
                container.removeView(obj as View)
            }
        }
        content.addView(pager)
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY))
            root.layout(0,0,400,900)
        }
        layout()
        var time=0L
        fun send(action:Int,y:Float) {
            time+=100
            root.dispatchTouchEvent(MotionEvent.obtain(time-100,time,action,200f,y,0))
        }
        for(page in listOf(1,0,2,1)) {
            pager.setCurrentItem(page,false)
            layout()
            // A child can disappear before sending its matching UP/CANCEL.
            root.setDisallowDragIntercept(true)
            send(MotionEvent.ACTION_DOWN,300f)
            send(MotionEvent.ACTION_MOVE,340f)
            send(MotionEvent.ACTION_MOVE,450f)
            assertTrue("Page $page must allow a downward player drag",root.getChildAt(0).top>0)
            send(MotionEvent.ACTION_CANCEL,450f)
            repeat(50) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
                root.computeScroll()
            }
            assertEquals("Cancelled gesture must return the player",0,root.getChildAt(0).top)
        }
        controller.pause().stop().destroy()
    }
}
