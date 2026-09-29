package gd.app.musicplayer.ui.common.menu

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import com.coui.appcompat.poplist.COUIPopupListWindow
import gd.app.musicplayer.ui.common.base.BaseActivity

/**
 * Paints pictured/dialog surface into COUI popup lists without replacing the
 * wrapper background that drives row press masks.
 *
 * COUI draws a rounded plate on [coui_popup_wrapper] but row presses are
 * rectangular; RoundFrameLayout is supposed to clip them. Replacing that
 * background with our surface left presses unclipped at the corners. Fix:
 * keep COUI's background/mask, insert a pictured plate *behind* the list,
 * and force [View.setClipToOutline] with the COUI corner radius.
 */
object CouiPopupListSurface {

    private const val TAG_PICTURED_PLATE = "coui_popup_pictured_plate"

    @JvmStatic
    @JvmOverloads
    fun apply(
        popup: COUIPopupListWindow,
        context: Context,
        surface: Drawable? = null
    ) {
        val resolved = (surface ?: resolveSurface(context) ?: return).mutate()
        popup.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        paintWrapper(popup.contentView, resolved)
        paintWrapper(popup.subMenuListView?.parent as? View, resolved)
        paintWrapper(popup.mainMenuListView?.parent as? View, resolved)
    }

    private fun paintWrapper(root: View?, surface: Drawable) {
        if (root == null) return
        val wrapper = root.findViewById<View>(com.coui.appcompat.R.id.coui_popup_wrapper)
            ?: if (root.id == com.coui.appcompat.R.id.coui_popup_wrapper) root else null
        val group = wrapper as? ViewGroup ?: return

        val pictured = surface.constantState?.newDrawable()?.mutate() ?: surface.mutate()
        // Do not replace group.background — that owns COUI's press-mask wiring.

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

        // Hide the opaque COUI solid plate so pictured shows, without destroying
        // the RoundFrameLayout instance or its radius field.
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

    private fun resolveSurface(context: Context): Drawable? {
        val activity = context as? Activity
            ?: ((context as? ContextWrapper)?.baseContext as? Activity)
        val palette = (activity as? BaseActivity)?.themeEngine?.currentTheme() ?: return null
        return palette.getDialogSurfaceDrawable(context)
    }
}
