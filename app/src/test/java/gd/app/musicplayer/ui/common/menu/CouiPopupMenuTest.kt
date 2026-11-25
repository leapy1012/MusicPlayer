package gd.app.musicplayer.ui.common.menu

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Rect
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import com.coui.appcompat.poplist.RoundFrameLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.DarkThemePalette
import gd.app.musicplayer.core.designsystem.theme.LightThemePalette
import gd.app.musicplayer.core.designsystem.theme.PictureThemePalette
import gd.app.musicplayer.domain.model.MusicSet
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class CouiPopupMenuTest {
    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
        setTheme(R.style.AppTheme)
    }

    @Test fun picturedSurfacePreservesNativeListHierarchyAndAnimatedOutline() {
        val activity = activity()
        val theme = PictureThemePalette()
        PictureThemePalette::class.java.getDeclaredField("blurredBitmap").apply {
            isAccessible = true
            set(theme, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        }
        val popup = COUIPopupListWindow(CouiPopupListSurface.popupContext(activity, theme))
        val main = popup.mainMenuListView.parent as RoundFrameLayout
        val sub = popup.subMenuListView.parent as RoundFrameLayout
        val mainOutline = main.outlineProvider
        val subOutline = sub.outlineProvider
        CouiPopupListSurface.apply(popup, activity, theme)
        assertEquals(1, main.childCount)
        assertEquals(1, sub.childCount)
        assertSame(popup.mainMenuListView, main.getChildAt(0))
        assertSame(popup.subMenuListView, sub.getChildAt(0))
        assertSame(mainOutline, main.outlineProvider)
        assertSame(subOutline, sub.outlineProvider)
        assertNotSame(main.background, sub.background)
        // Native transition clipping must still work after the pictured surface is applied.
        sub.layout(0, 0, 240, 400)
        sub.setOverrideOutline(0, 90, 240, 300, 0.5f)
        val outline = Outline()
        sub.outlineProvider.getOutline(sub, outline)
        val rect = Rect()
        assertTrue(outline.getRect(rect))
        assertEquals(Rect(0, 90, 240, 300), rect)
    }

    @Test fun titleColorsKeepSelectedAndDisabledStatesInLightAndDarkThemes() {
        val activity = activity()
        for (theme in listOf(LightThemePalette(), DarkThemePalette())) {
            val item = PopupListItem.Builder().setTitle("Name").setIsChecked(true).build()
            CouiPopupListSurface.paintItemTitles(activity, listOf(item), theme)
            val colors = item.titleColorList
            val enabled = intArrayOf(android.R.attr.state_enabled)
            val selected = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected)
            assertNotEquals(colors.getColorForState(enabled, 0), colors.getColorForState(selected, 0))
            assertNotEquals(colors.getColorForState(enabled, 0), colors.getColorForState(intArrayOf(), 0))
            assertEquals(0, item.forceTint and PopupListItem.MENU_ITEM_FORCE_TINT_TITLE)
            assertNotEquals(0, item.forceTint and PopupListItem.MENU_ITEM_FORCE_TINT_STATE_ICON)
        }
    }

    @Test fun librarySortBranchContainsNativeSubmenuAndCurrentValue() {
        val activity = activity()
        val menu = ContextMenu(activity, MusicSet.Tracks, LightThemePalette(), {}, currentSortStyle = "name")
        val buildItems = ContextMenu::class.java.getDeclaredMethod("buildItems").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val items = buildItems.invoke(menu) as List<PopupListItem>
        val branch = items.single { !it.subMenuItemList.isNullOrEmpty() }
        assertEquals(activity.getString(R.string.sort_by), branch.title)
        assertTrue(branch.hasSubMenu())
        assertEquals(branch.subMenuItemList.first { it.isChecked }.title, branch.description)
        assertTrue(branch.subMenuItemList.size > 2)
    }
}
