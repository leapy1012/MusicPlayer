package gd.app.musicplayer.core.designsystem.theme

import android.app.Activity
import android.app.Application
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import com.coui.appcompat.R as CouiR
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class LiveThemeUpdaterTest {
    private fun color(context: Context, attr: Int): Int {
        val a = context.obtainStyledAttributes(intArrayOf(attr))
        return try { a.getColor(0, 0) } finally { a.recycle() }
    }

    @Test fun roundTripRecolorsExistingViewsWithoutLosingTextSelectionOrScroll() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(CouiR.style.Theme_COUI_Main_Light)
        val initial = activity.resources.newTheme().apply { setTo(activity.theme) }
        val root = LinearLayout(activity)
        val label = TextView(activity).apply { setTextColor(color(activity, CouiR.attr.couiColorPrimaryNeutral)) }
        val input = EditText(activity).apply { setText("Keep my input"); setSelection(4) }
        root.addView(label); root.addView(input)
        activity.setContentView(root)
        root.scrollTo(0, 73)
        val updater = LiveThemeUpdater {}
        val light = label.currentTextColor
        updater.rememberColors(activity.theme)
        activity.theme.applyStyle(CouiR.style.Theme_COUI_Main_Dark, true)
        updater.refresh(root)
        assertEquals(color(activity, CouiR.attr.couiColorPrimaryNeutral), label.currentTextColor)
        assertNotEquals(light, label.currentTextColor)
        updater.rememberColors(activity.theme)
        activity.theme.setTo(initial)
        updater.refresh(root)
        assertEquals(light, label.currentTextColor)
        assertSame(label, root.getChildAt(0))
        assertEquals("Keep my input", input.text.toString())
        assertEquals(4, input.selectionStart)
        assertEquals(73, root.scrollY)
    }

    @Test fun cachedRecyclerRowsUseCurrentThemeWhenReattached() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(CouiR.style.Theme_COUI_Main_Light)
        val row = TextView(activity).apply { setTextColor(color(activity, CouiR.attr.couiColorPrimaryNeutral)) }
        val recycler = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 1
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = object : RecyclerView.ViewHolder(row) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
            }
        }
        val updater = LiveThemeUpdater {}
        updater.rememberColors(activity.theme)
        activity.theme.applyStyle(CouiR.style.Theme_COUI_Main_Dark, true)
        updater.refresh(recycler)
        recycler.measure(android.view.View.MeasureSpec.makeMeasureSpec(300, 1073741824),
            android.view.View.MeasureSpec.makeMeasureSpec(300, 1073741824))
        recycler.layout(0, 0, 300, 300)
        assertEquals(color(activity, CouiR.attr.couiColorPrimaryNeutral), row.currentTextColor)
    }

    @Test fun couiRefreshPreservesCheckedStateWithoutCallingTheChangeListener() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(CouiR.style.Theme_COUI_Main_Light)
        val toggle = com.coui.appcompat.couiswitch.COUISwitch(activity).apply { isChecked = true }
        var changes = 0
        toggle.setOnCheckedChangeListener { _, _ -> changes++ }
        val updater = LiveThemeUpdater {}
        updater.rememberColors(activity.theme)
        activity.theme.applyStyle(CouiR.style.Theme_COUI_Main_Dark, true)
        updater.refresh(toggle)
        assertTrue(toggle.isChecked)
        assertEquals(0, changes)
    }

    @Test fun toolbarRefreshKeepsNavigationAndMenuItemInstances() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(CouiR.style.Theme_COUI_Main_Light)
        val toolbar = com.coui.appcompat.toolbar.COUIToolbar(activity)
        val icon = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)
        toolbar.navigationIcon = icon
        val action = toolbar.menu.add("Keep this action")
        action.isVisible = false
        val updater = LiveThemeUpdater {}
        updater.rememberColors(activity.theme)
        activity.theme.applyStyle(CouiR.style.Theme_COUI_Main_Dark, true)
        updater.refresh(toolbar)
        assertSame(icon, toolbar.navigationIcon)
        assertSame(action, toolbar.menu.getItem(0))
        assertFalse(action.isVisible)
    }

    @Test fun refreshedButtonUsesTheSameTextColorsAsANewButtonInTheTargetTheme() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(CouiR.style.Theme_COUI_Main_Dark)
        val button = com.coui.appcompat.button.COUIButton(activity)
        val updater = LiveThemeUpdater {}
        updater.rememberColors(activity.theme)
        activity.theme.applyStyle(CouiR.style.Theme_COUI_Main_Light, true)
        updater.refresh(button)
        val expected = com.coui.appcompat.button.COUIButton(activity)
        assertEquals(expected.textColors, button.textColors)
    }
}
