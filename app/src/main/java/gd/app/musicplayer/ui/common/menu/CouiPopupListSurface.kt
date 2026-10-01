package gd.app.musicplayer.ui.common.menu

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.ImageViewCompat
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.dialogTitleColor
import gd.app.musicplayer.ui.common.base.BaseActivity

/**
 * Themes COUI popup lists to match the dialog plate.
 *
 * ## Why labels/icons stay white in light mode
 * [PopupListItem] defaults `mForceTint = MENU_ITEM_FORCE_TINT_ALL (7)`.
 * [DefaultAdapter] then always tints title/icon from
 * `R.color.coui_popup_list_window_item_tint_selector` → `?couiColorLabelPrimary`,
 * and **ignores** [PopupListItem.getTitleColorList] while the TITLE bit is set.
 *
 * Pictured/dark activities overlay [Theme_COUI_Main_Dark], so `LabelPrimary` is
 * `#e6ffffff`. Light theme paints a white dialog plate (or we skip the custom plate),
 * leaving white-on-white. Setting `titleColorList` alone does nothing unless the
 * TITLE force-tint bit is cleared (Builder does `forceTint &= ~TITLE` only when
 * color is supplied at [PopupListItem.Builder.build] time — not via setters after).
 *
 * Fix: wrap the popup in Light/Dark COUI theme for correct token defaults, clear
 * TITLE|ICON force-tint, and set an explicit title [ColorStateList].
 */
object CouiPopupListSurface {

    private const val TAG_PICTURED_PLATE = "coui_popup_pictured_plate"
    private const val FORCE_TINT_TITLE_OR_ICON =
        PopupListItem.MENU_ITEM_FORCE_TINT_TITLE or PopupListItem.MENU_ITEM_FORCE_TINT_ICON

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
        surface: Drawable? = null
    ) {
        val palette = resolvePalette(context)
        val textColor = resolveItemTextColor(palette)
        applyItemTextColors(popup, textColor)

        if (shouldPaintCustomSurface(palette)) {
            val resolved = (surface ?: palette?.getDialogSurfaceDrawable(context) ?: return).mutate()
            popup.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            paintWrapper(popup.contentView, resolved)
            paintWrapper(popup.subMenuListView?.parent as? View, resolved)
            paintWrapper(popup.mainMenuListView?.parent as? View, resolved)
        }

        tintVisibleRows(popup.mainMenuListView, textColor)
        tintVisibleRows(popup.subMenuListView, textColor)
        tintVisibleRows(popup.listView, textColor)
        popup.contentView?.post {
            tintVisibleRows(popup.mainMenuListView, textColor)
            tintVisibleRows(popup.subMenuListView, textColor)
            tintVisibleRows(popup.listView, textColor)
        }
    }

    /**
     * Stamp dialog-contrast title colors and clear TITLE/ICON force-tint so
     * [DefaultAdapter] honors [PopupListItem.getTitleColorList].
     */
    @JvmStatic
    fun paintItemTitles(context: Context, items: List<PopupListItem>, palette: ThemePalette? = null) {
        val colors = ColorStateList.valueOf(
            resolveItemTextColor(palette ?: resolvePalette(context))
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

    private fun applyItemTextColors(popup: COUIPopupListWindow, color: Int) {
        val colors = ColorStateList.valueOf(color)
        val items = popup.itemList ?: return
        items.forEach { item ->
            paintItemTitle(item, colors)
            item.subMenuItemList?.forEach { paintItemTitle(it, colors) }
            item.subArray?.forEach { paintItemTitle(it, colors) }
        }
        (popup.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    private fun paintItemTitle(item: PopupListItem, colors: ColorStateList) {
        item.setTitleColorList(colors)
        // Default forceTint=ALL makes DefaultAdapter ignore titleColorList.
        item.setForceTint(item.forceTint and FORCE_TINT_TITLE_OR_ICON.inv())
    }

    private fun tintVisibleRows(listView: ListView?, color: Int) {
        if (listView == null) return
        val colors = ColorStateList.valueOf(color)
        for (index in 0 until listView.childCount) {
            tintTree(listView.getChildAt(index), colors)
        }
    }

    private fun tintTree(view: View?, colors: ColorStateList) {
        when (view) {
            null -> return
            is TextView -> view.setTextColor(colors)
            is ImageView -> {
                if (view.id == com.coui.appcompat.R.id.popup_list_window_item_icon) {
                    ImageViewCompat.setImageTintList(view, colors)
                }
            }
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    tintTree(view.getChildAt(index), colors)
                }
            }
        }
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

    private fun paintWrapper(root: View?, surface: Drawable) {
        if (root == null) return
        val wrapper = root.findViewById<View>(com.coui.appcompat.R.id.coui_popup_wrapper)
            ?: if (root.id == com.coui.appcompat.R.id.coui_popup_wrapper) root else null
        val group = wrapper as? ViewGroup ?: return

        val pictured = surface.constantState?.newDrawable()?.mutate() ?: surface.mutate()

        val plate = group.findViewWithTag<View>(TAG_PICTURED_PLATE)
            ?: View(group.context).also { plateView ->
                plateView.tag = TAG_PICTURED_PLATE
                plateView.isClickable = false
                plateView.isFocusable = false
                plateView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                group.addView(
                    plateView,
                    0,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        plate.background = pictured

        if (group.background !is com.coui.appcompat.state.COUIMaskEffectDrawable) {
            group.setBackgroundColor(Color.TRANSPARENT)
        }

        ensureRoundClip(group)
    }

    private fun ensureRoundClip(wrapper: View) {
        val radius = wrapper.resources.getDimension(
            com.coui.appcompat.R.dimen.coui_round_corner_m
        )
        wrapper.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        wrapper.clipToOutline = true
        if (wrapper is ViewGroup) {
            wrapper.clipChildren = true
        }
    }

    private fun resolvePalette(context: Context): ThemePalette? {
        val activity = context as? Activity
            ?: ((context as? ContextWrapper)?.baseContext as? Activity)
        return (activity as? BaseActivity)?.themeEngine?.currentTheme()
    }
}
