package gd.app.musicplayer.ui.common.menu

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.widget.BaseAdapter
import androidx.appcompat.content.res.AppCompatResources
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import com.coui.appcompat.poplist.RoundFrameLayout
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.dialogTitleColor
import gd.app.musicplayer.ui.common.base.BaseActivity

/**
 * Applies the app's dialog contrast and pictured surface without changing COUI's
 * child hierarchy, animated clipping, row effects, or selected/disabled label states.
 */
object CouiPopupListSurface {

    /**
     * Context whose COUI label tokens match [palette]'s dialog contrast.
     * Use this when constructing [COUIPopupListWindow].
     */
    @JvmStatic
    fun popupContext(context: Context, palette: ThemePalette? = null): Context {
        val resolved = palette ?: resolvePalette(context)
        val style = if (usesLightPopupChrome(resolved)) {
            com.coui.appcompat.R.style.Theme_COUI_Main_Light
        } else {
            com.coui.appcompat.R.style.Theme_COUI_Main_Dark
        }
        return ContextThemeWrapper(context, style)
    }

    @JvmStatic
    @JvmOverloads
    fun apply(
        popup: COUIPopupListWindow,
        context: Context,
        palette: ThemePalette? = null
    ) {
        val resolved = palette ?: resolvePalette(context)
        popup.itemList?.let { paintItemTitles(context, it, resolved) }
        (popup.adapter as? BaseAdapter)?.notifyDataSetChanged()

        if (shouldPaintCustomSurface(resolved)) {
            // Both wrappers already exist, including the detached submenu. Each needs its own
            // drawable: pictured surfaces have no ConstantState and mutate() does not clone them.
            listOfNotNull(
                popup.mainMenuListView?.parent as? RoundFrameLayout,
                popup.subMenuListView?.parent as? RoundFrameLayout
            ).distinct().forEach { wrapper ->
                paintWrapper(wrapper, checkNotNull(resolved).getBottomDialogSurfaceDrawable(context))
            }
        }
    }

    /**
     * Clear only TITLE force-tint so COUI honors our contrast colors, while retaining
     * its native selected/disabled states and icon tinting.
     */
    @JvmStatic
    fun paintItemTitles(context: Context, items: List<PopupListItem>, palette: ThemePalette? = null) {
        val resolved = palette ?: resolvePalette(context)
        val nativeColors = checkNotNull(AppCompatResources.getColorStateList(
            popupContext(context, resolved),
            com.coui.appcompat.R.color.coui_popup_list_window_item_tint_selector
        ))
        val colors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(
                nativeColors.getColorForState(intArrayOf(-android.R.attr.state_enabled), nativeColors.defaultColor),
                nativeColors.getColorForState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected), nativeColors.defaultColor),
                resolveItemTextColor(resolved)
            )
        )
        items.forEach { item ->
            paintItemTitle(item, colors)
            item.subMenuItemList?.forEach { paintItemTitle(it, colors) }
            item.subArray?.forEach { paintItemTitle(it, colors) }
        }
    }

    private fun usesLightPopupChrome(palette: ThemePalette?): Boolean {
        if (palette == null) return true
        return palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT ||
            palette.isDialogSurfaceLight()
    }

    private fun shouldPaintCustomSurface(palette: ThemePalette?): Boolean {
        if (palette == null) return false
        return palette.getThemeType() != ThemeManager.THEME_TYPE_LIGHT
    }

    private fun paintItemTitle(item: PopupListItem, colors: ColorStateList) {
        item.setTitleColorList(colors)
        // Default forceTint=ALL makes DefaultAdapter ignore titleColorList.
        item.setForceTint(item.forceTint and PopupListItem.MENU_ITEM_FORCE_TINT_TITLE.inv())
    }

    private fun resolveItemTextColor(palette: ThemePalette?): Int {
        if (palette == null) return 0xDE000000.toInt()
        return if (usesLightPopupChrome(palette)) {
            // Hard dark label — do not trust activity theme attrs (may still be Dark overlay).
            palette.dialogTitleColor.takeUnless { Color.alpha(it) == 0 || isVisuallyWhite(it) }
                ?: 0xDE000000.toInt()
        } else {
            palette.dialogTitleColor.takeUnless { Color.alpha(it) == 0 }
                ?: Color.WHITE
        }
    }

    private fun isVisuallyWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r >= 0xF0 && g >= 0xF0 && b >= 0xF0
    }

    private fun paintWrapper(wrapper: RoundFrameLayout, surface: Drawable) {
        // COUI animates background bounds and its override outline, and assumes child 0 is
        // the list. Never insert a plate view or replace the native outline provider.
        wrapper.background = surface.mutate()
    }

    private fun resolvePalette(context: Context): ThemePalette? {
        var current = context
        while (current is ContextWrapper) {
            if (current is BaseActivity) return current.themeEngine.currentTheme()
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return null
    }
}
