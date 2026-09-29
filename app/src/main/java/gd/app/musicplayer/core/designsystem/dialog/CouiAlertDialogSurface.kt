package gd.app.musicplayer.core.designsystem.dialog

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.ui.common.base.BaseActivity

/**
 * Applies [ThemePalette.getDialogSurfaceDrawable] onto COUI alert chrome.
 * COUI paints a solid `?couiColorSurface` on [parentPanel]; pictured/dark need the
 * blurred theme surface instead. Call after the dialog is shown (or from onShow).
 */
object CouiAlertDialogSurface {

    @JvmStatic
    @JvmOverloads
    fun apply(dialog: AlertDialog, surface: Drawable? = null) {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val parentPanel = dialog.findViewById<View>(com.coui.appcompat.R.id.parentPanel)
        val rootView = dialog.findViewById<View>(com.coui.appcompat.R.id.rootView)
        val target = parentPanel ?: rootView ?: return

        val resolved = (surface ?: resolveSurface(dialog) ?: return).mutate()
        target.background = if (parentPanel != null) {
            insetLikeCouiBuilder(parentPanel.context, resolved)
        } else {
            resolved
        }
    }

    private fun resolveSurface(dialog: AlertDialog): Drawable? {
        val activity = dialog.ownerActivity
            ?: (dialog.context as? Activity)
            ?: ((dialog.context as? ContextWrapper)?.baseContext as? Activity)
        val palette = (activity as? BaseActivity)?.themeEngine?.currentTheme() ?: return null
        return palette.getDialogSurfaceDrawable(dialog.context)
    }

    /** Match [coui_alert_dialog_builder_background] insets so width/margins stay COUI-correct. */
    private fun insetLikeCouiBuilder(context: android.content.Context, surface: Drawable): Drawable {
        val horizontal = context.resources.getDimensionPixelSize(
            com.coui.appcompat.R.dimen.coui_dialog_layout_margin_horizontal
        )
        val vertical = context.resources.getDimensionPixelSize(
            com.coui.appcompat.R.dimen.coui_dialog_layout_margin_vertical
        )
        return InsetDrawable(surface, horizontal, 0, horizontal, vertical)
    }
}
