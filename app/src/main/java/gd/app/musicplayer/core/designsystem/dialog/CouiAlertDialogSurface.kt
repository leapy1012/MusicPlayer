package gd.app.musicplayer.core.designsystem.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import dagger.hilt.android.EntryPointAccessors
import gd.app.musicplayer.di.ThemeEntryPoint
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
        val activity = findActivity(dialog.ownerActivity) ?: findActivity(dialog.context)
        val palette = (activity as? BaseActivity)?.themeEngine?.currentTheme()
            ?: resolvePaletteFromApplication(dialog.context)
            ?: return null
        return palette.getDialogSurfaceDrawable(dialog.context)
    }

    private fun resolvePaletteFromApplication(context: Context) = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeRepo.getCorePalette()
    } catch (_: Exception) {
        null
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    /** Match [coui_alert_dialog_builder_background] insets so width/margins stay COUI-correct. */
    private fun insetLikeCouiBuilder(context: Context, surface: Drawable): Drawable {
        val horizontal = context.resources.getDimensionPixelSize(
            com.coui.appcompat.R.dimen.coui_dialog_layout_margin_horizontal
        )
        val vertical = context.resources.getDimensionPixelSize(
            com.coui.appcompat.R.dimen.coui_dialog_layout_margin_vertical
        )
        return InsetDrawable(surface, horizontal, 0, horizontal, vertical)
    }
}
