package gd.app.musicplayer.ui.common

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.coui.appcompat.tablayout.COUITabLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.LiveThemeUpdater
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
class CouiTabSelectionTest {
    @Test fun repeatedSelectionsAndThemeRefreshPreserveOneSelectedTabAndStateColors() {
        val c=Robolectric.buildActivity(Activity::class.java)
        c.get().setTheme(R.style.AppTheme)
        val activity=c.setup().get()
        val tabs=COUITabLayout(activity)
        val pager=ViewPager2(activity)
        pager.adapter=object:RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount()=4
            override fun onCreateViewHolder(parent:ViewGroup,type:Int)=object:RecyclerView.ViewHolder(TextView(activity).apply {
                layoutParams=ViewGroup.LayoutParams(-1,-1)
            }){}
            override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int)=Unit
        }
        val mediator=CouiTabLayoutMediator(tabs,pager) { tab,position -> tab.text=listOf("Tracks","Artists","Albums","Genres")[position] }
        mediator.attach()
        for(position in listOf(3,0,2,1,3)) {
            tabs.getTabAt(position)!!.select()
            assertEquals(position,pager.currentItem)
            assertEquals(position,tabs.selectedTabPosition)
            assertEquals(1,(0 until 4).count { tabs.getTabAt(it)!!.view.isSelected })
        }
        val updater=LiveThemeUpdater {}
        updater.rememberColors(activity.theme)
        activity.theme.applyStyle(com.coui.appcompat.R.style.Theme_COUI_Main_Dark,true)
        updater.refresh(tabs)
        assertEquals(3,tabs.selectedTabPosition)
        val colors=tabs.getTabAt(3)!!.view.textView.textColors
        assertTrue(colors.isStateful)
        assertNotEquals(colors.defaultColor,colors.getColorForState(intArrayOf(android.R.attr.state_selected,android.R.attr.state_enabled),colors.defaultColor))
        mediator.detach()
        c.pause().stop().destroy()
    }
}
