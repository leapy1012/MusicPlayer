package gd.app.musicplayer.feature.setting

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import androidx.preference.PreferenceViewHolder
import com.coui.appcompat.couiswitch.COUISwitch
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.designsystem.view.PreferenceDeskLrcItemView
import gd.app.musicplayer.feature.setting.preference.DesktopLyricsPreference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
class DesktopLyricsPreferenceTest {
    @Test fun bindingAndRebindingKeepTheCustomSwitchVisibleAndInteractive() {
        ShadowSettings.setCanDrawOverlays(true)
        val c=Robolectric.buildActivity(Activity::class.java)
        c.get().setTheme(R.style.AppTheme)
        val activity=c.setup().get()
        val preference=DesktopLyricsPreference(activity)
        var visible:Boolean?=null
        preference.onVisibleChanged={visible=it}
        val row=LayoutInflater.from(activity).inflate(R.layout.preference_desktop_lyrics_row,null) as PreferenceDeskLrcItemView
        activity.setContentView(row)
        val holder=PreferenceViewHolder.createInstanceForTests(row)
        preference.render(DesktopLyricPreference(visible=false))
        repeat(2) {
            preference.onBindViewHolder(holder)
            assertEquals(View.VISIBLE,row.findViewById<View>(android.R.id.widget_frame).visibility)
            assertTrue(row.isClickable)
        }
        row.performClick()
        assertEquals(true,visible)
        val frame=row.findViewById<android.view.ViewGroup>(android.R.id.widget_frame)
        val switch=(0 until frame.childCount).map { frame.getChildAt(it) }.filterIsInstance<COUISwitch>().single()
        assertTrue(switch.isChecked)
        preference.render(DesktopLyricPreference(visible=false))
        preference.onBindViewHolder(holder)
        assertFalse(switch.isChecked)
        c.pause().stop().destroy()
    }
    @Test fun lockButtonReceivesTheWholeTapWithoutTogglingVisibilityAndUsesThemeTint() {
        ShadowSettings.setCanDrawOverlays(true)
        val c=Robolectric.buildActivity(Activity::class.java)
        c.get().setTheme(R.style.AppTheme)
        val activity=c.setup().get()
        val row=LayoutInflater.from(activity).inflate(R.layout.preference_desktop_lyrics_row,null) as PreferenceDeskLrcItemView
        activity.setContentView(row)
        row.render(DesktopLyricPreference(visible=true))
        row.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(250,View.MeasureSpec.EXACTLY))
        row.layout(0,0,1080,250)
        val lock=row.findViewById<android.widget.ImageView>(R.id.desk_lrc_lock)
        var visibilityChanges=0
        val locks=mutableListOf<Boolean>()
        row.onVisibleChanged={ visibilityChanges++ }
        row.onLockedChanged={ locks+=it }
        val attrs=activity.obtainStyledAttributes(intArrayOf(com.coui.appcompat.R.attr.couiColorLabelPrimary))
        assertEquals(attrs.getColor(0,0),lock.imageTintList!!.defaultColor)
        attrs.recycle()
        fun tap() {
            val location=IntArray(2); lock.getLocationOnScreen(location)
            val parent=IntArray(2); row.getLocationOnScreen(parent)
            val x=(location[0]-parent[0]+lock.width/2).toFloat()
            val y=(location[1]-parent[1]+lock.height/2).toFloat()
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                val e=android.view.MotionEvent.obtain(0,20,action,x,y,0)
                row.dispatchTouchEvent(e); e.recycle()
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        tap()
        assertEquals(listOf(true),locks)
        assertTrue(lock.isSelected)
        tap()
        assertEquals(listOf(true,false),locks)
        assertEquals(0,visibilityChanges)
        assertFalse(lock.isSelected)
        c.pause().stop().destroy()
    }

}
